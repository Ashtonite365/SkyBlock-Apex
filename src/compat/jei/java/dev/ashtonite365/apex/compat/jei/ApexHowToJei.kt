// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jei

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.math.Rectangle
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.inputs.IJeiInputHandler
import mezz.jei.api.gui.inputs.IJeiUserInput
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.gui.widgets.IRecipeWidget
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.types.IRecipeType
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.features.items.recipes.itemlist.HowToPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigation
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigator
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageCanvas
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageLine
import dev.ashtonite365.apex.features.items.recipes.itemlist.PricesPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ScrollLinesBlock
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.ItemId

data class ItemRef(val itemId: String)

private fun IJeiUserInput.isLeftClick(): Boolean =
	key.type == InputConstants.Type.MOUSE && key.value == InputConstants.MOUSE_BUTTON_LEFT

private class LineClick(val rect: Rectangle, val onClick: () -> Unit) : IJeiInputHandler {
	override fun getArea(): ScreenRectangle = ScreenRectangle(rect.x, rect.y, rect.width, rect.height)
	override fun handleInput(mouseX: Double, mouseY: Double, input: IJeiUserInput): Boolean {
		if (!input.isLeftClick()) return false
		if (!input.isSimulate) Faults.attempt("Item list page click failed") { onClick() }
		return true
	}
}

private class ScrollLinesWidget(private val block: ScrollLinesBlock) : IRecipeWidget {
	override fun getPosition(): ScreenPosition = ScreenPosition(0, 0)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) = block.draw(guiGraphics)
}

private class ScrollLinesInput(private val block: ScrollLinesBlock) : IJeiInputHandler {
	override fun getArea(): ScreenRectangle = block.box.let { ScreenRectangle(it.x, it.y, it.width, it.height) }
	override fun handleMouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
		block.scroll(mouseX, mouseY, scrollY)

	override fun handleInput(mouseX: Double, mouseY: Double, input: IJeiUserInput): Boolean {
		if (!input.isLeftClick()) return false
		if (input.isSimulate) return true
		return block.click(mouseX, mouseY)
	}
}

private class ItemPageCanvas(
	private val base: RecipeLayouter,
	private val slots: IRecipeLayoutBuilder?,
	private val extras: IRecipeExtrasBuilder?,
) : PageCanvas, RecipeLayouter by base {
	override fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>) {
		extras?.addWidget(WrappedTextWidget(x, y, width, lines))
	}

	override fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>) {
		extras?.addWidget(MobEntityWidget(mob, box, tooltip))
	}

	override fun clickArea(box: Rectangle, onClick: () -> Unit) {
		extras?.addInputHandler(LineClick(box, onClick))
	}

	override fun scrollLines(box: Rectangle, lines: List<PageLine>) {
		val builder = extras ?: return
		val block = ScrollLinesBlock(box, lines)
		builder.addWidget(ScrollLinesWidget(block))
		builder.addInputHandler(ScrollLinesInput(block))
	}

	override fun lookupItems(items: List<SBItemStack>) {
		if (items.isEmpty()) return
		slots?.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)?.addIngredients(SBItemIngredientType, items)
	}
}

abstract class ItemPageJeiCategory(type: IRecipeType<ItemRef>, private val page: ItemListPage<ItemPageRef>, guiHelper: IGuiHelper) :
	LayouterCategory<ItemRef>(type, page.title, guiHelper.createDrawableItemStack(page.icon), page.width, page.height) {
	private fun area() = Rectangle(0, 0, page.width, page.height)

	override fun allRecipes(): List<ItemRef> = page.allRecipes().map { ItemRef(it.itemId) }

	override fun layout(recipe: ItemRef, bounds: Rectangle, layouter: RecipeLayouter) =
		page.draw(ItemPageRef(recipe.itemId), bounds, ItemPageCanvas(layouter, null, null))

	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: ItemRef, focuses: IFocusGroup) =
		page.draw(ItemPageRef(recipe.itemId), area(), ItemPageCanvas(JeiRecipeLayouter.forSlots(builder), builder, null))

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: ItemRef, focuses: IFocusGroup) =
		page.draw(ItemPageRef(recipe.itemId), area(), ItemPageCanvas(JeiRecipeLayouter.forExtras(builder), null, builder))
}

class HowToJeiCategory(guiHelper: IGuiHelper) : ItemPageJeiCategory(TYPE, HowToPage, guiHelper) {
	companion object {
		val TYPE: IRecipeType<ItemRef> = IRecipeType.create(HowToPage.id, ItemRef::class.java)
	}
}

class PricesJeiCategory(guiHelper: IGuiHelper) : ItemPageJeiCategory(TYPE, PricesPage, guiHelper) {
	companion object {
		val TYPE: IRecipeType<ItemRef> = IRecipeType.create(PricesPage.id, ItemRef::class.java)
	}
}

object ApexHowToJei {
	var runtime: IJeiRuntime? = null
		set(value) {
			field = value
			if (value != null) {
				if (ItemListNavigation.navigator == null) ItemListNavigation.navigator = JeiLinks
			} else ItemListNavigation.release(JeiLinks)
		}

	fun openRecipesFor(itemId: String) {
		val rt = runtime ?: return
		val target = rt.jeiHelpers.focusFactory.createFocus(RecipeIngredientRole.OUTPUT, SBItemIngredientType, SBItemStack(ItemId(itemId)))
		rt.recipesGui.show(target)
	}

	private object JeiLinks : ItemListNavigator {
		override fun leaveRecipeScreen() {
			ViewerTabs.navigate("items", null)
		}

		override fun openMob(mobId: String?) {
			ViewerTabs.navigate("mobs", mobId)
		}

		override fun openItemPage(page: ItemListPage<ItemPageRef>, itemId: String) {
			ViewerTabs.navigate(if (page === PricesPage) "prices" else "howto", itemId)
		}

		override fun openRecipesFor(itemId: String) = ApexHowToJei.openRecipesFor(itemId)
	}
}
