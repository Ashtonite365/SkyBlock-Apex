// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import dev.ashtonite365.apex.repo.itemsSafe
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUForgeRecipe
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.tr

object SBForgeRecipeRenderer
	: GenericRecipeRenderer<NEUForgeRecipe> {
	private const val RADIUS = 30

	override val typ = NEUForgeRecipe::class.java
	override val icon: ItemStack by lazy { Blocks.FURNACE.asItem().defaultInstance }
	override val title: Component
		get() = tr("apex.category.forge", "Forge Recipes")
	override val identifier: Identifier
		get() = Apex.identifier("forge_recipe")
	override val displayHeight get() = 104

	override fun render(recipe: NEUForgeRecipe, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {
		val arrow = layouter.arrowAt(bounds.minX + 90, bounds.minY + 45)
		val hover = Component.empty().append(Component.translatableEscape("apex.recipe.forge.time", recipe.duration.seconds))
		val note = recipe.extraText
		if (!note.isNullOrBlank()) hover.append(Component.nullToEmpty("\n")).append(Component.nullToEmpty(note))
		layouter.hoverArea(arrow, hover)

		val fire = Point(bounds.minX + 41, bounds.minY + 46)
		layouter.flameAt(fire, 25)
		val inputs = recipe.inputs
		if (inputs.size == 1) {
			layouter.itemSlotAt(fire.x, fire.y - 18, SBItemStack(inputs[0]), RecipeLayouter.SlotRole.SMALL_INPUT)
		} else {
			for ((n, input) in inputs.withIndex()) {
				val angle = 2 * Math.PI * n / inputs.size
				val x = (fire.x + cos(angle) * RADIUS).toInt()
				val y = (fire.y + sin(angle) * RADIUS).toInt()
				layouter.itemSlotAt(x, y, SBItemStack(input), RecipeLayouter.SlotRole.SMALL_INPUT)
			}
		}
		layouter.itemSlotAt(bounds.minX + 124, bounds.minY + 46, SBItemStack(recipe.outputStack), RecipeLayouter.SlotRole.BIG_OUTPUT)
	}

	override fun getInputs(recipe: NEUForgeRecipe): Collection<SBItemStack> = recipe.inputs.mapNotNull { ingredient -> SBItemStack(ingredient) }

	override fun getOutputs(recipe: NEUForgeRecipe): Collection<SBItemStack> = listOfNotNull(SBItemStack(recipe.outputStack))

	override fun findAllRecipes(coreRepository: NEURepository): Iterable<NEUForgeRecipe> =
		coreRepository.items.itemsSafe.values.flatMap { it.recipes }.filterIsInstance<NEUForgeRecipe>()
}
