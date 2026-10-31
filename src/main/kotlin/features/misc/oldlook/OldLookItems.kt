/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: draws SkyBlock items with their pre-resource-pack look while Hypixel's pack is skipped.
 * Original implementation. Data: assets/apex/old_look/old_look.json (see OldLookData.kt and the Credits menu).
 */
package dev.ashtonite365.apex.features.misc.oldlook

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.MapMaker
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import java.util.UUID
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip
import dev.ashtonite365.apex.util.Base64Util.padToValidBase64
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

object OldLookItems {
	const val RESOURCE = "/assets/apex/old_look/old_look.json"

	@Volatile
	var bundle: OldLookBundle? = null
		private set
	private val loading = AtomicBoolean(false)

	fun ensureLoading() {
		if (bundle != null || !loading.compareAndSet(false, true)) return
		thread(name = "Apex classic item looks loader", isDaemon = true) {
			try {
				bundle = loadBundle()
				Apex.logger.info("Classic Item Looks: loaded ${bundle?.items?.size} items, ${bundle?.rules?.size} state rules")
			} catch (e: Exception) {
				Apex.logger.warn("Classic Item Looks: could not load $RESOURCE, items keep their current look", e)
			}
		}
	}

	fun loadBundle(): OldLookBundle {
		val text = OldLookItems::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
			?: error("$RESOURCE missing from the jar")
		return OldLookBundle.parse(text)
	}

	@JvmStatic
	val enabled: Boolean
		get() = OldLookPolicy.active(ServerResourcePackSkip.TConfig.skipPack, ServerResourcePackSkip.TConfig.classicLooks)

	@Volatile
	private var hookEntered = false

	private class Slot(
		val model: Identifier,
		val glintOverride: Boolean?,
		val dye: DyedItemColor?,

		val look: ItemStack?,

		val cooldownLook: ItemStack?,
	)

	private val slots: ConcurrentMap<Any, Slot> = MapMaker().weakKeys().makeMap()
	private val profiles = HashMap<OldLookEntry, ResolvableProfile>()

	@JvmStatic
	fun substitute(stack: ItemStack): ItemStack {
		hookEntered = true
		if (!enabled) return stack
		val model = stack.get(DataComponents.ITEM_MODEL) ?: return stack
		if (!OldLookPolicy.replaces(model.namespace, model.path)) return stack
		val data = bundle ?: run { ensureLoading(); return stack }
		val key: Any = stack.get(DataComponents.CUSTOM_DATA) ?: stack
		val glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)
		val dye = stack.get(DataComponents.DYED_COLOR)
		var slot = slots[key]
		if (slot == null || slot.model != model || slot.glintOverride != glint || slot.dye != dye) {
			slot = makeSlot(data, stack, model, glint, dye)
			slots[key] = slot
		}
		val cd = slot.cooldownLook
		if (cd != null && Client.player?.cooldowns?.isOnCooldown(stack) == true) return cd
		return slot.look ?: stack
	}

	private fun makeSlot(data: OldLookBundle, stack: ItemStack, model: Identifier, glint: Boolean?, dye: DyedItemColor?): Slot {
		val res = try {
			data.resolve(stack.view().repoItemId?.skyblockId, stack.get(DataComponents.CUSTOM_DATA)?.copyTag(), model.path)
		} catch (e: Exception) {
			OldLookResolution.Keep
		}
		return when (res) {
			OldLookResolution.Keep -> Slot(model, glint, dye, null, null)
			is OldLookResolution.Look -> Slot(model, glint, dye, buildStack(res.item, res.entry, glint, dye), null)
			is OldLookResolution.Cooldown ->
				Slot(model, glint, dye, buildStack(res.otherwise, null, glint, dye), buildStack(res.onCooldown, null, glint, dye))
		}
	}

	fun buildStack(item: String, entry: OldLookEntry?, glintOverride: Boolean?, dye: DyedItemColor?): ItemStack? {
		val id = Identifier.tryParse("minecraft:$item") ?: return null
		val vanilla = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return null
		if (vanilla == Items.AIR) return null
		val out = ItemStack(vanilla)
		if (entry != null) {
			if (entry.isHead && entry.skin != null) out.set(DataComponents.PROFILE, profileFor(entry))
			entry.potion?.let { p ->
				BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace(p)).ifPresent { out.set(DataComponents.POTION_CONTENTS, PotionContents(it)) }
			}
		}
		(dye ?: entry?.color?.let(::DyedItemColor))?.let { out.set(DataComponents.DYED_COLOR, it) }
		val glint = glintOverride ?: entry?.glint?.takeIf { it }
		if (glint != null) out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint)
		return out
	}

	fun profileFor(entry: OldLookEntry): ResolvableProfile = synchronized(profiles) {
		profiles.getOrPut(entry) { headProfile(entry.skin!!, entry.uuid) }
	}

	fun headProfile(skin: String, uuid: String?): ResolvableProfile {
		val value = skin.padToValidBase64()
		val id = uuid?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.nameUUIDFromBytes(value.encodeToByteArray())
		val props = PropertyMap(ImmutableMultimap.of("textures", Property("textures", value)))
		return ResolvableProfile.createResolved(GameProfile(id, "", props))
	}

	private var activeTicks = 0
	private var warnedHook = false

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!enabled) return
		ensureLoading()
		if (warnedHook || hookEntered || Client.player == null) return
		if (++activeTicks >= 1200) {
			warnedHook = true
			Apex.logger.warn("Classic Item Looks: the item model hook never ran (mixin target mismatch?); items keep their current look")
		}
	}
}
