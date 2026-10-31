/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * APEX-26.3 PORT: JEI recipe categories for SkyBlock recipes (replaces the REI categories).
 */
package dev.ashtonite365.apex.compat.jei

import io.github.moulberry.repo.data.NEUNpcShopRecipe
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.GenericRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.catalogItemId

abstract class LayouterCategory<T : Any>(
	val type: IRecipeType<T>,
	private val title: Component,
	private val icon: IDrawable?,
	private val width: Int,
	private val height: Int,
) : IRecipeCategory<T> {
	abstract fun layout(recipe: T, bounds: Rectangle, layouter: RecipeLayouter)
	abstract fun allRecipes(): List<T>

	override fun getRecipeType(): IRecipeType<T> = type
	override fun getTitle(): Component = title
	override fun getIcon(): IDrawable? = icon
	override fun getWidth(): Int = width
	override fun getHeight(): Int = height
	private fun bounds() = Rectangle(0, 0, width, height)
	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: T, focuses: IFocusGroup) =
		layout(recipe, bounds(), JeiRecipeLayouter.forSlots(builder))

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: T, focuses: IFocusGroup) =
		layout(recipe, bounds(), JeiRecipeLayouter.forExtras(builder))
}

class GenericJeiRecipe<T : Any>(val recipe: T)

class GenericJeiCategory<T : Any>(val renderer: GenericRecipeRenderer<T>, guiHelper: IGuiHelper) :
	LayouterCategory<GenericJeiRecipe<T>>(
		IRecipeType.create(renderer.identifier, GenericJeiRecipe::class.java as Class<GenericJeiRecipe<T>>),
		renderer.title, guiHelper.createDrawableItemStack(renderer.icon),
		renderer.displayWidth, renderer.displayHeight,
	) {
	override fun layout(recipe: GenericJeiRecipe<T>, bounds: Rectangle, layouter: RecipeLayouter) =
		renderer.render(recipe.recipe, bounds, layouter, null)

	override fun allRecipes(): List<GenericJeiRecipe<T>> =
		renderer.findAllRecipes(RepoManager.coreRepo).map { GenericJeiRecipe(it) }
}

private fun MCFontWidthHalf(s: String) = dev.ashtonite365.apex.util.Client.font.width(s) / 2

class ShopJeiCategory(guiHelper: IGuiHelper) : LayouterCategory<NEUNpcShopRecipe>(
	IRecipeType.create(Apex.MOD_ID, "npc_shopping", NEUNpcShopRecipe::class.java),
	Component.literal("SkyBlock NPC Shopping"), guiHelper.createDrawableItemStack(ItemStack(Items.EMERALD)), 150, 66,
) {
	override fun allRecipes(): List<NEUNpcShopRecipe> = RepoManager.getAllRecipes().filterIsInstance<NEUNpcShopRecipe>().distinct().toList()

	override fun layout(recipe: NEUNpcShopRecipe, bounds: Rectangle, layouter: RecipeLayouter) {
		val point = Point(bounds.centerX, bounds.centerY)
		layouter.itemSlotAt(point.x - 2 - 18 / 2, point.y - 18 - 6,
		                        SBItemStack(recipe.isSoldBy.catalogItemId), RecipeLayouter.SlotRole.DISPLAY)
		layouter.arrowAt(point.x - 2 - 24 / 2, point.y - 6)
		val cost = recipe.cost
		for ((i, item) in cost.withIndex()) {
			layouter.itemSlotAt(point.x - 14 - 18, point.y + i * 18 - 18 * cost.size / 2,
			                        SBItemStack(item), RecipeLayouter.SlotRole.SMALL_INPUT)
		}
		layouter.itemSlotAt(point.x + 18, point.y - 18 / 2, SBItemStack(recipe.result), RecipeLayouter.SlotRole.BIG_OUTPUT)
	}
}
