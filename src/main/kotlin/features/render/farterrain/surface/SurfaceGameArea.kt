/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, where the game can be drawing, measured from what it has loaded and what it
 * really drew (pure Kotlin, unit-tested).
 *
 * Far terrain never reads the game's render-distance setting (nor the server's view distance, nor the chunk storage
 * radius derived from them). The game cannot draw a chunk it has not loaded, and its drawn list says exactly what it
 * drew, so everything the hand-off needs is measured here instead:
 *  * [extent]: how far (Chebyshev, chunks) loaded chunks reach around the camera's chunk; the hand-off box
 *    (SurfaceHandoffGrid) and the forget radius of the hand-off state;
 *  * [loadedRadius]: the largest rounded disc around the camera's chunk whose chunks are all loaded;
 *  * [drawnReach]: how far out the game drew over the last few seconds (SurfaceDrawnList.frameReach);
 *  * [radius]: the radius the prediction geometry uses where no exact per-frame answer exists (the pre-draw band of a
 *    renderer's late list, the prediction path): the drawn reach when known, never more than the loaded disc.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import it.unimi.dsi.fastutil.longs.LongOpenHashSet

class SurfaceGameArea(private val reachWindowMs: Long = REACH_WINDOW_MS) {
	private val keys = LongOpenHashSet()
	private var owner: Any? = null
	private var dirty = true
	private var atX = Int.MIN_VALUE
	private var atZ = Int.MIN_VALUE

	var extent = 0
		private set

	var loadedRadius = 0
		private set

	private val bucketMax = IntArray(BUCKETS)
	private val bucketId = LongArray(BUCKETS) { Long.MIN_VALUE }

	fun chunkLoaded(level: Any, x: Int, z: Int) {
		if (owner !== level) reset(level)
		keys.add(SurfaceIndex.key(x, z))
		dirty = true
	}

	fun chunkUnloaded(level: Any, x: Int, z: Int) {
		if (owner !== level) return
		keys.remove(SurfaceIndex.key(x, z))
		dirty = true
	}

	fun loadedChunks(level: Any): LongArray = if (owner === level) keys.toLongArray() else LongArray(0)

	private fun reset(level: Any?) {
		keys.clear(); owner = level; dirty = true
		extent = 0; loadedRadius = 0
		bucketId.fill(Long.MIN_VALUE); bucketMax.fill(0)
	}

	fun clear() = reset(null)

	fun refresh(level: Any, ccx: Int, ccz: Int, loaded: (Int, Int) -> Boolean) {
		if (owner !== level) reset(level)
		if (!dirty && ccx == atX && ccz == atZ) return
		dirty = false; atX = ccx; atZ = ccz
		var ext = 0
		val it = keys.iterator()
		while (it.hasNext()) {
			val k = it.nextLong()
			val x = SurfaceIndex.keyX(k); val z = SurfaceIndex.keyZ(k)
			if (!loaded(x, z)) { it.remove(); continue }
			ext = maxOf(ext, Math.abs(x - ccx), Math.abs(z - ccz))
		}
		while (ext < MAX_EXTENT && ringHasLoaded(ccx, ccz, ext + 1, loaded)) ext++
		extent = ext

		var minSq = Long.MAX_VALUE
		val r = ext + 1
		for (dx in -r..r) for (dz in -r..r) {
			val sq = SurfaceHandoff.roundedDistSq(0, 0, dx, dz)
			if (sq >= minSq) continue
			if (!loaded(ccx + dx, ccz + dz)) minSq = sq
		}
		loadedRadius = if (minSq == Long.MAX_VALUE) r else Math.floor(Math.sqrt(minSq.toDouble())).toInt()
	}

	private fun ringHasLoaded(ccx: Int, ccz: Int, r: Int, loaded: (Int, Int) -> Boolean): Boolean {
		for (d in -r..r) {
			if (loaded(ccx + d, ccz - r) || loaded(ccx + d, ccz + r) || loaded(ccx - r, ccz + d) || loaded(ccx + r, ccz + d)) return true
		}
		return false
	}

	fun drawnFrame(reach: Int, nowMs: Long) {
		if (reach <= 0) return
		val id = Math.floorDiv(nowMs, reachWindowMs / BUCKETS)
		val i = Math.floorMod(id, BUCKETS.toLong()).toInt()
		if (bucketId[i] != id) { bucketId[i] = id; bucketMax[i] = 0 }
		if (reach > bucketMax[i]) bucketMax[i] = reach
	}

	fun drawnReach(nowMs: Long): Int {
		val id = Math.floorDiv(nowMs, reachWindowMs / BUCKETS)
		var m = 0
		for (i in 0 until BUCKETS) if (bucketId[i] != Long.MIN_VALUE && id - bucketId[i] in 0 until BUCKETS) m = maxOf(m, bucketMax[i])
		return m
	}

	fun radius(nowMs: Long): Int {
		val d = drawnReach(nowMs)
		return if (d > 0) minOf(d, loadedRadius) else loadedRadius
	}

	companion object {

		fun urgentReach(extent: Int): Int = maxOf(0, extent) + URGENT_MARGIN
		const val URGENT_MARGIN = 2

		const val REACH_WINDOW_MS = 5000L
		private const val BUCKETS = 5

		const val MAX_EXTENT = 512
	}
}
