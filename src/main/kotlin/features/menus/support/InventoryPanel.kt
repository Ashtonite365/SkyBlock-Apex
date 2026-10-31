// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.world.inventory.ContainerInput

object InventoryPanel {

	const val WIDTH = 9 * 18

	const val HEIGHT = 10 + 4 * 18 + 4

	const val RESERVED = HEIGHT + 4

	fun chestSlots(windowSlots: Int) = (windowSlots - 36).coerceAtLeast(0)

	fun slotIndex(row: Int, col: Int, chestSlots: Int): Int {
		require(row in 0..3 && col in 0..8)
		return chestSlots + row * 9 + col
	}

	fun scaledCell(row: Int, col: Int, ix: Int, iy: Int, k: Float): Triple<Int, Int, Int> {
		val size = Math.round(18 * k)
		return Triple(ix + col * size, iy + 10 + row * size + (if (row == 3) Math.round(4 * k) else 0), size)
	}

	fun scaledHeight(k: Float) = 10 + 4 * Math.round(18 * k) + Math.round(4 * k)

	fun offset(row: Int, col: Int) = col * 18 to row * 18 + (if (row == 3) 4 else 0)

	fun route(packetButton: Int, shift: Boolean): Pair<Int, ContainerInput> =
		(if (packetButton == 1) 1 else 0) to (if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)

	fun isPanelSlot(index: Int, windowSlots: Int): Boolean {
		val chest = chestSlots(windowSlots)
		return windowSlots >= 36 && index >= chest && index < chest + 36
	}

	fun itemPos(row: Int, col: Int, ix: Int, iy: Int): Pair<Int, Int> {
		val (ox, oy) = offset(row, col)
		return ix + ox + 1 to iy + 10 + oy + 1
	}

	fun slotPlacement(index: Int, windowSlots: Int, origin: Pair<Int, Int>?, leftPos: Int, topPos: Int,
	                  cells: Map<Int, Pair<Int, Int>> = emptyMap()): Pair<Int, Int>? {
		cells[index]?.let { (ax, ay) -> return ax - leftPos to ay - topPos }
		if (origin == null || !isPanelSlot(index, windowSlots)) return null
		val rel = index - chestSlots(windowSlots)
		val (ax, ay) = itemPos(rel / 9, rel % 9, origin.first, origin.second)
		return ax - leftPos to ay - topPos
	}

	fun hitsCell(sx: Int, sy: Int, mx: Double, my: Double, scale: Float = 1f): Boolean {
		val s = scale.toDouble()
		return mx >= sx - s && mx < sx + 17 * s && my >= sy - s && my < sy + 17 * s
	}

	fun hitsSlot(index: Int, windowSlots: Int, origin: Pair<Int, Int>?, sx: Int, sy: Int, mx: Double, my: Double,
	             cells: Map<Int, Pair<Int, Int>> = emptyMap(), scale: Float = 1f): Boolean =
		(index in cells || origin != null && isPanelSlot(index, windowSlots)) && hitsCell(sx, sy, mx, my, scale)

	fun cellAt(origin: Pair<Int, Int>?, windowSlots: Int, mx: Double, my: Double, cells: Map<Int, Pair<Int, Int>> = emptyMap(), scale: Float = 1f,
	           scaleOf: ((Int) -> Float)? = null): Int? {
		for ((slot, pos) in cells) if (slot < windowSlots && hitsCell(pos.first, pos.second, mx, my, scaleOf?.invoke(slot) ?: scale)) return slot
		if (origin == null || windowSlots < 36) return null
		val chest = chestSlots(windowSlots)
		for (row in 0..3) for (col in 0..8) {
			val (sx, sy) = itemPos(row, col, origin.first, origin.second)
			if (hitsCell(sx, sy, mx, my, scale)) return slotIndex(row, col, chest)
		}
		return null
	}

	fun cellAtPositions(itemPositions: List<Pair<Int, Int>>, mx: Double, my: Double): Int? =
		itemPositions.indexOfFirst { (sx, sy) -> hitsCell(sx, sy, mx, my) }.takeIf { it >= 0 }

	fun cellItemPos(fx: Int, fy: Int) = fx + 1 to fy + 1

	class Gesture {

		var vanilla = false; private set

		fun press(overPanelCell: Boolean): Boolean { vanilla = overPanelCell; return vanilla }
		fun drag(): Boolean = vanilla

		fun release(): Boolean { val v = vanilla; vanilla = false; return v }

		fun scroll(overPanelCell: Boolean): Boolean = overPanelCell
	}
}
