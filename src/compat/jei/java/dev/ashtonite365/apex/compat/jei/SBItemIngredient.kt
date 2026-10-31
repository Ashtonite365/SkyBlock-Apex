// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.jei

import com.mojang.serialization.Codec
import mezz.jei.api.gui.builder.ITooltipBuilder
import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ItemId

object SBItemIngredientType : IIngredientType<SBItemStack> {
	override fun getIngredientClass(): Class<out SBItemStack> = SBItemStack::class.java
	override fun getUid(): String = "apex:skyblock_item"
}

val SBItemCodec: Codec<SBItemStack> = SBItemStack.CODEC

object SBItemIngredientHelper : IIngredientHelper<SBItemStack> {
	override fun getIngredientType(): IIngredientType<SBItemStack> = SBItemIngredientType

	override fun getDisplayName(ingredient: SBItemStack): String = SkyblockEntries.searchName(ingredient)

	override fun getUid(ingredient: SBItemStack, context: UidContext): Any = SkyblockEntries.identity(ingredient)
	override fun getIdentifier(ingredient: SBItemStack): Identifier = ingredient.catalogItemId.identifier
	override fun getDisplayModId(ingredient: SBItemStack): String = SkyblockEntries.SOURCE_MOD
	override fun getAmount(ingredient: SBItemStack): Long = ingredient.getStackSize().toLong()

	override fun copyWithAmount(ingredient: SBItemStack, amount: Long): SBItemStack =
		ingredient.copy().also { it.setStackSize(amount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }

	override fun copyIngredient(ingredient: SBItemStack): SBItemStack = ingredient.copy()
	override fun normalizeIngredient(ingredient: SBItemStack): SBItemStack = SkyblockEntries.normalize(ingredient)
	override fun isValidIngredient(ingredient: SBItemStack): Boolean = ingredient.catalogItemId != ItemId.NULL
	override fun getCheatItemStack(ingredient: SBItemStack): ItemStack = SkyblockEntries.cheatStack(ingredient)
	override fun getErrorInfo(ingredient: SBItemStack?): String = "SBItemStack(${ingredient?.catalogItemId})"
}

object SBItemIngredientRenderer : IIngredientRenderer<SBItemStack> {
	override fun render(guiGraphics: GuiGraphicsExtractor, ingredient: SBItemStack) =
		SkyblockEntries.draw(guiGraphics, ingredient, 0, 0)

	@Deprecated("Older tooltip entry point, still called by some versions")
	override fun getTooltip(ingredient: SBItemStack, tooltipFlag: TooltipFlag): List<Component> =
		buildTooltip(ingredient, Item.TooltipContext.EMPTY, tooltipFlag)

	override fun getTooltip(
		tooltip: ITooltipBuilder, ingredient: SBItemStack, tooltipContext: Item.TooltipContext, player: Player?, tooltipFlag: TooltipFlag,
	) {
		tooltip.addAll(buildTooltip(ingredient, tooltipContext, tooltipFlag))
	}

	var canUseVanillaTooltipEvents: Boolean
		get() = SkyblockEntries.canUseVanillaTooltipEvents
		set(value) { SkyblockEntries.canUseVanillaTooltipEvents = value }

	fun buildTooltip(ingredient: SBItemStack, ctx: Item.TooltipContext, flag: TooltipFlag): List<Component> =
		SkyblockEntries.tooltip(ingredient, ctx, flag)
}
