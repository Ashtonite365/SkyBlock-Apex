// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes

class ScrollPage(val rows: List<Int>, val viewHeight: Int, val rowHeight: Int = 10) {

	val tops: List<Int> = rows.runningFold(0) { acc, r -> acc + r.coerceAtLeast(1) * rowHeight }.dropLast(1)
	val contentHeight: Int = rows.sumOf { it.coerceAtLeast(1) } * rowHeight
	val maxScroll: Int = (contentHeight - viewHeight).coerceAtLeast(0)
	val overflows: Boolean get() = maxScroll > 0

	fun clamp(offset: Int): Int = offset.coerceIn(0, maxScroll)

	fun scrolled(offset: Int, wheel: Double, step: Int = rowHeight * 2): Int = clamp(offset - (wheel * step).toInt())

	fun lineAt(y: Double, offset: Int): Int? {
		if (y < 0 || y >= viewHeight) return null
		val cy = y + clamp(offset)
		val i = tops.indexOfLast { it <= cy }
		if (i < 0 || cy >= tops[i] + rows[i].coerceAtLeast(1) * rowHeight) return null
		return i
	}

	fun hitBox(i: Int, offset: Int): IntRange? {
		val top = tops[i] - clamp(offset)
		val bottom = top + rows[i].coerceAtLeast(1) * rowHeight
		val a = top.coerceAtLeast(0); val b = bottom.coerceAtMost(viewHeight)
		return if (a < b) a until b else null
	}

	fun thumb(offset: Int): Pair<Int, Int>? {
		if (!overflows) return null
		val h = (viewHeight.toLong() * viewHeight / contentHeight).toInt().coerceIn(8, viewHeight)
		val top = ((viewHeight - h).toLong() * clamp(offset) / maxScroll).toInt()
		return top to h
	}
}
