/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, surface column extraction (pure Kotlin, unit-tested).
 * Reads a chunk (plus a one-block border from its loaded neighbours) through [ColumnReader] and keeps every block
 * with a face toward open air, at any depth (several runs per column, hidden spans between them), plus the single
 * underside block of a solid mass over the void too thick for the flood range. See LOD-DESIGN.md, section 1.1.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

interface ColumnReader {
	val minY: Int

	fun maxY(lx: Int, lz: Int): Int

	fun has(lx: Int, lz: Int): Boolean

	fun state(lx: Int, y: Int, lz: Int): String

	fun biome(lx: Int, y: Int, lz: Int): String
}

object SurfaceExtractor {
	const val MAX_DEPTH = 384

	const val MAX_RUN = MAX_DEPTH + 1

	const val MAX_COLUMN = 4096

	const val FLOOD_BELOW = 64
	private const val NONE = Int.MIN_VALUE

	private class ColumnInfo(val top: Int, val opaqueTop: Int)

	private const val C_AIR: Byte = 0
	private const val C_SEE: Byte = 1
	private const val C_SOLID: Byte = 2
	private const val C_CLOSED: Byte = 3

	fun extract(cx: Int, cz: Int, r: ColumnReader, classifier: BlockClassifier, now: Long): SurfaceChunk {
		val classes = HashMap<String, BlockClass>()
		fun cls(s: String): BlockClass = if (s == SurfaceChunk.AIR) BlockClass.AIR else classes.getOrPut(s) { classifier.classify(s) }

		fun info(lx: Int, lz: Int): ColumnInfo {
			var top = NONE
			var y = r.maxY(lx, lz)
			while (y >= r.minY) {
				val c = cls(r.state(lx, y, lz))
				if (c != BlockClass.AIR) {
					if (top == NONE) top = y
					if (c == BlockClass.OPAQUE) return ColumnInfo(top, y)
				}
				y--
			}
			return ColumnInfo(top, NONE)
		}

		val grid = arrayOfNulls<ColumnInfo>(18 * 18)
		for (lz in -1..16) for (lx in -1..16) {
			val border = lx == -1 || lx == 16 || lz == -1 || lz == 16
			if (border && !r.has(lx, lz)) continue
			grid[(lx + 1) + (lz + 1) * 18] = info(lx, lz)
		}

		val palette = ArrayList<String>().apply { add(SurfaceChunk.AIR) }
		val paletteIndex = HashMap<String, Int>().apply { put(SurfaceChunk.AIR, 0) }
		val biomes = ArrayList<String>()
		val biomeIndex = HashMap<String, Int>()
		val top = IntArray(256) { SurfaceChunk.NO_COLUMN }
		val depth = IntArray(256)
		val colBiome = IntArray(256)
		val offsets = IntArray(257)
		val blocks = ArrayList<Int>(512)
		var underY: IntArray? = null
		var underState: IntArray? = null
		fun paletteOf(s: String): Int = paletteIndex.getOrPut(s) { palette.add(s); palette.size - 1 }

		var yHi = NONE
		var minOpaque = Int.MAX_VALUE
		for (ci in grid) {
			if (ci == null || ci.top == NONE) continue
			if (ci.top > yHi) yHi = ci.top
			if (ci.opaqueTop != NONE && ci.opaqueTop < minOpaque) minOpaque = ci.opaqueTop
		}
		var ownAny = false
		for (lz in 0..15) for (lx in 0..15) if (grid[(lx + 1) + (lz + 1) * 18]!!.top != NONE) ownAny = true
		if (!ownAny || yHi == NONE) {
			return SurfaceChunk(cx, cz, palette, biomes, top, depth, colBiome, IntArray(0), offsets, now, edgesOf(r), null, null)
		}
		yHi += 1
		val yLo = maxOf(r.minY, (if (minOpaque == Int.MAX_VALUE) r.minY else minOpaque) - FLOOD_BELOW)
		val h = yHi - yLo + 1
		fun idx(gx: Int, y: Int, gz: Int) = ((y - yLo) * 18 + gz) * 18 + gx
		val cell = ByteArray(18 * 18 * h)
		val own = arrayOfNulls<String>(16 * 16 * h)
		for (gz in 0 until 18) for (gx in 0 until 18) {
			val ci = grid[gx + gz * 18]
			if (ci == null) { for (y in yLo..yHi) cell[idx(gx, y, gz)] = C_CLOSED; continue }
			if (ci.top == NONE) continue
			val lx = gx - 1; val lz = gz - 1
			val isOwn = lx in 0..15 && lz in 0..15
			for (y in yLo..minOf(ci.top, yHi)) {
				val st = r.state(lx, y, lz)
				if (isOwn) own[((y - yLo) * 16 + lz) * 16 + lx] = st
				cell[idx(gx, y, gz)] = when (cls(st)) { BlockClass.AIR -> C_AIR; BlockClass.TRANSPARENT -> C_SEE; BlockClass.OPAQUE -> C_SOLID }
			}
		}

		val open = BooleanArray(cell.size)
		val queue = IntArray(cell.size)
		var qh = 0; var qt = 0
		for (gz in 0 until 18) for (gx in 0 until 18) {
			val ci = grid[gx + gz * 18] ?: continue
			val from = if (ci.opaqueTop == NONE) yLo else maxOf(yLo, ci.opaqueTop + 1)
			for (y in from..yHi) {
				val i = idx(gx, y, gz)
				val c = cell[i]
				if ((c == C_AIR || c == C_SEE) && !open[i]) { open[i] = true; queue[qt++] = i }
			}
		}
		val layer = 18 * 18
		while (qh < qt) {
			val i = queue[qh++]
			val gx = i % 18; val gz = (i / 18) % 18; val yi = i / layer
			fun visit(j: Int) {
				if (open[j]) return
				val c = cell[j]
				if (c == C_AIR || c == C_SEE) { open[j] = true; queue[qt++] = j }
			}
			if (gx > 0) visit(i - 1)
			if (gx < 17) visit(i + 1)
			if (gz > 0) visit(i - 18)
			if (gz < 17) visit(i + 18)
			if (yi > 0) visit(i - layer)
			if (yi < h - 1) visit(i + layer)
		}
		fun isOpen(gx: Int, y: Int, gz: Int): Boolean = when {
			y > yHi -> true
			y < yLo -> false
			else -> open[idx(gx, y, gz)]
		}
		fun nonAirAt(gx: Int, y: Int, gz: Int): Boolean {
			if (y < yLo || y > yHi) return false
			val c = cell[idx(gx, y, gz)]
			return c == C_SEE || c == C_SOLID
		}

		fun voidBelow(lx: Int, y: Int, lz: Int): Boolean {
			if (y - r.minY + 1 > MAX_DEPTH) return false
			var yy = y
			while (yy >= r.minY) {
				if (cls(r.state(lx, yy, lz)) != BlockClass.AIR) return false
				yy--
			}
			return true
		}

		val col0 = IntArray(MAX_COLUMN)
		for (lz in 0..15) for (lx in 0..15) {
			val col = SurfaceIndex.col(lx, lz)
			offsets[col] = blocks.size
			val me = grid[(lx + 1) + (lz + 1) * 18]!!
			if (me.top == NONE) continue
			val gx = lx + 1; val gz = lz + 1

			var n = 0
			var lastKept = -1
			var y = me.top
			while (y >= yLo && n < MAX_COLUMN) {
				val c = cell[idx(gx, y, gz)]
				val v = when (c) {
					C_AIR -> if (open[idx(gx, y, gz)]) 0 else -1
					else -> {
						val exposed = isOpen(gx, y + 1, gz) || isOpen(gx, y - 1, gz) || isOpen(gx - 1, y, gz) ||
							isOpen(gx + 1, y, gz) || isOpen(gx, y, gz - 1) || isOpen(gx, y, gz + 1)
						if (exposed) paletteOf(own[((y - yLo) * 16 + lz) * 16 + lx]!!) else -1
					}
				}
				col0[n] = v

				if (v > 0 || (v == 0 && (nonAirAt(gx - 1, y, gz) || nonAirAt(gx + 1, y, gz) || nonAirAt(gx, y, gz - 1) || nonAirAt(gx, y, gz + 1)))) lastKept = n
				n++
				y--
			}
			if (lastKept < 0) continue
			var count = lastKept + 1

			if (col0[lastKept] > 0 && count < n && col0[count] == 0) count++
			else if (col0[lastKept] > 0 && count == n && me.top - count >= r.minY && voidBelow(lx, me.top - count, lz)) {
				col0[count] = 0; count++
			}

			var i = 0
			while (i < count) {
				if (col0[i] == -1) {
					var j = i
					while (j < count && col0[j] == -1) j++
					blocks.add(-(j - i))
					i = j
				} else { blocks.add(col0[i]); i++ }
			}
			top[col] = me.top
			depth[col] = count
			val bottomY = me.top - count + 1

			if (yLo > r.minY && cell[idx(gx, yLo, gz)] != C_AIR && cls(r.state(lx, yLo - 1, lz)) != BlockClass.AIR &&
				cls(r.state(lx, r.minY, lz)) == BlockClass.AIR) {
				var yy = yLo - 1
				val limit = maxOf(r.minY, yy - MAX_DEPTH + 1)
				while (yy >= limit && cls(r.state(lx, yy, lz)) != BlockClass.AIR) yy--
				if (yy >= r.minY && yy >= limit && yy + 1 < bottomY && voidBelow(lx, yy, lz)) {
					if (underY == null) { underY = IntArray(256) { SurfaceChunk.NO_COLUMN }; underState = IntArray(256) }
					underY!![col] = yy + 1
					underState!![col] = paletteOf(r.state(lx, yy + 1, lz))
				}
			}
			val b = r.biome(lx, me.top, lz)
			colBiome[col] = biomeIndex.getOrPut(b) { biomes.add(b); biomes.size - 1 }
		}
		offsets[256] = blocks.size
		return SurfaceChunk(cx, cz, palette, biomes, top, depth, colBiome, blocks.toIntArray(), offsets, now, edgesOf(r), underY, underState)
	}

	private fun edgesOf(r: ColumnReader): Int {
		var edges = 0
		if (r.has(0, -1)) edges = edges or SurfaceChunk.EDGE_NORTH
		if (r.has(0, 16)) edges = edges or SurfaceChunk.EDGE_SOUTH
		if (r.has(-1, 0)) edges = edges or SurfaceChunk.EDGE_WEST
		if (r.has(16, 0)) edges = edges or SurfaceChunk.EDGE_EAST
		return edges
	}
}
