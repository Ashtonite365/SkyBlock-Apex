// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes.itemlist

import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter

interface ItemListPage<T : Any> {
	val id: Identifier
	val title: Component
	val icon: ItemStack
	val width: Int
	val height: Int

	fun allRecipes(): List<T>

	fun draw(recipe: T, bounds: Rectangle, canvas: PageCanvas)

	fun inputs(recipe: T): List<List<SBItemStack>> = RecordingCanvas.of(this, recipe).inputs

	fun outputs(recipe: T): List<List<SBItemStack>> = RecordingCanvas.of(this, recipe).outputs

	fun craftAction(recipe: T): (() -> Unit)? = null
}

data class PageLine(val text: Component, val action: (() -> Unit)? = null)

interface PageCanvas : RecipeLayouter {

	fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>)

	fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>)

	fun clickArea(box: Rectangle, onClick: () -> Unit)

	fun scrollLines(box: Rectangle, lines: List<PageLine>)

	fun lookupItems(items: List<SBItemStack>) {}
}

class RecordingCanvas : PageCanvas {
	data class Slot(val x: Int, val y: Int, val content: List<SBItemStack>, val role: RecipeLayouter.SlotRole)

	val slots = mutableListOf<Slot>()
	val labels = mutableListOf<Pair<Point, Component>>()
	val texts = mutableListOf<List<Component>>()
	val clicks = mutableListOf<Pair<Rectangle, () -> Unit>>()
	val scrolls = mutableListOf<Pair<Rectangle, List<PageLine>>>()
	val mobs = mutableListOf<MobEntry>()
	val lookups = mutableListOf<SBItemStack>()

	val inputs: List<List<SBItemStack>>
		get() = slots.filter { it.role == RecipeLayouter.SlotRole.SMALL_INPUT && it.content.isNotEmpty() }.map { it.content } +
			lookups.map { listOf(it) }
	val outputs: List<List<SBItemStack>>
		get() = slots.filter { (it.role == RecipeLayouter.SlotRole.SMALL_OUTPUT || it.role == RecipeLayouter.SlotRole.BIG_OUTPUT) && it.content.isNotEmpty() }
			.map { it.content } + lookups.map { listOf(it) }

	override fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.RotatingCell {
		slots += Slot(x, y, content, slotRole)
		return FixedCell(content)
	}

	override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
		labels += Point(x, y) to text
		return RecipeLayouter.Settable { }
	}

	override fun hoverArea(rectangle: Rectangle, label: List<Component>) {}
	override fun arrowAt(left: Int, top: Int): Rectangle = Rectangle(left, top, 24, 17)
	override fun flameAt(point: Point, animationTicks: Int) {}
	override fun entityIn(rectangle: Rectangle, entity: LivingEntity) {}
	override fun embedAt(box: Rectangle, component: GuiComponent) {}
	override fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>) { texts += lines }
	override fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>) { mobs += mob }
	override fun clickArea(box: Rectangle, onClick: () -> Unit) { clicks += box to onClick }
	override fun scrollLines(box: Rectangle, lines: List<PageLine>) { scrolls += box to lines }
	override fun lookupItems(items: List<SBItemStack>) { lookups += items }

	companion object {

		fun <T : Any> of(page: ItemListPage<T>, recipe: T): RecordingCanvas = RecordingCanvas().also {
			runCatching { page.draw(recipe, Rectangle(0, 0, page.width, page.height), it) }
		}
	}
}

class FixedCell(private val content: List<SBItemStack>) : RecipeLayouter.RotatingCell {
	override fun showing(): SBItemStack = content.firstOrNull() ?: SBItemStack.EMPTY
	override fun setTo(value: SBItemStack) = Unit
	override fun onRotate(listener: () -> Unit) = Unit
}
