/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, region layout and draw-range splitting (pure Kotlin, unit-tested).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

object SurfaceRegions {

	const val SIZE = 8
	const val SLOTS = SIZE * SIZE

	fun regionOf(chunk: Int): Int = Math.floorDiv(chunk, SIZE)

	fun slot(cx: Int, cz: Int): Int = Math.floorMod(cz, SIZE) * SIZE + Math.floorMod(cx, SIZE)

	fun affectedRegions(cx: Int, cz: Int): Set<Long> {
		val out = HashSet<Long>(4)
		for (dx in -1..1) for (dz in -1..1) out.add(SurfaceIndex.key(regionOf(cx + dx), regionOf(cz + dz)))
		return out
	}
}

object SurfaceDrawRuns {
	data class Run(val firstVertex: Int, val vertexCount: Int)

	fun runs(start: IntArray, count: IntArray, skip: (slot: Int) -> Boolean): List<Run> {
		val out = ArrayList<Run>()
		var runStart = -1
		var runEnd = -1
		for (s in start.indices) {
			if (count[s] == 0) continue
			if (skip(s)) continue
			if (runStart >= 0 && start[s] == runEnd) {
				runEnd += count[s]
			} else {
				if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
				runStart = start[s]
				runEnd = start[s] + count[s]
			}
		}
		if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
		return out
	}

	class Bucketed(val runs: List<Run>, val culledFaces: Int, val offScreenFaces: Int = 0)

	fun bucketRuns(start: IntArray, count: IntArray, slots: Int, bucketMask: Int, skipSlot: BooleanArray): Bucketed {
		val out = ArrayList<Run>()
		var culled = 0
		var runStart = -1
		var runEnd = -1
		for (i in start.indices) {
			val n = count[i]
			if (n == 0) continue
			val slot = i % slots
			if (skipSlot[slot]) continue
			if ((bucketMask ushr (i / slots)) and 1 == 0) { culled += n / 4; continue }
			if (runStart >= 0 && start[i] == runEnd) {
				runEnd += n
			} else {
				if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
				runStart = start[i]
				runEnd = start[i] + n
			}
		}
		if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
		return Bucketed(out, culled)
	}

	fun bucketRuns(
		start: IntArray, count: IntArray, slots: Int, bucketMask: Int, skipSlot: BooleanArray,
		offScreen: BooleanArray, bridgeVertices: Int,
	): Bucketed {
		val out = ArrayList<Run>()
		var culled = 0
		var offFaces = 0
		var runStart = -1
		var runEnd = -1

		var gapOpen = false
		var gapEnd = 0
		var gapVertices = 0
		for (i in start.indices) {
			val n = count[i]
			if (n == 0) continue
			val slot = i % slots
			if (skipSlot[slot]) { gapOpen = false; continue }
			if ((bucketMask ushr (i / slots)) and 1 == 0) { culled += n / 4; gapOpen = false; continue }
			if (offScreen[slot]) {
				offFaces += n / 4
				if (runStart >= 0) {
					if (!gapOpen && start[i] == runEnd) { gapOpen = true; gapEnd = start[i] + n; gapVertices = n }
					else if (gapOpen && start[i] == gapEnd) { gapEnd += n; gapVertices += n }
					else gapOpen = false
				}
				continue
			}
			if (runStart >= 0 && start[i] == runEnd) {
				runEnd += n
			} else if (runStart >= 0 && gapOpen && start[i] == gapEnd && gapVertices <= bridgeVertices) {

				offFaces -= gapVertices / 4
				runEnd = start[i] + n
			} else {
				if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
				runStart = start[i]
				runEnd = start[i] + n
			}
			gapOpen = false
		}
		if (runStart >= 0) out.add(Run(runStart, runEnd - runStart))
		return Bucketed(out, culled, offFaces)
	}

	fun layerRuns(start: IntArray, count: IntArray, bucketMask: Int, skipSlot: BooleanArray, offScreen: BooleanArray?): Bucketed =
		if (offScreen == null) bucketRuns(start, count, SurfaceRegions.SLOTS, bucketMask, skipSlot)
		else bucketRuns(start, count, SurfaceRegions.SLOTS, bucketMask, skipSlot, offScreen, BRIDGE_VERTICES)

	const val BRIDGE_VERTICES = 2048
}

interface SurfaceIndexStorage {
	fun hasStorage(indexCount: Int): Boolean
	fun ensure(indexCount: Int)
}

object SurfaceIndexPlan {

	fun indicesFor(vertexCount: Int): Int = vertexCount / 4 * 6

	fun maxIndices(runs: Iterable<SurfaceDrawRuns.Run>): Int = runs.maxOfOrNull { indicesFor(it.vertexCount) } ?: 0

	fun prepare(storage: SurfaceIndexStorage, maxIndices: Int) {
		if (maxIndices > 0 && !storage.hasStorage(maxIndices)) storage.ensure(maxIndices)
	}

	fun drawable(storage: SurfaceIndexStorage, runs: List<SurfaceDrawRuns.Run>): List<SurfaceDrawRuns.Run> =
		runs.filter { val n = indicesFor(it.vertexCount); n > 0 && storage.hasStorage(n) }
}

object SurfaceBudget {
	class Entry(val key: Long, val distSq: Long, val bytes: Long)

	fun toDrop(entries: List<Entry>, maxCount: Int, maxBytes: Long): List<Long> {
		val sorted = entries.sortedBy { it.distSq }
		val drop = ArrayList<Long>()
		var bytes = 0L
		for ((i, e) in sorted.withIndex()) {
			bytes += e.bytes
			if (i > 0 && (i >= maxCount || bytes > maxBytes)) drop.add(e.key)
		}
		return drop
	}
}
