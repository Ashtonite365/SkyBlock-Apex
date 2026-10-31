// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.accessories

object AccessorySidebarLayout {
	data class Box(val x: Int, val y: Int, val w: Int, val h: Int) {
		val right get() = x + w
		val bottom get() = y + h
		fun overlaps(o: Box) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
		fun contains(mx: Double, my: Double) = mx >= x && mx < right && my >= y && my < bottom
	}

	const val GAP = 4
	const val MARGIN = 2
	const val MIN_W = 60

	fun dock(anchor: Box, screenW: Int, screenH: Int, maxW: Int, contentH: Int): Box? {
		val leftRoom = anchor.x - GAP - MARGIN
		val rightRoom = screenW - MARGIN - (anchor.right + GAP)
		val left = leftRoom >= MIN_W && (leftRoom >= maxW || leftRoom >= rightRoom)
		val room = if (left) leftRoom else rightRoom
		if (room < MIN_W) return null
		val w = minOf(maxW, room)
		val x = if (left) anchor.x - GAP - w else anchor.right + GAP
		val h = minOf(contentH, screenH - 2 * MARGIN).coerceAtLeast(1)
		val y = anchor.y.coerceAtMost(screenH - MARGIN - h).coerceAtLeast(MARGIN)
		return Box(x, y, w, h)
	}

	fun clampScroll(scroll: Int, contentH: Int, viewH: Int) = scroll.coerceIn(0, (contentH - viewH).coerceAtLeast(0))
}
