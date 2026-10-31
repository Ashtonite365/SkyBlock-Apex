// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import dev.ashtonite365.apex.features.menus.MenuBounds

data class ChestFrame(val x: Int, val y: Int, val rows: Int) {
	val w get() = WIDTH
	val h get() = heightFor(rows)
	val bounds get() = MenuBounds(x, y, w, h)

	val chestBottom get() = y + TOP + rows * SLOT

	val inventoryLabelY get() = y + h - 94

	val inventoryTop get() = y + h - 82
	val hotbarTop get() = y + h - 24

	fun slot(index: Int): Pair<Int, Int> = x + LEFT + (index % COLS) * SLOT to y + TOP + (index / COLS) * SLOT

	fun slotAt(mx: Int, my: Int): Int? {
		val cx = mx - (x + LEFT - 1); val cy = my - (y + TOP - 1)
		if (cx < 0 || cy < 0) return null
		val c = cx / SLOT; val r = cy / SLOT
		return if (c < COLS && r < rows) r * COLS + c else null
	}

	companion object {
		const val WIDTH = 176
		const val COLS = 9
		const val SLOT = 18
		const val LEFT = 8
		const val TOP = 18
		fun heightFor(rows: Int) = 114 + rows * SLOT

		fun rowsFor(totalSlots: Int) = ((totalSlots - 36) / COLS).coerceIn(1, 6)

		fun of(screenW: Int, screenH: Int, rows: Int = 6) =
			ChestFrame((screenW - WIDTH) / 2, (screenH - heightFor(rows)) / 2, rows)
	}
}
