package dev.ashtonite365.apex.features.inventory.storageoverlay

import me.shedaniel.math.Rectangle

class StorageOverlayGeometry(
	screenWidth: Int, screenHeight: Int, wantedColumns: Int, heightSetting: Int, val separateControls: Boolean = false,
) {
	companion object {
		const val SLOT = 18
		const val GAP = 10
		const val PAGE_WIDTH = 9 * SLOT + 4
		const val PLAYER_WIDTH = 184
		const val PLAYER_HEIGHT = 91
		const val PLAYER_OVERLAP = 3
		const val SCROLL_BAR_WIDTH = 8
		const val KNOB_HEIGHT = 16
		const val CONTROLS_WIDTH = 70
		const val CONTROLS_HEIGHT = 50
		const val CONTROLS_BACKGROUND_WIDTH = CONTROLS_WIDTH + 4
		private const val CONTROLS_TUCK_X = 3
		private const val CONTROLS_RAISE = 5

		const val CONTROLS_SPACING = 2
		private const val PLAYER_SLOTS_LEFT = 12
		private const val PLAYER_HOTBAR_TOP = 67
		private const val PLAYER_MAIN_TOP = 9

		const val UNKNOWN_PAGE_HEIGHT = 18

		fun columnsFor(screenWidth: Int, wanted: Int): Int =
			maxOf(1, minOf(wanted, (screenWidth - GAP) / (PAGE_WIDTH + GAP)))

		const val ROW_GAP = 4

		fun boxRows(remembered: Int?, isOpen: Boolean, liveRows: Int): Int? =
			if (isOpen && liveRows > 0) liveRows else remembered

		fun intersectsPanel(box: Rectangle, panel: Rectangle): Boolean =
			box.y < panel.y + panel.height && box.y + box.height > panel.y

		fun pageHeight(rows: Int?, lineHeight: Int): Int = if (rows == null) UNKNOWN_PAGE_HEIGHT else rows * SLOT + 6 + lineHeight
	}

	val columns: Int = columnsFor(screenWidth, wantedColumns)
	val panelWidth: Int = columns * PAGE_WIDTH + (columns - 1) * GAP
	val frameWidth: Int = panelWidth + 3 * GAP + SCROLL_BAR_WIDTH
	val frameHeight: Int = minOf(screenHeight - PLAYER_HEIGHT - minOf(80, screenHeight / 10), heightSetting)
	val panelHeight: Int = frameHeight - 2 * GAP
	val frameX: Int = screenWidth / 2 - frameWidth / 2
	val frameY: Int = screenHeight / 2 - (frameHeight + PLAYER_HEIGHT) / 2
	val playerX: Int = screenWidth / 2 - PLAYER_WIDTH / 2
	val playerY: Int = frameY + frameHeight - PLAYER_OVERLAP
	val controlsX: Int =
		if (separateControls) playerX - CONTROLS_BACKGROUND_WIDTH - CONTROLS_SPACING else playerX - CONTROLS_WIDTH + CONTROLS_TUCK_X
	val controlsY: Int = if (separateControls) frameY + frameHeight + CONTROLS_SPACING else playerY - CONTROLS_RAISE

	fun controlsBackground() = Rectangle(controlsX, controlsY, CONTROLS_BACKGROUND_WIDTH, CONTROLS_HEIGHT)
	val totalHeight: Int = frameHeight - PLAYER_OVERLAP + PLAYER_HEIGHT

	fun frame() = Rectangle(frameX, frameY, frameWidth, frameHeight)
	fun panel() = Rectangle(frameX + GAP, frameY + GAP, panelWidth, panelHeight)
	fun scrollBar() = Rectangle(frameX + 2 * GAP + panelWidth, frameY + GAP, SCROLL_BAR_WIDTH, panelHeight)
	fun playerPanel() = Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT)
	fun controls() = Rectangle(controlsX, controlsY, CONTROLS_WIDTH, CONTROLS_HEIGHT)

	fun bounds(): List<Rectangle> = listOf(frame(), playerPanel(), if (separateControls) controlsBackground() else controls())

	fun playerSlot(index: Int): Pair<Int, Int> {
		val x = playerX + PLAYER_SLOTS_LEFT + (index % 9) * SLOT
		val y = if (index < 9) playerY + PLAYER_HOTBAR_TOP else playerY + PLAYER_MAIN_TOP + (index / 9 - 1) * SLOT
		return x to y
	}

	class PageLayout(val boxes: List<Rectangle>, val contentHeight: Int)

	fun layoutPages(heights: List<Int>, scroll: Float, rowGap: Int = 0): PageLayout {
		val top = frameY + GAP - scroll.toInt()
		val boxes = ArrayList<Rectangle>(heights.size)
		var rowTop = 0
		for (row in heights.chunked(columns)) {
			row.forEachIndexed { column, h ->
				boxes += Rectangle(frameX + GAP + column * (PAGE_WIDTH + GAP), top + rowTop, PAGE_WIDTH, h)
			}
			rowTop += row.max() + rowGap
		}
		return PageLayout(boxes, if (boxes.isEmpty()) 0 else rowTop - rowGap)
	}

	fun maxScroll(contentHeight: Int): Float = contentHeight.toFloat() - panelHeight

	fun clampScroll(scroll: Float, contentHeight: Int): Float = maxOf(0F, minOf(scroll, maxScroll(contentHeight)))

	fun knobTop(scroll: Float, contentHeight: Int): Int {
		val fraction = scroll / maxScroll(contentHeight)
		return frameY + GAP + (fraction * (panelHeight - KNOB_HEIGHT)).toInt()
	}

	fun scrollForKnobTop(knobTop: Double, contentHeight: Int): Float {
		val track = maxOf(1, panelHeight - KNOB_HEIGHT)
		val fraction = ((knobTop - (frameY + GAP)) / track).coerceIn(0.0, 1.0)
		val wanted = (maxOf(0F, maxScroll(contentHeight)) * fraction).toFloat()
		return clampScroll(wanted, contentHeight)
	}

	object Page {
		fun titleX(pageX: Int) = pageX + 6
		fun titleY(pageY: Int) = pageY + 3
		fun gridTop(pageY: Int, lineHeight: Int) = pageY + 5 + lineHeight
		fun slotX(pageX: Int, index: Int) = pageX + 3 + (index % 9) * SLOT
		fun slotY(pageY: Int, index: Int, lineHeight: Int) = gridTop(pageY, lineHeight) + 1 + (index / 9) * SLOT

		fun outline(pageX: Int, pageY: Int, rows: Int, lineHeight: Int) = Rectangle(pageX, pageY + 3 + lineHeight, PAGE_WIDTH, rows * SLOT + 4)
		fun grid(pageX: Int, pageY: Int, rows: Int, lineHeight: Int) = Rectangle(pageX + 2, gridTop(pageY, lineHeight), 9 * SLOT, rows * SLOT)
	}
}
