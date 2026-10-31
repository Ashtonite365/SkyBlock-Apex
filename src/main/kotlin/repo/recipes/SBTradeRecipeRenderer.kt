package dev.ashtonite365.apex.repo.recipes

import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUKatUpgradeRecipe
import io.github.moulberry.repo.data.NEUTradeRecipe
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.util.tr

object SBTradeRecipeRenderer : GenericRecipeRenderer<NEUTradeRecipe> {
	override fun render(recipe: NEUTradeRecipe, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {
		val cy = bounds.centerY - 9
		SBItemStack(recipe.cost)?.let { layouter.itemSlotAt(bounds.minX + 30, cy, it, RecipeLayouter.SlotRole.SMALL_INPUT) }
		val arrow = layouter.arrowAt(bounds.centerX - 12, cy)
		if (recipe.min > 0 || recipe.max > 0)
			layouter.hoverArea(arrow, listOf(Component.literal("§7Amount: ${recipe.min}-${recipe.max}")))
		SBItemStack(recipe.result)?.let { layouter.itemSlotAt(bounds.maxX - 48, cy - 4, it, RecipeLayouter.SlotRole.BIG_OUTPUT) }
	}

	override fun getInputs(recipe: NEUTradeRecipe) = listOfNotNull(SBItemStack(recipe.cost))
	override fun getOutputs(recipe: NEUTradeRecipe) = listOfNotNull(SBItemStack(recipe.result))
	override val icon: ItemStack by lazy { ItemStack(Items.EMERALD) }
	override val title: Component = tr("apex.category.trade", "Trades")
	override val identifier: Identifier = Apex.identifier("trade_recipe")
	override fun findAllRecipes(coreRepository: NEURepository): Iterable<NEUTradeRecipe> =
		coreRepository.items.itemsSafe.values.flatMap { it.recipes }.filterIsInstance<NEUTradeRecipe>()
	override val displayHeight: Int get() = 40
	override val typ: Class<NEUTradeRecipe> get() = NEUTradeRecipe::class.java
}

object SBKatUpgradeRecipeRenderer : GenericRecipeRenderer<NEUKatUpgradeRecipe> {
	fun formatTime(seconds: Long): String {
		val d = seconds / 86400; val h = seconds % 86400 / 3600; val m = seconds % 3600 / 60
		return listOfNotNull(d.takeIf { it > 0 }?.let { "${it}d" }, h.takeIf { it > 0 }?.let { "${it}h" },
		                     m.takeIf { it > 0 }?.let { "${it}m" }).joinToString(" ").ifEmpty { "${seconds}s" }
	}

	override fun render(recipe: NEUKatUpgradeRecipe, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {
		SBItemStack(recipe.input)?.let { layouter.itemSlotAt(bounds.minX + 6, bounds.minY + 8, it, RecipeLayouter.SlotRole.SMALL_INPUT) }
		recipe.items.orEmpty().forEachIndexed { i, ing ->
			SBItemStack(ing)?.let { layouter.itemSlotAt(bounds.minX + 6 + (i % 4) * 18, bounds.minY + 30 + (i / 4) * 18, it, RecipeLayouter.SlotRole.SMALL_INPUT) }
		}
		val arrow = layouter.arrowAt(bounds.minX + 84, bounds.minY + 8)
		layouter.hoverArea(arrow, listOf(Component.literal("§6${"%,.0f".format(recipe.coins)} coins"),
		                                     Component.literal("§7Time: ${formatTime(recipe.seconds)}")))
		layouter.placeLabel(bounds.minX + 96, bounds.minY + 30, Component.literal(formatTime(recipe.seconds)))
		SBItemStack(recipe.output)?.let { layouter.itemSlotAt(bounds.maxX - 30, bounds.minY + 4, it, RecipeLayouter.SlotRole.BIG_OUTPUT) }
	}

	override fun getInputs(recipe: NEUKatUpgradeRecipe) =
		listOfNotNull(SBItemStack(recipe.input)) + recipe.items.orEmpty().mapNotNull { SBItemStack(it) }
	override fun getOutputs(recipe: NEUKatUpgradeRecipe) = listOfNotNull(SBItemStack(recipe.output))
	override val icon: ItemStack by lazy { ItemStack(Items.BONE) }
	override val title: Component = tr("apex.category.katgrade", "Kat Pet Upgrades")
	override val identifier: Identifier = Apex.identifier("kat_recipe")
	override fun findAllRecipes(coreRepository: NEURepository): Iterable<NEUKatUpgradeRecipe> =
		coreRepository.items.itemsSafe.values.flatMap { it.recipes }.filterIsInstance<NEUKatUpgradeRecipe>()
	override val displayHeight: Int get() = 70
	override val typ: Class<NEUKatUpgradeRecipe> get() = NEUKatUpgradeRecipe::class.java
}
