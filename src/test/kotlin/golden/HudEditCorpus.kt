// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object HudEditCorpus {
	data class ResizeIn(val handle: Int, val x0: Int, val y0: Int, val uw: Int, val uh: Int, val sx: Float, val sy: Float, val dx: Double, val dy: Double, val free: Boolean, val sw: Int, val sh: Int) {
		val key get() = "h$handle x$x0 y$y0 u${uw}x$uh s$sx/$sy d$dx/$dy f$free scr${sw}x$sh"
	}

	fun resize(): List<ResizeIn> {
		val r = Random(1028)
		val out = mutableListOf<ResizeIn>()
		val sizes = listOf(100 to 20, 8 to 8, 1 to 40, 0 to 10, 480 to 10, 37 to 91, 128 to 64)
		val scales = listOf(1f to 1f, 0.5f to 0.5f, 2f to 1f, 0.25f to 6f, 1.37f to 1.37f, 3.3f to 0.8f)
		val deltas = listOf(-400.0, -200.0, -37.0, -5.0, -1.0, 0.0, 0.5, 1.0, 3.0, 50.0, 400.0)

		for (h in 0 until 8) for ((uw, uh) in sizes) for ((sx, sy) in scales) for (free in listOf(false, true))
			for (i in 0 until 6) out += ResizeIn(h, 50, 40, uw, uh, sx, sy, deltas[r.nextInt(deltas.size)], deltas[r.nextInt(deltas.size)], free, 480, 270)

		repeat(3000) {
			out += ResizeIn(r.nextInt(8), r.nextInt(-20, 500), r.nextInt(-20, 300), r.nextInt(0, 300), r.nextInt(0, 200),
				r.nextInt(25, 600) / 100f, r.nextInt(25, 600) / 100f, r.nextDouble(-300.0, 300.0), r.nextDouble(-300.0, 300.0),
				r.nextBoolean(), listOf(480, 320, 960, 100)[r.nextInt(4)], listOf(270, 240, 540, 60)[r.nextInt(4)])
		}
		return out
	}

	fun handleProbes(): List<DoubleArray> {
		val r = Random(2810)
		return List(3000) {
			val x = r.nextInt(0, 400).toDouble(); val y = r.nextInt(0, 200).toDouble()
			val w = r.nextInt(0, 120).toDouble(); val h = r.nextInt(0, 80).toDouble()
			val mx = x + listOf(0.0, w / 2, w)[r.nextInt(3)] + r.nextDouble(-5.0, 5.0)
			val my = y + listOf(0.0, h / 2, h)[r.nextInt(3)] + r.nextDouble(-5.0, 5.0)
			doubleArrayOf(mx, my, x, y, w, h)
		}
	}

	fun toolbars(): List<Pair<IntArray, List<IntArray>>> {
		val r = Random(77)
		return List(2000) {
			val sw = listOf(480, 320, 640, 100)[r.nextInt(4)]; val sh = listOf(270, 240, 360, 50)[r.nextInt(4)]
			val w = r.nextInt(10, 200); val h = r.nextInt(8, 70)
			val n = r.nextInt(0, 8)
			intArrayOf(w, h, sw, sh) to List(n) { intArrayOf(r.nextInt(-20, sw), r.nextInt(-20, sh), r.nextInt(0, 200), r.nextInt(0, 100)) }
		}
	}

	fun rects(seed: Int, n: Int): List<IntArray> {
		val r = Random(seed)
		return List(n) { intArrayOf(r.nextInt(-50, 500), r.nextInt(-50, 300), r.nextInt(0, 300), r.nextInt(0, 200)) }
	}

	fun fmt(a: IntArray?) = a?.joinToString(",") ?: "null"
}
