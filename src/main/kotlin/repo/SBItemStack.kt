// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.github.moulberry.repo.data.NEUIngredient
import io.github.moulberry.repo.data.NEUItem
import net.minecraft.ChatFormatting
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.MutableComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.ItemCache.isBroken
import dev.ashtonite365.apex.repo.ItemCache.withFallback
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ReforgeKey
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.blue
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.reforgeKey
import dev.ashtonite365.apex.util.starCount
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.ItemEditor
import dev.ashtonite365.apex.util.mc.DisplayStackCount
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.appendLore
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.modifyLore
import dev.ashtonite365.apex.util.updateCustomData
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.prependChild
import dev.ashtonite365.apex.util.rebuildComponent
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.catalogItemId
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.useMatch

data class SBItemStack(
	val catalogItemId: ItemId,
	val repoItem: NEUItem?, private var stackSize: Int,
	private var petBlob: PetData?,
	val stars: Int = 0, val reforge: ReforgeKey? = null,
	val extraLore: List<Component> = emptyList(), val fallback: KeptStack? = null,
) {

	constructor(catalogItemId: ItemId, stackSize: Int = 1) :
		this(catalogItemId, RepoManager.getCoreItem(catalogItemId), stackSize, RepoManager.getPotentialStubPetData(catalogItemId))

	constructor(catalogItemId: ItemId, petBlob: PetData) :
		this(catalogItemId, RepoManager.getCoreItem(catalogItemId), 1, petBlob)

	private var built: KeptStack? = null

	private var builtAt = -1

	fun getStackSize(): Int = stackSize
	fun setStackSize(size: Int) {
		stackSize = size
		built = null
	}

	fun getPetData(): PetData? = petBlob
	fun setPetData(pet: PetData?) {
		petBlob = pet
		built = null
	}

	val hasModifiers: Boolean get() = petBlob != null || reforge != null

	val breakingPower
		get() = breakingPowerLine.useMatch(repoItem?.lore?.firstOrNull()?.stripSectionCodes()) { group("power").toInt() } ?: 0

	@ExpensiveItemCacheApi
	val itemType: ItemType? get() = ItemType.fromItemStack(asImmutableItemStack())

	@ExpensiveItemCacheApi
	val rarity: Rarity? get() = Rarity.fromItem(asImmutableItemStack())

	fun estimateLore(): List<Component> =
		repoItem?.lore.orEmpty().map(ItemCache::un189Lore) + extraLore

	fun isWarm(): Boolean = (built != null && builtAt == ItemCache.textureEpoch) || ItemCache.hasCacheFor(catalogItemId)

	@OptIn(ExpensiveItemCacheApi::class)
	fun asLazyImmutableItemStack(): KeptStack? = if (isWarm()) asImmutableItemStack() else null

	@ExpensiveItemCacheApi
	fun asImmutableItemStack(): KeptStack = display()

	private fun display(): KeptStack {
		val epoch = ItemCache.textureEpoch
		built?.takeIf { builtAt == epoch }?.let { return it }
		return buildDisplay().also { built = it; builtAt = epoch }
	}

	@OptIn(ExpensiveItemCacheApi::class)
	private fun buildDisplay(): KeptStack {
		if (catalogItemId == ItemId.COINS) return ItemCache.coinItem(stackSize).withFallback(fallback)
		val base = repoItem.asItemStack(idHint = catalogItemId, loreReplacements = petPlaceholders().takeIf { petBlob != null })
			.withFallback(fallback)
		if (repoItem == null && fallback != null) return fallback.withCount(stackSize)
		return base.editedCopy {
			amount = DisplayStackCount.clamp(stackSize, itemType.value().defaultMaxStackSize)
			val statsBefore = parseStatBlock(this)
			reforge?.let { applyReforge(this, it) }
			if (extraLore.isNotEmpty()) appendLore(extraLore)
			if (stars > 0) applyStars(this, statsBefore)
		}
	}

	private fun petPlaceholders(): MutableMap<String, String> {
		val pet = petBlob ?: return mutableMapOf()
		val info = SafeRepoConstants.petNumbers()[pet.petId]?.get(pet.rarity)
		if (pet.isStub) return PetPlaceholders.stub(info).toMutableMap()
		val level = pet.levelData.currentLevel
		val out = info?.let { PetPlaceholders.levelMap(it, level) }.orEmpty().toMutableMap()
		out["LVL"] = level.toString()
		return out
	}

	private fun applyReforge(stack: ItemEditor, id: ReforgeKey) {
		val found = ReforgeStore.modifierLut[id] ?: return
		val tier = Rarity.fromItem(stack)

		val stats = found.statsByTier?.get(tier) ?: return
		val name = stack.displayNameAccordingToNbt

		val colour = name.style.color ?: name.siblings.firstNotNullOfOrNull { it.style.color }
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("").withStyle(Style.EMPTY.withItalic(false))
			.append(Component.literal(found.prefixName + " ").withStyle(Style.EMPTY.withColor(colour)))
			.also { root -> if (name.contents == net.minecraft.network.chat.contents.PlainTextContents.EMPTY) name.siblings.forEach(root::append) else root.append(name) })
		stack.updateCustomData { it.putString("modifier", id.id) }
		appendEnhancedStats(stack, stats, BuffKind.REFORGE)
		val ability = found.abilityByTier?.get(tier) ?: return
		val lines = Client.font.splitter.splitLines(ability, 200, Style.EMPTY)
		stack.modifyLore { lore ->
			val at = lore.indexOfFirst { it.string.isBlank() }.let { if (it < 0) lore.size else it }
			lore.take(at) + listOf(Component.literal(""), Component.literal("${found.prefixName} Bonus").blue()) +
				lines.map { Component.literal(it.string).grey() } + lore.drop(at)
		}
	}

	private fun applyStars(stack: ItemEditor, statsBefore: List<Companion.StatLine>) {
		val type = ItemType.fromItemStack(stack)
		val counted = if (type?.isDungeon == true) stars.coerceAtMost(5) else stars
		stack.updateCustomData { it.putInt("upgrade_level", stars) }
		val buffs = statsBefore.filter { it.statFormatting.isStarAffected }
			.associate { line -> line.statName to (line.valueNum ?: 0.0) * 0.02 * counted }
		if (buffs.isNotEmpty()) appendEnhancedStats(stack, buffs, BuffKind.STAR_BUFF)
		stack.set(DataComponents.CUSTOM_NAME, (stack.displayNameAccordingToNbt.copy()).append(StatLore.starSuffix(stars)))
	}

	companion object Companion {
		private val breakingPowerLine = "Breaking Power (?<power>[0-9]+)".toPattern()

		val EMPTY: SBItemStack = SBItemStack(ItemId.NULL, null, 0, null)

		val CODEC: Codec<SBItemStack> = RecordCodecBuilder.create { builder ->
			builder.group(
				ItemId.CODEC.fieldOf("skyblockId").forGetter { it.catalogItemId },
				Codec.INT.fieldOf("count").forGetter { it.getStackSize() },
			).apply(builder) { id, count -> SBItemStack(id, count) }
		}

		val PACKET_CODEC: StreamCodec<in RegistryFriendlyByteBuf, SBItemStack> =
			StreamCodec.composite(ItemId.PACKET_CODEC, SBItemStack::catalogItemId, ByteBufCodecs.VAR_INT, SBItemStack::getStackSize, ::SBItemStack)

		operator fun invoke(itemStack: ItemStack): SBItemStack = invoke(itemStack.view())

		operator fun invoke(stack: ItemView): SBItemStack =
			(stack.repoItemId ?: ItemId.NULL).let { id ->
				SBItemStack(
					id, RepoManager.getCoreItem(id), stack.amount, stack.petBlob?.let(PetData::fromHypixel),
					stars = stack.starCount(), reforge = stack.reforgeKey(),
				)
			}

		operator fun invoke(coreIngredient: NEUIngredient): SBItemStack? =
			if (coreIngredient.catalogItemId == ItemId.EMPTY_SLOT) null
			else SBItemStack(coreIngredient.catalogItemId, coreIngredient.amount.toInt())

		fun passthrough(itemStack: KeptStack): SBItemStack =
			SBItemStack(ItemId.NULL, null, itemStack.amount, null, fallback = itemStack)

		fun parseStatBlock(itemStack: ItemView): List<StatLine> = StatLore.block(itemStack)

		fun parseStatLine(line: Component): StatLine? = StatLore.read(line)

		fun appendEnhancedStats(itemStack: ItemEditor, reforgeStats: Map<String, Double>, buffKind: BuffKind) =
			StatLore.applyBuffs(itemStack, reforgeStats, buffKind)

		fun statIdToName(statId: String): String = StatLore.humanName(statId)

		val formattingOverrides: Map<String, StatFormatting> get() = StatLore.looks

		data class StatFormatting(
			val postFix: String = "",
			val color: ChatFormatting = ChatFormatting.GREEN,
			val isStarAffected: Boolean = true,
		)

		enum class BuffKind(val color: ChatFormatting, val isHidden: Boolean = false) {
			REFORGE(ChatFormatting.BLUE),
			STAR_BUFF(ChatFormatting.RESET, isHidden = true),
			CATA_STAR_BUFF(ChatFormatting.DARK_GRAY);

			val prefix get() = if (isHidden) "" else "("
			val postFix get() = if (isHidden) "" else ")"
		}

		data class StatLine(
			val statName: String, val value: Component?,
			val valueNum: Double? = StatLore.numberIn(value), val rest: List<Component> = emptyList(),
		) {
			val statFormatting: StatFormatting get() = StatLore.lookOf(statName)

			fun addStat(amount: Double, buffKind: BuffKind): StatLine = StatLore.added(this, amount, buffKind)

			fun formatValue() = StatLore.valueText(this)

			fun rebuildComponent(abbreviateTo: Int = Int.MAX_VALUE): Component = StatLore.render(this, abbreviateTo)
		}
	}
}
