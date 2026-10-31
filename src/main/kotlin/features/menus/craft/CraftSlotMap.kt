// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.craft

object CraftSlotMap {
	const val TITLE = "Craft Item"
	const val CHEST_SLOTS = 54

	const val VERIFIED = true

	val GRID: List<Int> = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)

	const val OUTPUT = 23
	const val CLOSE = 49

	val QUICK_CRAFT: List<Int> = listOf(16, 25, 34)

	fun isRealGridCell(slot: Int) = slot in GRID

	fun inventorySlot(row: Int, col: Int) = CHEST_SLOTS + row * 9 + col
}
