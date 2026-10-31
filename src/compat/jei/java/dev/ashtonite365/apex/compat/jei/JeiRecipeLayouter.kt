/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * APEX-26.3 PORT: JEI implementation of the viewer-agnostic RecipeLayouter.
 * JEI splits a recipe into setRecipe (slots) and createRecipeExtras (everything else), so the
 * renderer is run twice: once with SLOTS mode and once with EXTRAS mode.
 */
package dev.ashtonite365.apex.compat.jei

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.ITooltipBuilder
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.gui.widgets.IRecipeWidget
import mezz.jei.api.recipe.RecipeIngredientRole
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client

class JeiRecipeLayouter private constructor(
	private val slots: IRecipeLayoutBuilder?,
	private val extras: IRecipeExtrasBuilder?,
) : RecipeLayouter {
	companion object {
		fun forSlots(b: IRecipeLayoutBuilder) = JeiRecipeLayouter(b, null)
		fun forExtras(b: IRecipeExtrasBuilder) = JeiRecipeLayouter(null, b)
	}

	override fun cyclingSlotAt(
		x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole
	): RecipeLayouter.RotatingCell {
		if (slots != null) {
			val role = when (slotRole) {
				RecipeLayouter.SlotRole.SMALL_INPUT -> RecipeIngredientRole.INPUT
				RecipeLayouter.SlotRole.SMALL_OUTPUT, RecipeLayouter.SlotRole.BIG_OUTPUT -> RecipeIngredientRole.OUTPUT
				RecipeLayouter.SlotRole.DISPLAY -> RecipeIngredientRole.RENDER_ONLY
			}
			val slot = slots.addSlot(role, x, y)
			if (content.isNotEmpty()) slot.addIngredients(SBItemIngredientType, content)
			when (slotRole) {
				RecipeLayouter.SlotRole.BIG_OUTPUT -> slot.setOutputSlotBackground()
				RecipeLayouter.SlotRole.DISPLAY -> {}
				else -> slot.setStandardSlotBackground()
			}
		}
		return object : RecipeLayouter.RotatingCell {
			override fun showing(): SBItemStack = content.firstOrNull() ?: SBItemStack.EMPTY
			override fun setTo(newValue: SBItemStack) = Unit
			override fun onRotate(action: () -> Unit) = Unit
		}
	}

	override fun hoverArea(rectangle: Rectangle, label: List<Component>) {
		extras?.addTooltipArea(rectangle.x, rectangle.y, rectangle.width, rectangle.height)?.setTooltip(label)
	}

	override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
		val label = LabelWidget(x, y, text).also { extras?.addWidget(it) }
		return RecipeLayouter.Settable { replacement -> label.text = replacement }
	}

	override fun arrowAt(x: Int, y: Int): Rectangle {
		val w = extras?.addRecipeArrowWidget()?.setPosition(x, y)
		return Rectangle(x, y, w?.width ?: 24, w?.height ?: 17)
	}

	override fun embedAt(box: Rectangle, component: GuiComponent) {
		extras?.addWidget(MoulConfigWidget(box, component))
	}

	override fun flameAt(point: Point, animationTicks: Int) {
		extras?.addAnimatedRecipeFlameWidget(animationTicks)?.setPosition(point.x, point.y)
	}

	override fun entityIn(rectangle: Rectangle, entity: LivingEntity) {
		extras?.addWidget(EntityWidget(entity, rectangle))
	}
}

class LabelWidget(val x: Int, val y: Int, var text: Component, val centered: Boolean = true) : IRecipeWidget {
	override fun getPosition(): ScreenPosition = ScreenPosition(0, 0)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) {
		if (centered) guiGraphics.centeredText(Client.font, text, x, y, -1)
		else guiGraphics.text(Client.font, text, x, y, -1)
	}
}

class EntityWidget(
	val entity: LivingEntity?, val bounds: Rectangle, val tooltip: List<Component> = listOf(),

	val fallback: (() -> LivingEntity?)? = null, val onFailure: ((Throwable) -> Unit)? = null,

	val onOutcome: ((dev.ashtonite365.apex.gui.entity.PreviewOutcome, String?) -> Unit)? = null,
) : IRecipeWidget {
	var hasErrored = false
	var usingFallback = false
	override fun getPosition(): ScreenPosition = ScreenPosition(0, 0)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) {
		if (fallback != null) {
			val out = dev.ashtonite365.apex.gui.entity.PreviewDraw.drawWithFallback(entity, hasErrored, fallback, { e ->
				EntityRenderer.renderEntity(e, guiGraphics, bounds.x, bounds.y, bounds.width.toDouble(), bounds.height.toDouble(), mouseX, mouseY)
			}, EntityRenderer::drawsNothingReason) { ex -> hasErrored = true; onFailure?.invoke(ex) }
			if (entity == null) hasErrored = true
			usingFallback = out == dev.ashtonite365.apex.gui.entity.PreviewOutcome.FALLBACK
			onOutcome?.invoke(out, (if (out == dev.ashtonite365.apex.gui.entity.PreviewOutcome.PRIMARY) entity else null)
				?.let { net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() })
			if (out == dev.ashtonite365.apex.gui.entity.PreviewOutcome.FAILED)
				guiGraphics.fill(bounds.x, bounds.y, bounds.maxX, bounds.maxY, 0xFFAA2222.toInt())
			return
		}
		if (!hasErrored && entity != null) {
			try {
				EntityRenderer.renderEntity(
					entity, guiGraphics, bounds.x, bounds.y,
					bounds.width.toDouble(), bounds.height.toDouble(), mouseX, mouseY
				)
			} catch (ex: Exception) {
				hasErrored = true
				Faults.reportFault("Recipe entity preview could not be drawn: $entity", ex)
			}
		}
		if (hasErrored || entity == null)
			guiGraphics.fill(bounds.x, bounds.y, bounds.maxX, bounds.maxY, 0xFFAA2222.toInt())
	}

	override fun getTooltip(tooltip: ITooltipBuilder, mouseX: Double, mouseY: Double) {
		if (this.tooltip.isNotEmpty() && bounds.contains(mouseX, mouseY)) tooltip.addAll(this.tooltip)
	}
}

class MoulConfigWidget(val bounds: Rectangle, val component: GuiComponent) : IRecipeWidget {
	override fun getPosition(): ScreenPosition = ScreenPosition(bounds.x, bounds.y)
	override fun drawWidget(guiGraphics: GuiGraphicsExtractor, mouseX: Double, mouseY: Double) {

		component.render(
			GuiImmediateContext(
				MoulConfigRenderContext(guiGraphics),
				0, 0, bounds.width, bounds.height,
				mouseX.toInt(), mouseY.toInt(), mouseX.toInt(), mouseY.toInt(),
				mouseX.toFloat(), mouseY.toFloat()
			)
		)
	}
}
