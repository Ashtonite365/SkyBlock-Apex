// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.rei

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import java.util.Optional
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics
import me.shedaniel.rei.api.client.gui.widgets.Tooltip
import me.shedaniel.rei.api.client.gui.widgets.Widget
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds
import me.shedaniel.rei.api.client.gui.widgets.Widgets
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.common.category.CategoryIdentifier
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.display.DisplaySerializer
import me.shedaniel.rei.api.common.entry.EntryIngredient
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
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
import dev.ashtonite365.apex.features.items.recipes.itemlist.FixedCell
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries

object ReiIds {
	var factory: (Identifier) -> CategoryIdentifier<*> = { CategoryIdentifier.of<Display>(it) }

	@Suppress("UNCHECKED_CAST")
	fun <D : Display> of(id: Identifier): CategoryIdentifier<D> = factory(id) as CategoryIdentifier<D>
}

class ReiPageDisplay<T : Any>(
	val page: ItemListPage<T>, val recipe: T, private val category: CategoryIdentifier<ReiPageDisplay<T>>,
) : Display {
	private val ins: List<EntryIngredient> by lazy { page.inputs(recipe).map(ReiEntries::ingredient) }
	private val outs: List<EntryIngredient> by lazy { page.outputs(recipe).map(ReiEntries::ingredient) }

	val inputIds: Set<String> by lazy { page.inputs(recipe).flatten().map(SkyblockEntries::identity).toSet() }
	val outputIds: Set<String> by lazy { page.outputs(recipe).flatten().map(SkyblockEntries::identity).toSet() }

	fun craftAction(): (() -> Unit)? = page.craftAction(recipe)

	override fun getInputEntries(): List<EntryIngredient> = ins
	override fun getOutputEntries(): List<EntryIngredient> = outs
	override fun getCategoryIdentifier(): CategoryIdentifier<*> = category
	override fun getDisplayLocation(): Optional<Identifier> = Optional.empty()
	override fun getSerializer(): DisplaySerializer<out Display>? = null
}

class ReiPageCategory<T : Any>(val page: ItemListPage<T>, val focus: Boolean = false) : DisplayCategory<ReiPageDisplay<T>> {
	val id: Identifier = if (focus) Identifier.fromNamespaceAndPath(page.id.namespace, page.id.path + "_focus") else page.id
	val categoryId: CategoryIdentifier<ReiPageDisplay<T>> = ReiIds.of(id)

	fun display(recipe: T): ReiPageDisplay<T> = ReiPageDisplay(page, recipe, categoryId)

	override fun getCategoryIdentifier(): CategoryIdentifier<out ReiPageDisplay<T>> = categoryId
	override fun getTitle(): Component = page.title
	override fun getIcon(): Renderer = object : Renderer {
		override fun render(graphics: GuiGraphics, bounds: Rectangle, mouseX: Int, mouseY: Int, delta: Float) {
			graphics.item(page.icon, bounds.centerX - 8, bounds.centerY - 8)
		}
	}
	override fun getDisplayWidth(display: ReiPageDisplay<T>): Int = page.width + PAD * 2
	override fun getDisplayHeight(): Int = page.height + PAD * 2

	override fun setupDisplay(display: ReiPageDisplay<T>, bounds: Rectangle): List<Widget> {
		val canvas = ReiCanvas()
		canvas.widgets += Widgets.createRecipeBase(bounds)
		Faults.attempt("Item list page ${page.id} failed to lay out") {
			page.draw(display.recipe, Rectangle(bounds.x + PAD, bounds.y + PAD, page.width, page.height), canvas)
		}
		return canvas.widgets
	}

	companion object {
		const val PAD = 4
	}
}

class ReiCanvas : PageCanvas {
	val widgets = mutableListOf<Widget>()

	override fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.RotatingCell {
		if (slotRole == RecipeLayouter.SlotRole.BIG_OUTPUT) widgets += Widgets.createResultSlotBackground(Point(x, y))
		val slot = Widgets.createSlot(Point(x, y)).entries(content.map(ReiEntries::stack))
		when (slotRole) {
			RecipeLayouter.SlotRole.SMALL_INPUT -> slot.markInput()
			RecipeLayouter.SlotRole.SMALL_OUTPUT -> slot.markOutput()
			RecipeLayouter.SlotRole.BIG_OUTPUT -> slot.markOutput().disableBackground()
			RecipeLayouter.SlotRole.DISPLAY -> slot.disableBackground()
		}
		widgets += slot
		return FixedCell(content)
	}

	override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
		val label = Widgets.createLabel(Point(x, y), text).centered().color(-1).shadow()
		widgets += label
		return RecipeLayouter.Settable { label.setMessage(it) }
	}

	override fun hoverArea(rectangle: Rectangle, label: List<Component>) {
		widgets += Widgets.createTooltip(rectangle, label)
	}

	override fun arrowAt(left: Int, top: Int): Rectangle {
		widgets += Widgets.createArrow(Point(left, top))
		return Rectangle(left, top, 24, 17)
	}

	override fun flameAt(point: Point, animationTicks: Int) {
		widgets += Widgets.createBurningFire(point).animationDurationTicks(animationTicks.toDouble())
	}

	override fun entityIn(rectangle: Rectangle, entity: LivingEntity) {
		widgets += DrawWidget(rectangle) { g, mx, my ->
			EntityRenderer.renderEntity(entity, g, rectangle.x, rectangle.y, rectangle.width.toDouble(), rectangle.height.toDouble(), mx.toDouble(), my.toDouble())
		}
	}

	override fun embedAt(box: Rectangle, component: GuiComponent) {
		widgets += DrawWidget(box) { g, mx, my ->
			g.pose().pushMatrix()
			g.pose().translate(box.x.toFloat(), box.y.toFloat())
			try {
				val rx = mx - box.x
				val ry = my - box.y
				component.render(GuiImmediateContext(MoulConfigRenderContext(g), 0, 0, box.width, box.height,
					rx, ry, mx, my, mx.toFloat(), my.toFloat()))
			} finally { g.pose().popMatrix() }
		}
	}

	override fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>) {
		val block = WrappedTextBlock(x, y, width, lines)
		widgets += DrawWidget(block.bounds()) { g, _, _ -> block.draw(g) }
	}

	override fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>) {
		val preview = MobPreview(mob, box, tooltip)
		widgets += DrawWidget(box) { g, mx, my ->
			preview.draw(g, mx, my)
			preview.tooltipAt(mx, my).takeIf { it.isNotEmpty() }?.let { Tooltip.create(Point(mx, my), it).queue() }
		}
	}

	override fun clickArea(box: Rectangle, onClick: () -> Unit) {
		widgets += object : DrawWidget(box, { _, _, _ -> }) {
			override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
				if (event.button() != 0 || !box.contains(event.x(), event.y())) return false
				Faults.attempt("Item list page click failed") { onClick() }
				return true
			}
		}
	}

	override fun scrollLines(box: Rectangle, lines: List<PageLine>) {
		val block = ScrollLinesBlock(box, lines)
		widgets += object : DrawWidget(box, { g, _, _ -> block.draw(g) }) {
			override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
				block.scroll(mouseX, mouseY, scrollY)

			override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
				event.button() == 0 && block.click(event.x(), event.y())
		}
	}
}

open class DrawWidget(private val box: Rectangle, private val draw: (GuiGraphicsExtractor, Int, Int) -> Unit) : WidgetWithBounds() {
	override fun getBounds(): Rectangle = box
	override fun children(): List<GuiEventListener> = listOf()
	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		draw(graphics, mouseX, mouseY)
	}
}
