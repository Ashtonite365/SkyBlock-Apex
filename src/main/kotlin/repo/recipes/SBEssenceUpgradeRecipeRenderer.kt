// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import io.github.moulberry.repo.NEURepository
import me.shedaniel.math.Rectangle
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.EssenceRecipeProvider.EssenceUpgradeRecipe
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.tr

object SBEssenceUpgradeRecipeRenderer : GenericRecipeRenderer<EssenceUpgradeRecipe> {
	override val typ = EssenceUpgradeRecipe::class.java
	override val title: Component
		get() = tr("apex.category.essence", "Essence Upgrades")
	override val identifier: Identifier
		get() = Apex.identifier("essence_upgrade")

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	override val icon: ItemStack by lazy { SBItemStack(ItemId("ESSENCE_WITHER")).asImmutableItemStack().current() }

	override fun render(recipe: EssenceUpgradeRecipe, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {
		val item = mainItem ?: SBItemStack(recipe.itemId)
		val midY = bounds.centerY
		val after = recipe.starCountAfter
		layouter.itemSlotAt(bounds.minX + 12, midY - 17, item.copy(stars = after - 1), RecipeLayouter.SlotRole.SMALL_INPUT)
		layouter.itemSlotAt(bounds.minX + 12, midY + 1, SBItemStack(recipe.essenceIngredient), RecipeLayouter.SlotRole.SMALL_INPUT)
		layouter.itemSlotAt(bounds.maxX - 28, midY - 8, item.copy(stars = after), RecipeLayouter.SlotRole.SMALL_OUTPUT)
		val extras = recipe.extraItems
		layouter.arrowAt(bounds.centerX - 12, if (extras.isEmpty()) midY - 8 else midY + 9)
		val rowStart = bounds.centerX - extras.size * 8 - 1
		extras.forEachIndexed { n, extra ->
			layouter.itemSlotAt(rowStart + n * 18, midY - 9, SBItemStack(extra), RecipeLayouter.SlotRole.SMALL_INPUT)
		}
	}

	override fun getInputs(recipe: EssenceUpgradeRecipe): Collection<SBItemStack> = recipe.allInputs.mapNotNull { ingredient -> SBItemStack(ingredient) }

	override fun getOutputs(recipe: EssenceUpgradeRecipe): Collection<SBItemStack> = listOfNotNull(SBItemStack(recipe.itemId))

	override fun findAllRecipes(coreRepository: NEURepository): Iterable<EssenceUpgradeRecipe> = RepoManager.essenceRecipeProvider.recipes
}
