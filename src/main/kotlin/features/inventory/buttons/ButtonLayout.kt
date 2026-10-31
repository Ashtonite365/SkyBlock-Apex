package dev.ashtonite365.apex.features.inventory.buttons

import me.shedaniel.math.Dimension
import me.shedaniel.math.Rectangle

object ButtonLayout {
	const val CELL = 18
	const val GAP = 2
	private const val SNAP = 20

	private val single = Dimension(CELL, CELL)
	private val double = Dimension(2 * CELL + GAP, 2 * CELL + GAP)

	fun sizeOf(big: Boolean): Dimension = if (big) double else single

	fun area(button: InventoryButton, frame: Rectangle): Rectangle {
		val edgeX = if (button.anchorRight) frame.maxX else frame.minX
		val edgeY = if (button.anchorBottom) frame.maxY else frame.minY
		val size = button.size
		return Rectangle(edgeX + button.x, edgeY + button.y, size.width, size.height)
	}

	data class Spot(val anchorRight: Boolean, val anchorBottom: Boolean, val x: Int, val y: Int)

	fun spotAt(mouseX: Int, mouseY: Int, frame: Rectangle, snap: Boolean): Spot? {
		val right = mouseX > frame.maxX
		val bottom = mouseY > frame.maxY
		fun offset(mouse: Int, edge: Int): Int = (mouse - edge).let { if (snap) Math.floorDiv(it, SNAP) * SNAP else it }
		val spot = Spot(right, bottom, offset(mouseX, if (right) frame.maxX else frame.minX), offset(mouseY, if (bottom) frame.maxY else frame.minY))
		return spot.takeUnless { area(InventoryButton(spot.x, spot.y, right, bottom), frame).intersects(frame) }
	}

	fun dragTo(button: InventoryButton, spot: Spot, frame: Rectangle) {
		button.x = spot.x
		button.y = spot.y
		button.anchorRight = spot.anchorRight
		button.anchorBottom = spot.anchorBottom
		if (spot.anchorRight) return
		val size = button.size
		if (spot.x > -size.width && area(button, frame).intersects(frame)) button.x = -size.width
		if (spot.y > -size.height && area(button, frame).intersects(frame)) button.y = -size.height
	}

	fun withoutSlashes(buttons: List<InventoryButton>): List<InventoryButton> =
		buttons.map { it.copy(command = it.command?.removePrefix("/")) }

	fun fitImported(buttons: List<InventoryButton>): Pair<MutableList<InventoryButton>, Int> {
		val (onInventory, fine) = buttons.partition { !it.anchorRight && !it.anchorBottom && it.x > 0 && it.y > 0 }
		val layout = fine.toMutableList()
		val origin = Rectangle(0, 0, 1, 1)
		val cells = generateSequence(0) { it + 1 }.iterator()
		for (button in onInventory) {
			while (true) {
				val cell = cells.next()
				val candidate = button.copy(x = cell % 10 * CELL, y = -(cell / 10) * CELL, anchorRight = false, anchorBottom = false)
				val area = area(candidate, origin)
				if (layout.any { area(it, origin).intersects(area) }) continue
				layout += candidate
				break
			}
		}
		return layout to onInventory.size
	}
}
