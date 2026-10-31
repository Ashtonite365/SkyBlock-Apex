/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: armour looks chosen by SkyBlock id (SPEC §2.6) and legacy armour texture paths.
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ashtonite365.apex.texengine.ArmorModels
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.EquipmentClientInfo
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.EquipmentAssets
import net.minecraft.world.item.equipment.Equippable

object ApexArmor : ArmorModels {

	val recognisedNamespaces = listOf("firmskyblock")

	private const val FOLDER = "overrides/armor_models"

	const val UNDYED_TINT = 0xFFA06540.toInt()

	class Look(val assetId: Identifier) {
		private val key = ResourceKey.create(EquipmentAssets.ROOT_ID, assetId)
		private val perSlot = arrayOfNulls<Equippable>(EquipmentSlot.entries.size)

		fun equippable(slot: EquipmentSlot): Equippable =
			perSlot[slot.ordinal] ?: Equippable.builder(slot)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
				.setAsset(key)
				.build()
				.also { perSlot[slot.ordinal] = it }
	}

	class Entry(val base: Look, val variants: List<Pair<StackTest, Look>>) {
		fun lookFor(stack: ItemStack): Look {
			for ((test, look) in variants) if (test.test(stack)) return look
			return base
		}
	}

	class Loaded(val byId: Map<String, Entry>, val madeAssets: Map<Identifier, EquipmentClientInfo>) {
		companion object {
			val EMPTY = Loaded(mapOf(), mapOf())
		}
	}

	@Volatile
	private var loaded = Loaded.EMPTY

	private val madeAssetCounter = AtomicInteger()

	override fun equippableFor(stack: ItemStack, slot: EquipmentSlot): Equippable? {
		if (!settings.armorOverrides) return null
		val data = loaded
		if (data.byId.isEmpty()) return null
		val id = stack.view().repoItemId ?: return null
		val entry = data.byId[id.skyblockId] ?: return null
		return entry.lookFor(stack).equippable(slot)
	}

	override fun clientInfoFor(assetId: Identifier): EquipmentClientInfo? {
		if (!settings.armorOverrides) return null
		return loaded.madeAssets[assetId]
	}

	@Volatile
	private var resources: ResourceManager? = null
	private val legacyExists = ConcurrentHashMap<Identifier, Boolean>()

	override fun legacyLayerTexture(textureId: Identifier, leggings: Boolean): Identifier? {
		if (!settings.legacyPaths) return null
		val legacy = textureId.withPath { "textures/models/armor/${it}_layer_${if (leggings) 2 else 1}.png" }
		val exists = legacyExists.computeIfAbsent(legacy) {
			(resources ?: Minecraft.getInstance().resourceManager).getResource(it).isPresent
		}
		return if (exists) legacy else null
	}

	fun useResources(manager: ResourceManager?) {
		resources = manager
		legacyExists.clear()
	}

	fun use(data: Loaded) {
		loaded = data
	}

	fun load(manager: ResourceManager): Loaded {
		val byId = HashMap<String, Entry>()
		val made = HashMap<Identifier, EquipmentClientInfo>()
		for ((fileId, resource) in PackFiles.list(manager, FOLDER, recognisedNamespaces)) {
			try {
				val o = PackFiles.read(resource).asJsonObject
				val base = lookFrom(o, made)
				val variants = o.get("overrides")?.asJsonArray?.map { v ->
					val vo = v.asJsonObject
					Predicates.read(vo.get("predicate"), fileId.toString()) to lookFrom(vo, made)
				} ?: listOf()
				val entry = Entry(base, variants)

				for (itemId in o.getAsJsonArray("item_ids") ?: error("missing item_ids")) byId[itemId.asString] = entry
			} catch (e: Exception) {
				PackFiles.log.warn("Skipping armour override {}: {}", fileId, e.message)
				dev.ashtonite365.apex.util.trail.Trail.crumb(dev.ashtonite365.apex.util.trail.Crumb.TEXTURE_FILE_SKIPPED, fileId)
			}
		}
		return Loaded(byId, made)
	}

	private fun lookFrom(o: JsonObject, made: MutableMap<Identifier, EquipmentClientInfo>): Look {
		val model = o.get("model")
		val layers = o.get("layers")
		require((model == null) != (layers == null)) { "needs exactly one of model and layers" }
		if (model != null) return Look(Identifier.parse(model.asString))
		val id = Identifier.fromNamespaceAndPath("apex", "sentinel/armor/${madeAssetCounter.getAndIncrement()}")
		made[id] = assetFromLayers(layers!!)
		return Look(id)
	}

	private fun assetFromLayers(json: JsonElement): EquipmentClientInfo {
		val layers = json.asJsonArray.map { l ->
			val lo = l.asJsonObject
			val tinted = lo.get("tint")?.asBoolean ?: false
			EquipmentClientInfo.Layer(
				Identifier.parse(lo.get("identifier").asString),
				if (tinted) Optional.of(EquipmentClientInfo.Dyeable(Optional.of(UNDYED_TINT))) else Optional.empty(),
				false,
			)
		}
		return EquipmentClientInfo(
			mapOf(EquipmentClientInfo.LayerType.HUMANOID to layers, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS to layers),
			listOf(),
		)
	}

	object Listener : SimplePreparableReloadListener<Loaded>() {
		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) = load(manager)
		override fun apply(data: Loaded, manager: ResourceManager, profiler: ProfilerFiller) {
			use(data)

			useResources(null)
		}
	}
}
