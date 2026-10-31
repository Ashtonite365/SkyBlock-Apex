// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.emi

import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.render.EmiRenderable
import dev.emi.emi.api.render.EmiTexture
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.Bounds
import dev.emi.emi.api.widget.SlotWidget
import dev.emi.emi.api.widget.Widget
import dev.emi.emi.api.widget.WidgetHolder
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.features.items.recipes.itemlist.FixedCell
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPreview
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageCanvas
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageLine
import dev.ashtonite365.apex.features.items.recipes.itemlist.ScrollLinesBlock
import dev.ashtonite365.apex.features.items.recipes.itemlist.WrappedTextBlock
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults

class EmiPageCategory(val page: ItemListPage<*>) :
	EmiRecipeCategory(page.id, EmiRenderable { g, x, y, _ -> g.item(page.icon, x, y) }) {
	override fun getName(): Component = page.title
}

class EmiPageRecipe<T : Any>(
	val page: ItemListPage<T>, val recipe: T, private val category: EmiPageCategory, private val index: Int,
) : EmiRecipe {
	private val ins: List<EmiIngredient> by lazy { page.inputs(recipe).map(EmiEntries::ingredient) }
	private val outs: List<EmiStack> by lazy { page.outputs(recipe).flatten().map(EmiEntries::stack) }

	override fun getCategory(): EmiRecipeCategory = category
	override fun getId(): Identifier = Identifier.fromNamespaceAndPath(page.id.namespace, "/${page.id.path}/$index")
	override fun getInputs(): List<EmiIngredient> = ins
	override fun getOutputs(): List<EmiStack> = outs
	override fun getDisplayWidth(): Int = page.width
	override fun getDisplayHeight(): Int = page.height
	override fun supportsRecipeTree(): Boolean = false

	override fun addWidgets(widgets: WidgetHolder) {
		val canvas = EmiCanvas(widgets, this)
		Faults.attempt("Item list page ${page.id} failed to lay out") {
			page.draw(recipe, Rectangle(0, 0, page.width, page.height), canvas)
		}
		page.craftAction(recipe)?.let { action -> widgets.add(PlusButton(page.width - PlusButton.SIZE, page.height - PlusButton.SIZE, action)) }
	}
}

class EmiCanvas(private val widgets: WidgetHolder, private val owner: EmiRecipe) : PageCanvas {
	override fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.RotatingCell {
		val ingredient = EmiEntries.ingredient(content)

		val slot: SlotWidget = if (slotRole.isBig) widgets.addSlot(ingredient, x - 5, y - 5).large(true)
		else widgets.addSlot(ingredient, x - 1, y - 1)
		when (slotRole) {
			RecipeLayouter.SlotRole.SMALL_OUTPUT, RecipeLayouter.SlotRole.BIG_OUTPUT -> slot.recipeContext(owner)
			RecipeLayouter.SlotRole.DISPLAY -> slot.drawBack(false)
			RecipeLayouter.SlotRole.SMALL_INPUT -> {}
		}
		return FixedCell(content)
	}

	override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
		val label = LabelWidget(x, y, text)
		widgets.add(label)
		return RecipeLayouter.Settable { label.text = it }
	}

	override fun hoverArea(rectangle: Rectangle, label: List<Component>) {
		widgets.addTooltipText(label, rectangle.x, rectangle.y, rectangle.width, rectangle.height)
	}

	override fun arrowAt(left: Int, top: Int): Rectangle {
		widgets.addTexture(EmiTexture.EMPTY_ARROW, left, top)
		return Rectangle(left, top, 24, 17)
	}

	override fun flameAt(point: Point, animationTicks: Int) {
		widgets.addTexture(EmiTexture.EMPTY_FLAME, point.x, point.y)
		widgets.addAnimatedTexture(EmiTexture.FULL_FLAME, point.x, point.y, animationTicks * 50, false, true, true)
	}

	override fun entityIn(rectangle: Rectangle, entity: LivingEntity) {
		widgets.add(DrawWidget(rectangle) { g, mx, my ->
			EntityRenderer.renderEntity(entity, g, rectangle.x, rectangle.y, rectangle.width.toDouble(), rectangle.height.toDouble(), mx.toDouble(), my.toDouble())
		})
	}

	override fun embedAt(box: Rectangle, component: GuiComponent) {
		widgets.add(DrawWidget(box) { g, mx, my ->
			g.pose().pushMatrix()
			g.pose().translate(box.x.toFloat(), box.y.toFloat())
			try {
				component.render(GuiImmediateContext(MoulConfigRenderContext(g), 0, 0, box.width, box.height,
					mx - box.x, my - box.y, mx, my, mx.toFloat(), my.toFloat()))
			} finally { g.pose().popMatrix() }
		})
	}

	override fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>) {
		val block = WrappedTextBlock(x, y, width, lines)
		widgets.add(DrawWidget(block.bounds()) { g, _, _ -> block.draw(g) })
	}

	override fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>) {
		val preview = MobPreview(mob, box, tooltip)
		widgets.add(object : DrawWidget(box, { g, mx, my -> preview.draw(g, mx, my) }) {
			override fun getTooltip(mouseX: Int, mouseY: Int): List<ClientTooltipComponent> = tooltipOf(preview.tooltipAt(mouseX, mouseY))
		})
	}

	override fun clickArea(box: Rectangle, onClick: () -> Unit) {
		widgets.add(object : DrawWidget(box, { _, _, _ -> }) {
			override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): Boolean {
				if (button != 0) return false
				Faults.attempt("Item list page click failed") { onClick() }
				return true
			}
		})
	}

	override fun scrollLines(box: Rectangle, lines: List<PageLine>) {
		val block = ScrollLinesBlock(box, lines)
		widgets.add(object : DrawWidget(box, { g, _, _ -> block.draw(g) }) {
			override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): Boolean {
				if (button != 0) return false
				if (mouseX >= box.maxX - SCROLLBAR_HIT) {
					val up = mouseY < box.centerY
					return block.scroll(mouseX.toDouble(), mouseY.toDouble(), if (up) PAGE_STEP else -PAGE_STEP)
				}
				return block.click(mouseX.toDouble(), mouseY.toDouble())
			}
		})
	}

	companion object {
		const val SCROLLBAR_HIT = 6
		const val PAGE_STEP = 5.0

		fun tooltipOf(lines: List<Component>): List<ClientTooltipComponent> =
			lines.map { ClientTooltipComponent.create(it.visualOrderText) }
	}
}

open class DrawWidget(private val box: Rectangle, private val draw: (GuiGraphicsExtractor, Int, Int) -> Unit) : Widget() {
	override fun getBounds(): Bounds = Bounds(box.x, box.y, box.width, box.height)
	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		draw(graphics, mouseX, mouseY)
	}
}

class LabelWidget(private val x: Int, private val y: Int, var text: Component) : Widget() {
	override fun getBounds(): Bounds {
		val w = runCatching { Client.font.width(text) }.getOrDefault(0)
		return Bounds(x - w / 2, y, w, 9)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		graphics.centeredText(Client.font, text, x, y, -1)
	}
}

class PlusButton(private val x: Int, private val y: Int, private val action: () -> Unit) : Widget() {
	override fun getBounds(): Bounds = Bounds(x, y, SIZE, SIZE)

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		val hovered = bounds.contains(mouseX, mouseY)
		graphics.fill(x, y, x + SIZE, y + SIZE, if (hovered) 0xFF5A5A5A.toInt() else 0xFF3A3A3A.toInt())
		graphics.centeredText(Client.font, Component.literal("+"), x + SIZE / 2, y + 1, -1)
	}

	override fun getTooltip(mouseX: Int, mouseY: Int): List<ClientTooltipComponent> =
		EmiCanvas.tooltipOf(listOf(Component.literal("Craft this recipe")))

	override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int): Boolean {
		if (button != 0) return false
		Faults.attempt("Craft hand-off failed") { action() }
		return true
	}

	companion object {
		const val SIZE = 10
	}
}
