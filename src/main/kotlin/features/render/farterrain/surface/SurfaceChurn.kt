/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, telling real block changes from capture noise (pure Kotlin, unit-tested).
 *
 * Why so many chunks counted as "changed": the same chunk captured twice rarely gives identical data even when nothing
 * was built or broken.
 *  1. Neighbour knowledge. A chunk captured while a neighbour was not loaded assumes level ground on that side, so its
 *     border columns stop shallow; captured again with the neighbour loaded, the same columns reach down a cliff. Either
 *     order made a "change", and a shallow capture could even overwrite a better one.
 *  2. Ticking block properties: crop / sapling age and stage, farmland moisture, leaf distance and persistence, the
 *     level of flowing water and lava, redstone power. They change on their own, all the time, without changing what
 *     far terrain looks like.
 *  3. Edge chunks at the edge of what the server sends were re-captured every 5 seconds forever, waiting for
 *     neighbours that never load, and each re-capture could hit (1) or (2).
 * So a new capture is compared column by column with those properties left out: a column that is only a shallower
 * version of the stored one keeps the stored run, a deeper version refines it (stored and re-meshed, not counted as a
 * change), and only a real difference counts as "changed".
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

object SurfaceChurn {
	enum class Result {

		SAME,

		REFINED,

		CHANGED,
	}

	class Outcome(val result: Result, val chunk: SurfaceChunk, val firstDifference: String?)

	private val TICKING = setOf("age", "stage", "moisture", "distance", "persistent", "power", "triggered", "honey_level", "bites")
	private val FLUIDS = setOf("minecraft:water", "minecraft:lava")
	private val normCache = java.util.concurrent.ConcurrentHashMap<String, String>()

	fun stable(state: String): String = normCache.getOrPut(state) {
		val b = state.indexOf('[')
		if (b < 0 || !state.endsWith("]")) return@getOrPut state
		val name = state.substring(0, b)
		val props = state.substring(b + 1, state.length - 1).split(',').filter { p ->
			val k = p.substringBefore('=')
			k !in TICKING && !(k == "level" && name in FLUIDS)
		}
		if (props.isEmpty()) name else props.joinToString(",", "$name[", "]")
	}

	private fun runOf(c: SurfaceChunk, col: Int): List<String?> {
		if (c.isEmpty(col)) return emptyList()
		val out = ArrayList<String?>(c.depth[col])
		for (i in c.offsets[col] until c.offsets[col + 1]) {
			val v = c.blocks[i]
			if (v < 0) repeat(-v) { out.add(null) } else out.add(stable(c.palette[v]))
		}
		return out
	}

	fun compare(old: SurfaceChunk, new: SurfaceChunk): Outcome {
		var newBetter = false
		var oldBetter = false
		val pick = BooleanArray(256)
		for (col in 0 until 256) {
			val oEmpty = old.isEmpty(col)
			val nEmpty = new.isEmpty(col)
			if (oEmpty != nEmpty || (!oEmpty && old.top[col] != new.top[col])) {
				return Outcome(Result.CHANGED, new, describe(old, new, col, if (oEmpty || nEmpty) null else maxOf(old.top[col], new.top[col])))
			}
			if (oEmpty) continue
			val o = runOf(old, col)
			val n = runOf(new, col)
			val common = minOf(o.size, n.size)

			var nKnows = false
			var oKnows = false
			for (i in 0 until common) {
				val a = o[i]; val b = n[i]
				if (a == null) { if (b != null) nKnows = true; continue }
				if (b == null) { oKnows = true; continue }
				if (a != b) return Outcome(Result.CHANGED, new, describe(old, new, col, old.top[col] - i))
			}
			if (n.size > o.size) nKnows = true
			if (o.size > n.size) oKnows = true
			val ou = old.underside(col)
			val nu = new.underside(col)
			if (ou != SurfaceChunk.NO_COLUMN && nu != SurfaceChunk.NO_COLUMN &&
				(ou != nu || stable(old.palette[old.underState!![col]]) != stable(new.palette[new.underState!![col]]))) {
				return Outcome(Result.CHANGED, new, describe(old, new, col, minOf(ou, nu)))
			}
			if (ou == SurfaceChunk.NO_COLUMN && nu != SurfaceChunk.NO_COLUMN) nKnows = true
			if (ou != SurfaceChunk.NO_COLUMN && nu == SurfaceChunk.NO_COLUMN) oKnows = true

			if (nKnows) { pick[col] = true; newBetter = true }
			if (oKnows && !nKnows) oldBetter = true
		}
		val edges = old.edges or new.edges
		if (!newBetter) {
			val kept = if (edges == old.edges) old else old.withEdges(edges)
			return Outcome(Result.SAME, kept, null)
		}
		if (!oldBetter) return Outcome(Result.REFINED, if (edges == new.edges) new else new.withEdges(edges), null)
		return Outcome(Result.REFINED, merge(old, new, pick, edges), null)
	}

	private fun SurfaceChunk.withEdges(e: Int) =
		SurfaceChunk(x, z, palette, biomes, top, depth, columnBiome, blocks, offsets, capturedAt, e, underY, underState)

	fun merge(old: SurfaceChunk, new: SurfaceChunk, takeNew: BooleanArray, edges: Int): SurfaceChunk {
		val palette = ArrayList<String>().apply { add(SurfaceChunk.AIR) }
		val index = HashMap<String, Int>().apply { put(SurfaceChunk.AIR, 0) }
		fun p(s: String) = index.getOrPut(s) { palette.add(s); palette.size - 1 }
		val biomes = ArrayList<String>()
		val bIndex = HashMap<String, Int>()
		fun b(s: String) = bIndex.getOrPut(s) { biomes.add(s); biomes.size - 1 }
		val top = IntArray(256); val depth = IntArray(256); val colBiome = IntArray(256); val offsets = IntArray(257)
		val blocks = ArrayList<Int>()
		var underY: IntArray? = null
		var underState: IntArray? = null
		for (col in 0 until 256) {
			val src = if (takeNew[col]) new else old
			offsets[col] = blocks.size
			top[col] = src.top[col]
			depth[col] = src.depth[col]
			for (i in src.offsets[col] until src.offsets[col + 1]) { val v = src.blocks[i]; blocks.add(if (v < 0) v else p(src.palette[v])) }
			colBiome[col] = if (src.biomes.isEmpty()) 0 else b(src.biomes[src.columnBiome[col]])
			val u = src.underside(col)
			if (u != SurfaceChunk.NO_COLUMN) {
				if (underY == null) { underY = IntArray(256) { SurfaceChunk.NO_COLUMN }; underState = IntArray(256) }
				underY!![col] = u
				underState!![col] = p(src.palette[src.underState!![col]])
			}
		}
		offsets[256] = blocks.size
		return SurfaceChunk(new.x, new.z, palette, biomes, top, depth, colBiome, blocks.toIntArray(), offsets, new.capturedAt, edges, underY, underState)
	}

	private fun describe(old: SurfaceChunk, new: SurfaceChunk, col: Int, y: Int?): String {
		val lx = SurfaceIndex.colX(col); val lz = SurfaceIndex.colZ(col)
		val wx = old.x * 16 + lx; val wz = old.z * 16 + lz
		if (y == null) return "column $wx,$wz: ${if (old.isEmpty(col)) "empty" else "top ${old.top[col]}"} -> ${if (new.isEmpty(col)) "empty" else "top ${new.top[col]}"}"
		return "$wx,$y,$wz: ${old.state(lx, y, lz) ?: "hidden"} -> ${new.state(lx, y, lz) ?: "hidden"}" +
			if (old.top[col] != new.top[col]) " (top ${old.top[col]} -> ${new.top[col]})" else ""
	}

	fun edgesNowAvailable(edges: Int, loaded: (Int, Int) -> Boolean): Boolean =
		(edges and SurfaceChunk.EDGE_NORTH == 0 && loaded(0, -1)) ||
			(edges and SurfaceChunk.EDGE_SOUTH == 0 && loaded(0, 1)) ||
			(edges and SurfaceChunk.EDGE_WEST == 0 && loaded(-1, 0)) ||
			(edges and SurfaceChunk.EDGE_EAST == 0 && loaded(1, 0))
}
