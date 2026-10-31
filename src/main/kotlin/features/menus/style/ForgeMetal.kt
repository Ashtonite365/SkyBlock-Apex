// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

object ForgeMetal {
	const val POKE = 3
	const val TAB = 7

	const val SPARK_MAX_ALPHA = 0x60

	data class Spark(val x: Int, val y: Int, val size: Int, val argb: Int)

	fun sparks(seed: Long, x: Int, y: Int, w: Int, h: Int, orange: Int): List<Spark> {
		val m = 6
		if (w <= 2 * m + 2 || h <= 2 * m + 2) return emptyList()
		val rnd = java.util.Random(seed)
		val n = (w * h / 2200).coerceIn(3, 40)
		return List(n) {
			val size = 1 + rnd.nextInt(3)
			val a = 0x28 + rnd.nextInt(SPARK_MAX_ALPHA - 0x28 + 1)
			Spark(x + m + rnd.nextInt(w - 2 * m), y + m + rnd.nextInt(h - 2 * m), size, (orange and 0xFFFFFF) or (a shl 24))
		}
	}

	fun tabs(x: Int, y: Int, w: Int, h: Int): List<IntArray> {
		val i = TAB - POKE
		return listOf(
			intArrayOf(x - POKE, y - POKE, x + i, y + i), intArrayOf(x + w - i, y - POKE, x + w + POKE, y + i),
			intArrayOf(x - POKE, y + h - i, x + i, y + h + POKE), intArrayOf(x + w - i, y + h - i, x + w + POKE, y + h + POKE))
	}

	fun plate(c: StyleCanvas, p: StylePalette, x: Int, y: Int, w: Int, h: Int) {
		if (p.style != Style.FORGE || w < 12 || h < 12) return
		val heat = 0xFF8A3D
		val r = p.cardRadius

		var row = y + 3
		while (row < y + h - 3) {
			val light = (row / 3) % 2 == 0
			val off = ((row * 7) % 11)
			c.fill(x + 3 + off, row, x + w - 3 - (10 - off), row + 1, if (light) 0x0CFFFFFF else 0x10000000)
			row += 3
		}

		c.fill(x + r, y + 1, x + w - r, y + 3, 0x30000000); c.fill(x + r, y + h - 3, x + w - r, y + h - 1, 0x38000000)
		c.fill(x + 1, y + r, x + 3, y + h - r, 0x30000000); c.fill(x + w - 3, y + r, x + w - 1, y + h - r, 0x30000000)
		val glow = (heat and 0xFFFFFF) or (0x40 shl 24)
		c.fill(x + r, y + 3, x + w - r, y + 4, glow); c.fill(x + r, y + h - 4, x + w - r, y + h - 3, glow)
		c.fill(x + 3, y + r, x + 4, y + h - r, glow); c.fill(x + w - 4, y + r, x + w - 3, y + h - r, glow)

		for (s in sparks((x.toLong() * 73856093) xor (y.toLong() * 19349663) xor (w.toLong() shl 20) xor h.toLong(), x, y, w, h, heat)) {
			c.fill(s.x, s.y, s.x + 1, s.y + 1, s.argb)
			for (k in 1..s.size) {
				val a = (s.argb ushr 24) * (s.size + 1 - k) / (s.size + 1)
				val col = (s.argb and 0xFFFFFF) or (a shl 24)
				c.fill(s.x - k, s.y, s.x - k + 1, s.y + 1, col); c.fill(s.x + k, s.y, s.x + k + 1, s.y + 1, col)
				c.fill(s.x, s.y - k, s.x + 1, s.y - k + 1, col); c.fill(s.x, s.y + k, s.x + 1, s.y + k + 1, col)
			}
		}

		val plate = if (p.dark) 0xFF4A4440.toInt() else 0xFF8C8580.toInt()
		for (t in tabs(x, y, w, h)) {
			c.fill(t[0], t[1], t[2], t[3], p.border or (0xFF shl 24))
			c.fill(t[0] + 1, t[1] + 1, t[2] - 1, t[3] - 1, plate)
			c.fill(t[0] + 1, t[1] + 1, t[2] - 1, t[1] + 2, 0x40FFFFFF)
			val cx = (t[0] + t[2]) / 2; val cy = (t[1] + t[3]) / 2
			c.fill(cx, cy, cx + 1, cy + 1, (heat and 0xFFFFFF) or (0xC0 shl 24))
		}
	}
}
