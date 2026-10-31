// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import kotlin.math.sqrt

object StyleAnim {
	const val REDUCED_OPEN_MS = 80L

	const val GLITCH_STRETCH = 1.4

	fun openMs(s: StyleState): Long = when {
		!s.animate -> 0L
		s.reduceMotion -> REDUCED_OPEN_MS
		kind(s) == MenuAnim.GLITCH -> (230L * GLITCH_STRETCH).toLong()
		else -> 230L
	}

	fun closeMs(s: StyleState): Long = when {
		!s.animate || s.reduceMotion -> 0L
		else -> ((if (s.style == Style.SKYGLASS) 110L  else 120L ) * (if (kind(s) == MenuAnim.GLITCH) GLITCH_STRETCH else 1.0)).toLong()
	}

	fun kind(s: StyleState): MenuAnim = if (s.reduceMotion) MenuAnim.FADE else s.anim

	fun progress(elapsedMs: Long, durationMs: Long): Double =
		if (durationMs <= 0) 1.0 else (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)

	fun easeOutQuart(t: Double) = 1 - (1 - t).let { it * it * it * it }
	fun easeInQuad(t: Double) = t * t

	fun openReveal(s: StyleState, elapsedMs: Long): Double {
		val p = progress(elapsedMs, openMs(s))
		return if (p >= 1.0) 1.0 else if (s.reduceMotion) p else easeOutQuart(p)
	}

	const val SETTINGS_OPEN_STRETCH = 1.5

	fun settingsOpenMs(s: StyleState): Long = (openMs(s) * SETTINGS_OPEN_STRETCH).toLong()

	fun settingsOpenReveal(s: StyleState, elapsedMs: Long): Double = openReveal(s, (elapsedMs / SETTINGS_OPEN_STRETCH).toLong())

	fun closeVisible(s: StyleState, elapsedMs: Long): Double {
		val p = progress(elapsedMs, closeMs(s))
		return if (p >= 1.0) 0.0 else 1 - easeInQuad(p)
	}

	fun circleRadius(w: Int, h: Int, amount: Double): Double = sqrt((w * w + h * h) / 4.0) * amount.coerceIn(0.0, 1.0)

	fun circleHalfWidth(r: Double, dy: Double): Int = if (kotlin.math.abs(dy) >= r) 0 else sqrt(r * r - dy * dy).toInt()

	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

	fun glitchRects(seed: Long, elapsedMs: Long, w: Int, h: Int, amount: Double): List<Rect> {
		val a = amount.coerceIn(0.0, 1.0)
		if (a >= 1.0 || w <= 0 || h <= 0) return emptyList()
		val rnd = java.util.Random(seed * 31 + elapsedMs / 16)
		val n = (60 * (1 - a)).toInt() + 4
		return List(n) {
			val rw = 2 + rnd.nextInt(maxOf(1, minOf(18, w / 6)))
			val rh = 1 + rnd.nextInt(maxOf(1, minOf(6, h / 10)))
			Rect(rnd.nextInt(maxOf(1, w - rw)), rnd.nextInt(maxOf(1, h - rh)), rw, rh)
		}
	}

	const val GLITCH_BASE_W = 8
	const val GLITCH_BASE_H = 4
	const val GLITCH_FLICKER_MS = 33L

	data class GlitchBox(val x: Int, val y: Int, val w: Int, val h: Int, val sx: Double, val sy: Double, val settle: Double, val jumps: List<Pair<Int, Int>>, val salt: Int)

	fun glitchBoxes(seed: Long, w: Int, h: Int): List<GlitchBox> {
		if (w <= 0 || h <= 0) return emptyList()
		val rnd = java.util.Random(seed)
		val out = ArrayList<GlitchBox>()
		var y = 0
		while (y < h) {
			val sy = 2.0 + rnd.nextDouble() * 3.0
			val bh = minOf(h - y, maxOf(1, (GLITCH_BASE_H * sy).toInt()))
			var x = 0
			while (x < w) {
				val sx = 2.0 + rnd.nextDouble() * 3.0
				val bw = minOf(w - x, maxOf(1, (GLITCH_BASE_W * sx).toInt()))
				val n = rnd.nextInt(4)
				val jumps = List(n) { (rnd.nextInt(2 * bw + 1) - bw) * 2 to (rnd.nextInt(2 * bh + 1) - bh) * 2 }
				out += GlitchBox(x, y, bw, bh, sx, sy, rnd.nextDouble() * 0.8, jumps, rnd.nextInt())
				x += bw
			}
			y += bh
		}
		return out
	}

	data class GlitchState(val x: Int, val y: Int, val settled: Boolean, val on: Boolean)

	fun glitchState(b: GlitchBox, t: Double, elapsedMs: Long): GlitchState {
		if (t >= b.settle) return GlitchState(b.x, b.y, true, true)

		val n = b.jumps.size
		val phase = if (b.settle <= 0.0) n else ((t / b.settle) * (n + 1)).toInt().coerceIn(0, n)
		val (dx, dy) = if (phase == 0) 0 to 0 else b.jumps[phase - 1]
		val on = ((b.salt.toLong() * 31 + elapsedMs / GLITCH_FLICKER_MS) and 3L) != 0L
		return GlitchState(b.x + dx, b.y + dy, false, on)
	}
}
