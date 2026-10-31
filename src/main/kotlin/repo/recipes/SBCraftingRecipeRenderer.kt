// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import dev.ashtonite365.apex.repo.itemsSafe
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUCraftingRecipe
import me.shedaniel.math.Rectangle
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.tr

object SBCraftingRecipeRenderer
	: GenericRecipeRenderer<NEUCraftingRecipe> {
	private const val CELL = 18

	override val typ = NEUCraftingRecipe::class.java
	override val icon: ItemStack by lazy { Blocks.CRAFTING_TABLE.asItem().defaultInstance }
	override val title: Component
		get() = tr("apex.category.crafting", "SkyBlock Crafting")
	override val identifier: Identifier
		get() = Apex.identifier("crafting_recipe")

	override fun render(recipe: NEUCraftingRecipe, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {

		val left = bounds.centerX - 58
		val top = bounds.centerY - 27
		val arrow = layouter.arrowAt(left + 60, top + CELL)
		val note = recipe.extraText
		if (!note.isNullOrBlank()) layouter.hoverArea(arrow, Component.nullToEmpty(note))

		for (column in 0..2) for (row in 0..2) {
			val input = recipe.inputs[row * 3 + column]
			layouter.itemSlotAt(left + 1 + column * CELL, top + 1 + row * CELL, SBItemStack(input), RecipeLayouter.SlotRole.SMALL_INPUT)
		}
		layouter.itemSlotAt(left + 95, top + 19, SBItemStack(recipe.output), RecipeLayouter.SlotRole.BIG_OUTPUT)
	}

	override fun getInputs(recipe: NEUCraftingRecipe): Collection<SBItemStack> = recipe.allInputs.mapNotNull { ingredient -> SBItemStack(ingredient) }

	override fun getOutputs(recipe: NEUCraftingRecipe): Collection<SBItemStack> = listOfNotNull(SBItemStack(recipe.output))

	override fun findAllRecipes(coreRepository: NEURepository): Iterable<NEUCraftingRecipe> =
		coreRepository.items.itemsSafe.values.flatMap { it.recipes }.filterIsInstance<NEUCraftingRecipe>()
}
