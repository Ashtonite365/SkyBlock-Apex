// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import com.mojang.serialization.Dynamic
import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUItem
import java.text.NumberFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.apache.logging.log4j.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.path.readText
import kotlin.jvm.optionals.getOrNull
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.debug.ExportedTestConstantMeta
import dev.ashtonite365.apex.repo.RepoManager.initialize
import dev.ashtonite365.apex.util.LegacyFormattingCode
import dev.ashtonite365.apex.util.LegacyTagParser
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TestUtil
import dev.ashtonite365.apex.util.ownLiteralText
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.ItemEditor
import dev.ashtonite365.apex.util.mc.ApexDataComponentTypes
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.appendLore
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loadItemFromNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.modifyLore
import dev.ashtonite365.apex.util.mc.setCustomName
import dev.ashtonite365.apex.util.mc.applySkinUrl
import dev.ashtonite365.apex.util.catalogItemId
import dev.ashtonite365.apex.util.mapComponentTree

object ItemCache : io.github.moulberry.repo.IReloadable {
	private val built = ConcurrentHashMap<String, KeptStack>()
	private val fixer = DataFixers.getDataFixer()
	private val log = LogManager.getLogger(Apex.logger.name + ".ItemCache")

	val isFlawless: Boolean get() = flawless
	private var flawless = true

	val currentSaveVersion: Int = SharedConstants.getCurrentVersion().dataVersion().version

	@ExpensiveItemCacheApi
	fun convert189ToModern(nbtComponent: CompoundTag): CompoundTag? {
		try {
			return fixUp(nbtComponent, -1)
		} catch (e: Exception) {
			flawless = false
			log.error("Data fixer rejected legacy item tag {}", nbtComponent, e)
		}
		return null
	}

	private fun fixUp(tag: Tag, fromVersion: Int): CompoundTag =
		fixer.update(References.ITEM_STACK, Dynamic(NbtOps.INSTANCE, tag), fromVersion, currentSaveVersion).value as CompoundTag

	private fun legacyTagOf(item: NEUItem): CompoundTag {
		val out = CompoundTag()
		out.put("tag", LegacyTagParser.parse(item.nbttag))
		out.putString("id", item.minecraftItemId)
		out.putByte("Count", 1.toByte())
		out.putShort("Damage", item.damage.toShort())
		return out
	}

	val ItemView.isBroken: Boolean
		get() = this.get(ApexDataComponentTypes.BROKEN_FLAG) == true

	fun KeptStack.withFallback(fallback: KeptStack?): KeptStack =
		if (fallback == null || !this.isBroken) this else fallback

	fun brokenItemStack(repoItem: NEUItem?, idHint: ItemId? = null): KeptStack = run {
		val name = repoItem?.displayName ?: idHint?.skyblockId ?: "null"
		val shownId: Any = repoItem?.skyblockItemId ?: idHint ?: "null"
		val rawId = repoItem?.skyblockItemId ?: idHint?.skyblockId ?: "null"
		KeptStack.build(Items.PAINTING) {
			setCustomName(Component.literal(name))
			appendLore(listOf(Component.translatableEscape("apex.repo.brokenitem", shownId)))
			val idTag = CompoundTag()
			idTag.putString("ID", rawId)
			set(DataComponents.CUSTOM_DATA, CustomData.of(idTag))
			this.set(ApexDataComponentTypes.BROKEN_FLAG, true)
		}
	}

	fun un189Lore(lore: String): MutableComponent = buildLegacy(lore)

	private fun buildLegacy(lore: String): MutableComponent {
		val out = Component.literal("").setStyle(Style.EMPTY.withItalic(false))
		var style = Style.EMPTY
		val pending = StringBuilder()
		fun emit() {
			if (pending.isEmpty()) return
			out.append(Component.literal(pending.toString()).setStyle(style))
			pending.setLength(0)
		}
		var at = 0
		while (at < lore.length) {
			val ch = lore[at]
			if (ch != '§') {
				pending.append(ch)
				at += 1
				continue
			}
			emit()
			if (at + 1 < lore.length) style = styleAfterCode(style, lore[at + 1])
			at += 2
		}
		emit()
		return out
	}

	private fun styleAfterCode(current: Style, code: Char): Style {
		val vanilla = (LegacyFormattingCode.byCode[code.lowercaseChar()] ?: LegacyFormattingCode.RESET).modern
		val colour = TextColor.fromLegacyFormat(vanilla) ?: return current.applyFormat(vanilla)
		return Style.EMPTY.withColor(colour)
	}

	fun tryFindFromModernFormat(catalogItemId: ItemId): CompoundTag? = modernOverlayTag(catalogItemId)

	private fun modernOverlayTag(catalogItemId: ItemId): CompoundTag? {
		val file = RepoManager.overlayData.getMostModernReadableOverlay(catalogItemId, currentSaveVersion) ?: return null
		val parsed = TagParser.parseCompoundFully(file.path.readText())
		val decoded = ExportedTestConstantMeta.SOURCE_CODEC.decode(NbtOps.INSTANCE, parsed).result().getOrNull()
			?: return null
		return fixUp(decoded.second, decoded.first.dataVersion)
	}

	@ExpensiveItemCacheApi
	private fun buildNow(item: NEUItem): KeptStack = try {
		build(item)
	} catch (failure: Exception) {
		failure.printStackTrace()
		brokenItemStack(item)
	}

	@ExpensiveItemCacheApi
	private fun build(item: NEUItem): KeptStack {
		val legacy = legacyTagOf(item)
		val overlay = tryFindFromModernFormat(item.catalogItemId)
		val modern = overlay ?: convert189ToModern(legacy) ?: return brokenItemStack(item)
		val stack = loadItemFromNbt(modern)?.toDraft() ?: return brokenItemStack(item)
		if (overlay == null) restoreLegacyExtras(stack, legacy)

		if (legacyHeadWithoutTexture(item.minecraftItemId, item.nbttag))
			try {
				OfficialItemSkins.headTexture(item.skyblockItemId, null, OfficialItemSkins.get(item.skyblockItemId), LearnedSkullTextures.get(item.skyblockItemId))
					?.let { stack.set(DataComponents.PROFILE, LearnedSkullTextures.profileOf(it)) }
			} catch (t: Throwable) { OfficialItemSkins.warnOnce("head texture for ${item.skyblockItemId}", t) }
		stack.loreAccordingToNbt = item.lore.map(::un189Lore)
		stack.displayNameAccordingToNbt = un189Lore(item.displayName)
		return stack.toStack()
	}

	private fun restoreLegacyExtras(stack: ItemEditor, legacy: CompoundTag) {
		val inner = legacy.getCompound("tag").getOrNull() ?: return
		inner.getCompound("ExtraAttributes").getOrNull()?.let { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(it)) }
		inner.getString("ItemModel").getOrNull()?.let { stack.set(DataComponents.ITEM_MODEL, Identifier.parse(it)) }

		if (legacyWantsGlint(inner)) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
	}

	fun legacyWantsGlint(tag: CompoundTag): Boolean = tag.contains("ench") || tag.contains("Enchantments")

	fun repoHeadNeedsTexture(id: String): Boolean {
		val item = runCatching { RepoManager.getCoreItem(ItemId(id)) }.getOrNull() ?: return false
		return legacyHeadWithoutTexture(item.minecraftItemId, item.nbttag)
	}

	fun legacyHeadWithoutTexture(itemId: String?, nbt: String?): Boolean =
		(itemId == "minecraft:skull" || itemId == "minecraft:player_head") && !(nbt ?: "").contains("textures:")

	fun forget(id: String) { built.remove(id); epoch++ }

	val textureEpoch: Int get() = epoch
	@Volatile private var epoch = 0

	fun forgetTexturelessHeads() {
		try { built.keys.filter { repoHeadNeedsTexture(it) }.forEach { built.remove(it) } }
		catch (t: Throwable) { OfficialItemSkins.warnOnce("head refresh", t) }
		epoch++
	}

	fun hasCacheFor(catalogItemId: ItemId): Boolean = built.containsKey(catalogItemId.skyblockId)

	@ExpensiveItemCacheApi
	fun NEUItem?.asItemStack(idHint: ItemId? = null, loreReplacements: Map<String, String>? = null): KeptStack =
		if (this == null) brokenItemStack(null, idHint) else cachedWith(this, loreReplacements)

	@ExpensiveItemCacheApi
	private fun cachedWith(item: NEUItem, loreReplacements: Map<String, String>?): KeptStack {
		val cached = built.getOrPut(item.skyblockItemId) { buildNow(item) }
		if (loreReplacements.isNullOrEmpty()) return cached
		return cached.editedCopy {
			modifyLore { lore -> lore.map { fillPlaceholders(it, loreReplacements) } }

			get(DataComponents.CUSTOM_NAME)?.let { setCustomName(fillPlaceholders(it, loreReplacements)) }
			get(DataComponents.ITEM_NAME)?.let { set(DataComponents.ITEM_NAME, fillPlaceholders(it, loreReplacements)) }
		}
	}

	private fun fillPlaceholders(text: Component, values: Map<String, String>): Component =
		text.mapComponentTree { part ->
			val literal = part.ownLiteralText ?: return@mapComponentTree part
			val filled = values.entries.fold(literal) { acc, (key, value) -> acc.replace("{$key}", value) }
			Component.literal(filled).setStyle(part.style)
		}

	private var warmScope: CoroutineScope? = null
	private val warmRequested = mutableSetOf<ItemId>()

	@OptIn(ExpensiveItemCacheApi::class)
	fun recacheSoon(item: NEUItem) {
		warmScope?.launch {
			val first = withContext(MinecraftDispatcher) { warmRequested.add(item.catalogItemId) }
			if (first) item.asItemStack()
		}
	}

	@OptIn(ExpensiveItemCacheApi::class)
	override fun reload(repo: NEURepository) {
		warmScope?.cancel("New reload invoked")
		built.clear()
		flawless = true
		if (!TestUtil.isInTest) warmEverything(repo)
	}

	@OptIn(ExpensiveItemCacheApi::class)
	private fun warmEverything(repository: NEURepository) {
		val workers = maxOf(1, Runtime.getRuntime().availableProcessors() / 4)
		val scope = CoroutineScope(
			Apex.coroutineScope.coroutineContext + SupervisorJob(Apex.globalJob) + Dispatchers.Default.limitedParallelism(workers)
		)
		val everything = repository.items?.items
		scope.launch {
			if (everything == null) return@launch
			val batches = everything.values.chunked(500)
			batches.map { batch -> async { for (item in batch) item.asItemStack() } }.awaitAll()
		}
		warmScope = scope
	}

	private const val COIN_TEXTURES = "http://textures.minecraft.net/texture/"

	fun coinItem(coinAmount: Int): KeptStack = coinPile(coinAmount)

	private fun coinPile(coinAmount: Int): KeptStack {
		val (profile, skin) = when {
			coinAmount >= 10_000_000 -> "0af8df1f-098c-3b72-ac6b-65d65fd0b668" to "7b951fed6a7b2cbc2036916dec7a46c4a56481564d14f945b6ebc03382766d3b"
			coinAmount >= 100_000 -> "94fa2455-2881-31fe-bb4e-e3e24d58dbe3" to "c9b77999fed3a2758bfeaf0793e52283817bea64044bf43ef29433f954bb52f6"
			else -> "2070f6cb-f5db-367a-acd0-64d39a7e5d1b" to "538071721cc5b4cd406ce431a13f86083a8973e1064d2f8897869930ee6e5237"
		}
		val label = "§r§6${NumberFormat.getInstance().format(coinAmount)} Coins"
		return KeptStack.build(Items.PLAYER_HEAD) build@{
			setCustomName(Component.literal(label))
			applySkinUrl(UUID.fromString(profile), COIN_TEXTURES + skin)
		}
	}

	init {
		if (TestUtil.isInTest) initialize()
	}
}

operator fun CompoundTag.set(key: String, value: String) = this.putString(key, value)

operator fun CompoundTag.set(key: String, value: Tag) = run { this.put(key, value); Unit }
