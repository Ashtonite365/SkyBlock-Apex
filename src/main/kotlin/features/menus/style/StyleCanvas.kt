// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

interface StyleCanvas {
	fun fill(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int)
	fun text(s: String, x: Int, y: Int, argb: Int, shadow: Boolean = false)
	fun textWidth(s: String): Int

	fun itemHint(x: Int, y: Int, size: Int, label: String) {}

	fun frost(x: Int, y: Int, w: Int, h: Int, r: Int, alpha: Double = 1.0) {}

	fun layer() {}
}

class RecordingCanvas(private val widthOf: (String) -> Int = { it.length * 6 }) : StyleCanvas {
	val ops = mutableListOf<String>()
	override fun fill(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) { if (x1 > x0 && y1 > y0) ops += "F $x0 $y0 $x1 $y1 ${Integer.toHexString(argb)}" }
	override fun text(s: String, x: Int, y: Int, argb: Int, shadow: Boolean) { ops += "T $x $y ${Integer.toHexString(argb)} $shadow $s" }
	override fun textWidth(s: String) = widthOf(s)
	override fun itemHint(x: Int, y: Int, size: Int, label: String) { ops += "I $x $y $size $label" }
	override fun frost(x: Int, y: Int, w: Int, h: Int, r: Int, alpha: Double) { ops += "B $x $y $w $h $r" }
	override fun layer() { ops += "L" }
}

object Shapes {

	fun cornerInset(radius: Int, row: Int): Int {
		if (radius <= 0 || row >= radius) return 0
		if (radius < INSET_MAX && row >= 0) return INSETS[radius][row]
		return insetOf(radius, row)
	}

	private const val INSET_MAX = 33

	private val INSETS: Array<IntArray> = Array(INSET_MAX) { r -> IntArray(r) { row -> insetOf(r, row) } }

	private fun insetOf(radius: Int, row: Int): Int {
		if (radius <= 0 || row >= radius) return 0
		val r = radius.toDouble(); val dy = r - row - 0.5
		return (r - kotlin.math.sqrt(maxOf(0.0, r * r - dy * dy))).let { kotlin.math.round(it).toInt() }.coerceIn(0, radius)
	}

	fun round(c: StyleCanvas, x: Int, y: Int, w: Int, h: Int, r: Int, argb: Int) {
		if (w <= 0 || h <= 0) return
		val rr = minOf(r, w / 2, h / 2)
		for (i in 0 until rr) {
			val k = cornerInset(rr, i)
			c.fill(x + k, y + i, x + w - k, y + i + 1, argb)
			c.fill(x + k, y + h - 1 - i, x + w - k, y + h - i, argb)
		}
		c.fill(x, y + rr, x + w, y + h - rr, argb)
	}

	fun roundFrame(c: StyleCanvas, x: Int, y: Int, w: Int, h: Int, r: Int, fill: Int, border: Int, glass: Boolean = false) =
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.PANELS) { roundFrameImpl(c, x, y, w, h, r, fill, border, glass) }

	private fun roundFrameImpl(c: StyleCanvas, x: Int, y: Int, w: Int, h: Int, r: Int, fill: Int, border: Int, glass: Boolean) {
		if (!glass || isOpaque(fill) || w <= 2 || h <= 2) round(c, x, y, w, h, r, border) else ring(c, x, y, w, h, r, border)
		round(c, x + 1, y + 1, w - 2, h - 2, maxOf(0, r - 1), fill)
	}

	fun isOpaque(argb: Int) = (argb ushr 24) == 0xFF

	fun ring(c: StyleCanvas, x: Int, y: Int, w: Int, h: Int, r: Int, argb: Int) {
		if (w <= 0 || h <= 0) return
		val ro = minOf(r, w / 2, h / 2)
		val iw = w - 2; val ih = h - 2; val ri = minOf(maxOf(0, r - 1), iw / 2, ih / 2)
		fun outer(j: Int) = cornerInset(ro, minOf(j, h - 1 - j))
		fun inner(j: Int): Int? = if (j <= 0 || j >= h - 1) null else cornerInset(ri, minOf(j - 1, ih - 1 - (j - 1)))
		var start = 0
		while (start < h) {
			val o = outer(start); val i = inner(start)
			var end = start + 1
			while (end < h && outer(end) == o && inner(end) == i) end++
			if (i == null) c.fill(x + o, y + start, x + w - o, y + end, argb)
			else {
				c.fill(x + o, y + start, x + 1 + i, y + end, argb)
				c.fill(x + w - 1 - i, y + start, x + w - o, y + end, argb)
			}
			start = end
		}
	}

	fun pill(c: StyleCanvas, x: Int, y: Int, w: Int, h: Int, fill: Int, border: Int? = null) =
		if (border == null) round(c, x, y, w, h, h / 2, fill) else roundFrame(c, x, y, w, h, h / 2, fill, border)
}
