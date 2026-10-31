// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jei

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.math.Rectangle
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.ITooltipBuilder
import mezz.jei.api.gui.inputs.IJeiInputHandler
import mezz.jei.api.gui.inputs.IJeiUserInput
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.gui.widgets.IRecipeWidget
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobDropsPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPreview
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobsPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageCanvas
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageLine
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageMobs
import dev.ashtonite365.apex.features.items.recipes.itemlist.WrappedTextBlock
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.Faults

data class MobRef(val mobId: String) {
	val mob: MobEntry? get() = MobIndex.get(mobId)
}

object MobEntities {
	fun clear() = PageMobs.clear()

	fun isUnknown(mob: MobEntry): Boolean = PageMobs.isUnknown(mob)

	fun entityFor(mob: MobEntry): LivingEntity? = PageMobs.entityFor(mob)

	fun fallbackFor(mob: MobEntry): LivingEntity? = PageMobs.fallbackFor(mob)
}

class MobEntityWidget(val mob: MobEntry, val bounds: Rectangle, tooltip: List<Component>) : IRecipeWidget {
	private val preview = MobPreview(mob, bounds, tooltip)
	override fun getPosition(): ScreenPosition = ScreenPosition(0, 0)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) =
		preview.draw(guiGraphics, mouseX.toInt(), mouseY.toInt())

	override fun getTooltip(tooltip: ITooltipBuilder, mouseX: Double, mouseY: Double) =
		tooltip.addAll(preview.tooltipAt(mouseX.toInt(), mouseY.toInt()))
}

class WrappedTextWidget(val x: Int, val y: Int, val width: Int, val lines: List<Component>) : IRecipeWidget {
	private val block = WrappedTextBlock(x, y, width, lines)
	override fun getPosition(): ScreenPosition = ScreenPosition(0, 0)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) = block.draw(guiGraphics)

	fun area(): Rectangle = block.bounds()
}

private class PageClick(val box: Rectangle, val onClick: () -> Unit) : IJeiInputHandler {
	override fun getArea(): ScreenRectangle = ScreenRectangle(box.x, box.y, box.width, box.height)
	override fun handleInput(mouseX: Double, mouseY: Double, input: IJeiUserInput): Boolean {
		val k = input.key
		if (k.type != InputConstants.Type.MOUSE || k.value != InputConstants.MOUSE_BUTTON_LEFT) return false
		if (!input.isSimulate) Faults.attempt("Item list page click failed") { onClick() }
		return true
	}
}

private class JeiPageCanvas(
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
		extras?.addInputHandler(PageClick(box, onClick))
	}

	override fun scrollLines(box: Rectangle, lines: List<PageLine>) = wrappedText(box.x, box.y, box.width, lines.map { it.text })

	override fun lookupItems(items: List<SBItemStack>) {
		if (items.isNotEmpty()) slots?.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)?.addIngredients(SBItemIngredientType, items)
	}
}

abstract class MobPageCategory(type: IRecipeType<MobRef>, private val page: ItemListPage<MobPageRef>, guiHelper: IGuiHelper) :
	LayouterCategory<MobRef>(type, page.title, guiHelper.createDrawableItemStack(page.icon), page.width, page.height) {
	override fun allRecipes(): List<MobRef> = page.allRecipes().map { MobRef(it.mobId) }

	private fun bounds() = Rectangle(0, 0, page.width, page.height)

	override fun layout(recipe: MobRef, bounds: Rectangle, layouter: RecipeLayouter) =
		page.draw(MobPageRef(recipe.mobId), bounds, JeiPageCanvas(layouter, null, null))

	override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: MobRef, focuses: IFocusGroup) =
		page.draw(MobPageRef(recipe.mobId), bounds(), JeiPageCanvas(JeiRecipeLayouter.forSlots(builder), builder, null))

	override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: MobRef, focuses: IFocusGroup) =
		page.draw(MobPageRef(recipe.mobId), bounds(), JeiPageCanvas(JeiRecipeLayouter.forExtras(builder), null, builder))
}

class MobsJeiCategory(guiHelper: IGuiHelper) : MobPageCategory(TYPE, MobsPage, guiHelper) {
	companion object {
		val TYPE: IRecipeType<MobRef> = IRecipeType.create(Apex.MOD_ID, "skyblock_mobs", MobRef::class.java)
	}
}

class MobDropRefJeiCategory(guiHelper: IGuiHelper) : MobPageCategory(TYPE, MobDropsPage, guiHelper) {
	companion object {
		val TYPE: IRecipeType<MobRef> = IRecipeType.create(Apex.MOD_ID, "mob_drop_recipe", MobRef::class.java)
	}
}
