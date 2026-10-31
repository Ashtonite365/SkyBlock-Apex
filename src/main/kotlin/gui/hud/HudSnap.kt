// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.hud

object HudSnap {
	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
		val right get() = x + w
		val bottom get() = y + h
	}

	data class Snapped(val x: Int, val y: Int, val guideX: Int?, val guideY: Int?)

	const val DEFAULT_THRESHOLD = 4

	private fun snapAxis(pos: Int, size: Int, lines: List<Int>, threshold: Int): Pair<Int, Int>? {
		var best: Pair<Int, Int>? = null
		var bestDist = threshold + 1
		for (line in lines) for (cand in intArrayOf(line, line - size / 2, line - size)) {
			val d = kotlin.math.abs(cand - pos)
			if (d < bestDist) { bestDist = d; best = cand to line }
		}
		return best
	}

	fun clamp(pos: Int, size: Int, screen: Int): Int = if (size >= screen) 0 else pos.coerceIn(0, screen - size)

	fun drag(start: Rect, dx: Int, dy: Int, screenW: Int, screenH: Int, others: List<Rect> = emptyList(),
	         enabled: Boolean = true, threshold: Int = DEFAULT_THRESHOLD): Snapped {
		var x = start.x + dx
		var y = start.y + dy
		var gx: Int? = null
		var gy: Int? = null
		if (enabled) {
			val xs = listOf(0, screenW / 2, screenW) + others.flatMap { listOf(it.x, it.right) }
			val ys = listOf(0, screenH / 2, screenH) + others.flatMap { listOf(it.y, it.bottom) }
			snapAxis(x, start.w, xs, threshold)?.let { x = it.first; gx = it.second }
			snapAxis(y, start.h, ys, threshold)?.let { y = it.first; gy = it.second }
		}
		val cx = clamp(x, start.w, screenW)
		val cy = clamp(y, start.h, screenH)
		return Snapped(cx, cy, gx.takeIf { cx == x }, gy.takeIf { cy == y })
	}

	fun nudge(r: Rect, dx: Int, dy: Int, shift: Boolean, screenW: Int, screenH: Int, big: Int = 10): Rect {
		val step = if (shift) big else 1
		return r.copy(x = clamp(r.x + dx * step, r.w, screenW), y = clamp(r.y + dy * step, r.h, screenH))
	}
}
