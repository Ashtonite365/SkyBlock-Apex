// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

object TooltipFit {
	const val MARGIN = 4

	const val FRAME = 4

	fun scale(w: Int, h: Int, screenW: Int, screenH: Int): Float {
		val sh = (screenH - 2 * (MARGIN + FRAME)).toFloat() / h.coerceAtLeast(1)
		val sw = (screenW - 2 * (MARGIN + FRAME)).toFloat() / w.coerceAtLeast(1)
		return minOf(1f, sh, sw).coerceAtLeast(0.1f)
	}

	@Volatile private var avoid: IntArray? = null
	@Volatile private var avoidAt = 0L
	private const val AVOID_MS = 150L

	fun avoidFor(rect: IntArray) { avoid = rect; avoidAt = System.currentTimeMillis() }
	fun activeAvoid(now: Long = System.currentTimeMillis()): IntArray? = avoid?.takeIf { now - avoidAt <= AVOID_MS }

	@JvmOverloads
	fun position(mx: Int, my: Int, w: Int, h: Int, screenW: Int, screenH: Int, keepClear: IntArray? = activeAvoid()): Pair<Int, Int> {
		var x = mx + 12
		var y = my - 12
		if (x + w + FRAME + MARGIN > screenW) x = mx - 24 - w
		if (keepClear != null && keepClear[0] <= mx && mx < keepClear[2] && keepClear[1] <= my && my < keepClear[3]) {

			x = if (keepClear[2] + 8 + w + FRAME + MARGIN <= screenW) keepClear[2] + 8 else keepClear[0] - 8 - w - FRAME
		}
		val minX = MARGIN + FRAME; val minY = MARGIN + FRAME
		x = x.coerceIn(minX, maxOf(minX, screenW - w - FRAME - MARGIN))
		y = y.coerceIn(minY, maxOf(minY, screenH - h - FRAME - MARGIN))
		return x to y
	}
}
