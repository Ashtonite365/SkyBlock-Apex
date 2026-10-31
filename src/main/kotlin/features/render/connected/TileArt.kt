/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, runtime tile art. Every tile is derived from the texture currently loaded for the
 * block (vanilla or a resource pack), so no tile art ships with Apex. Pure code over ARGB int arrays.
 */
package dev.ashtonite365.apex.features.render.connected

enum class TileStyle { FRAME, SURFACE }

object TileArt {

	fun border(size: Int): Int = maxOf(1, size / 16)

	fun tile(style: TileStyle, src: IntArray, size: Int, key: Int, bands: IntArray? = null, field: SurfaceField? = null): IntArray {
		if (style == TileStyle.FRAME) return generate(src, size, key)
		val b = bands ?: bands(src, size)
		return generateSurface(src, size, key, b, field ?: surfaceField(src, size, b))
	}

	fun generate(src: IntArray, size: Int, key: Int, out: IntArray = IntArray(size * size)): IntArray {
		val b = border(size)
		val t = key and ConnectTiles.K_TOP != 0
		val r = key and ConnectTiles.K_RIGHT != 0
		val bo = key and ConnectTiles.K_BOTTOM != 0
		val l = key and ConnectTiles.K_LEFT != 0
		for (y in 0 until size) {
			val top = y < b
			val bottom = y >= size - b
			for (x in 0 until size) {
				val left = x < b
				val right = x >= size - b
				var sx = x
				var sy = y
				val keep = (top && !t) || (bottom && !bo) || (left && !l) || (right && !r) ||
					(top && left && key and ConnectTiles.K_CORNER_TL != 0) ||
					(top && right && key and ConnectTiles.K_CORNER_TR != 0) ||
					(bottom && right && key and ConnectTiles.K_CORNER_BR != 0) ||
					(bottom && left && key and ConnectTiles.K_CORNER_BL != 0)
				if (!keep) {
					if (left) sx = 2 * b - 1 - x else if (right) sx = 2 * (size - b) - 1 - x
					if (top) sy = 2 * b - 1 - y else if (bottom) sy = 2 * (size - b) - 1 - y
				}
				out[y * size + x] = src[sy * size + sx]
			}
		}
		return out
	}

	const val TOP = 0
	const val RIGHT = 1
	const val BOTTOM = 2
	const val LEFT = 3

	fun minBand(size: Int): Int = maxOf(1, size / 8)

	fun maxBand(size: Int): Int = maxOf(minBand(size), size * 3 / 16)

	private fun luma(p: Int): Float {
		val a = (p ushr 24 and 255) / 255f
		return (0.299f * (p ushr 16 and 255) + 0.587f * (p ushr 8 and 255) + 0.114f * (p and 255)) * a
	}

	fun bands(src: IntArray, size: Int): IntArray {
		val out = IntArray(4)
		val mw = maxBand(size)
		val lo = mw
		val hi = size - mw
		if (hi - lo < 2) { out.fill(minBand(size).coerceAtMost(maxOf(1, size / 4))); return out }
		fun rowMean(y: Int): Float { var s = 0f; for (x in lo until hi) s += luma(src[y * size + x]); return s / (hi - lo) }
		fun colMean(x: Int): Float { var s = 0f; for (y in lo until hi) s += luma(src[y * size + x]); return s / (hi - lo) }
		fun stats(f: (Int) -> Float): FloatArray {
			var s = 0f; var q = 0f
			for (i in lo until hi) { val m = f(i); s += m; q += m * m }
			val n = (hi - lo).toFloat()
			val mu = s / n
			return floatArrayOf(mu, kotlin.math.sqrt(maxOf(0f, q / n - mu * mu)))
		}
		val rows = stats(::rowMean)
		val cols = stats(::colMean)
		for (side in 0 until 4) {
			val st = if (side == TOP || side == BOTTOM) rows else cols
			val limit = maxOf(2.5f * st[1], 6f)
			var w = 1
			for (k in 1 until mw) {
				val m = when (side) {
					TOP -> rowMean(k)
					BOTTOM -> rowMean(size - 1 - k)
					LEFT -> colMean(k)
					else -> colMean(size - 1 - k)
				}
				if (kotlin.math.abs(m - st[0]) > limit) w = k + 1
			}
			out[side] = maxOf(w, minBand(size))
		}
		return out
	}

	class SurfaceField(val size: Int, val h: IntArray, val v: IntArray, val o: IntArray, val reachT: Int, val reachR: Int, val reachB: Int, val reachL: Int)

	private class AxisFill(val map: IntArray, val reachLo: Int, val reachHi: Int)

	private fun diff(a: Int, b: Int): Int {
		var d = 0
		var sh = 0
		while (sh < 32) { val c = (a ushr sh and 255) - (b ushr sh and 255); d += c * c; sh += 8 }
		return d
	}

	private fun axisFill(size: Int, lo: Int, hi: Int, px: (Int, Int) -> Int): AxisFill {
		val map = IntArray(size * size) { it % size }
		val w = hi - lo
		val g = size - w
		if (g <= 0) return AxisFill(map, 0, 0)
		val m = (w - g) / 2
		if (w <= 0 || m < 1) {

			for (line in 0 until size) for (i in 0 until size) if (i < lo || i >= hi) map[line * size + i] = lo + Math.floorMod(i - lo, maxOf(1, w))
			return AxisFill(map, lo, size - hi)
		}
		val p = g + 2 * m
		val cuts = m + 1
		val big = Long.MAX_VALUE / 4

		fun cutCost(err: IntArray, line: Int, k: Int): Long {
			var c = 0L
			if (k - 1 >= 0) c += err[line * m + k - 1]
			if (k < m) c += err[line * m + k]
			return c
		}

		fun cheapestCut(err: IntArray): Pair<Long, IntArray> {
			var best = big
			var bestPath = IntArray(size)
			val dp = LongArray(size * cuts)
			val from = IntArray(size * cuts)
			for (k0 in 0 until cuts) {
				java.util.Arrays.fill(dp, big)
				dp[k0] = cutCost(err, 0, k0)
				for (line in 1 until size) for (k in 0 until cuts) {
					var bk = -1
					var bc = big
					for (d in -1..1) {
						val pk = k + d
						if (pk < 0 || pk >= cuts) continue
						val c = dp[(line - 1) * cuts + pk]
						if (c < bc) { bc = c; bk = pk }
					}
					if (bk >= 0 && bc < big) { dp[line * cuts + k] = bc + cutCost(err, line, k); from[line * cuts + k] = bk }
				}
				for (k in maxOf(0, k0 - 1)..minOf(m, k0 + 1)) {
					val c = dp[(size - 1) * cuts + k]
					if (c < best) {
						best = c
						val path = IntArray(size)
						var kk = k
						for (line in size - 1 downTo 0) { path[line] = kk; if (line > 0) kk = from[line * cuts + kk] }
						bestPath = path
					}
				}
			}
			return best to bestPath
		}
		var bestCost = big
		var bestStart = lo
		var bestLow = IntArray(size)
		var bestHigh = IntArray(size)
		val errLow = IntArray(size * m)
		val errHigh = IntArray(size * m)
		for (start in lo..hi - p) {
			for (line in 0 until size) for (j in 0 until m) {

				errLow[line * m + j] = diff(px(hi - m + j, line), px(start + j, line))

				errHigh[line * m + j] = diff(px(lo + j, line), px(start + m + g + j, line))
			}
			val (cl, pl) = cheapestCut(errLow)
			if (cl >= bestCost) continue
			val (ch, ph) = cheapestCut(errHigh)
			if (cl + ch < bestCost) { bestCost = cl + ch; bestStart = start; bestLow = pl; bestHigh = ph }
		}
		for (line in 0 until size) for (j in 0 until p) {
			val i = Math.floorMod(hi - m + j, size)
			val useStrip = when {
				j < m -> j >= bestLow[line]
				j < m + g -> true
				else -> j - m - g < bestHigh[line]
			}
			if (useStrip) map[line * size + i] = bestStart + j
		}
		return AxisFill(map, lo + m, size - hi + m)
	}

	fun surfaceField(src: IntArray, size: Int, bands: IntArray = bands(src, size)): SurfaceField {
		val wt = bands[TOP]; val wr = bands[RIGHT]; val wb = bands[BOTTOM]; val wl = bands[LEFT]
		val n = size * size
		if (size - wl - wr <= 0 || size - wt - wb <= 0) return SurfaceField(size, src.copyOf(), src.copyOf(), src.copyOf(), 0, 0, 0, 0)
		val across = axisFill(size, wl, size - wr) { x, y -> src[y * size + x] }
		val down = axisFill(size, wt, size - wb) { y, x -> src[y * size + x] }
		val h = IntArray(n) { i -> val x = i % size; val y = i / size; src[y * size + across.map[y * size + x]] }
		val v = IntArray(n) { i -> val x = i % size; val y = i / size; src[down.map[x * size + y] * size + x] }
		val o = IntArray(n) { i -> val x = i % size; val y = i / size; v[y * size + across.map[y * size + x]] }
		return SurfaceField(size, h, v, o, down.reachLo, across.reachHi, down.reachHi, across.reachLo)
	}

	fun generateSurface(
		src: IntArray, size: Int, key: Int, bands: IntArray = bands(src, size), field: SurfaceField = surfaceField(src, size, bands),
		out: IntArray = IntArray(size * size),
	): IntArray {
		val wt = bands[TOP]; val wr = bands[RIGHT]; val wb = bands[BOTTOM]; val wl = bands[LEFT]
		val t = key and ConnectTiles.K_TOP != 0
		val r = key and ConnectTiles.K_RIGHT != 0
		val bo = key and ConnectTiles.K_BOTTOM != 0
		val l = key and ConnectTiles.K_LEFT != 0
		for (y in 0 until size) {
			val top = y < wt
			val bottom = y >= size - wb
			val yc = (t && y < field.reachT) || (bo && y >= size - field.reachB)
			for (x in 0 until size) {
				val i = y * size + x
				val left = x < wl
				val right = x >= size - wr
				val inner = (top && left && key and ConnectTiles.K_CORNER_TL != 0) ||
					(top && right && key and ConnectTiles.K_CORNER_TR != 0) ||
					(bottom && right && key and ConnectTiles.K_CORNER_BR != 0) ||
					(bottom && left && key and ConnectTiles.K_CORNER_BL != 0)
				val xc = (l && x < field.reachL) || (r && x >= size - field.reachR)
				out[i] = when {
					inner -> src[i]
					xc && yc -> field.o[i]
					xc -> field.h[i]
					yc -> field.v[i]
					else -> src[i]
				}
			}
		}
		return out
	}
}
