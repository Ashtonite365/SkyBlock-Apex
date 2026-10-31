/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, upload smoothing (pure Kotlin, unit-tested).
 *
 * Finished region meshes wait here (render thread) and go to the GPU a few per frame. Each frame:
 *  1. removals (a null mesh) and meshes of an old generation are handled at once: they cost no upload;
 *  2. urgent meshes: regions within [Budget.urgentReachChunks] of the camera (where the hand-off may need far
 *     terrain's copy of a chunk the game stops drawing, so they must never wait behind smoothing), up to
 *     [Budget.urgentMax] per frame (the old fixed per-frame count), nearest first, outside the byte / time budget;
 *  3. the rest within the frame's budget ([Budget.maxBytes], [Budget.maxNanos], [Budget.maxCount]), at least one per
 *     frame, best first: waited [Budget.ageFrames] frames or more (oldest first), then regions not on the GPU yet
 *     (they fill empty space; a re-mesh's old copy stays drawn meanwhile), then nearest first.
 * A newer mesh for a region replaces a waiting older one (the older is closed): only the latest is ever uploaded.
 * Every entry gets uploaded: one per frame at least, and anything left waiting long enough comes first.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfaceUploadQueue<M : Any>(private val close: (M) -> Unit) {
	class Budget(
		val maxBytes: Long = 4L shl 20,
		val maxNanos: Long = 1_000_000L,
		val maxCount: Int = 8,
		val urgentMax: Int = 8,
		val ageFrames: Int = 90,
		val urgentReachChunks: Int = 0,
	)

	class Entry<M>(val key: Long, val rx: Int, val rz: Int) {
		var mesh: M? = null
			internal set
		var generation = 0
			internal set
		var bytes = 0L
			internal set
		var since = 0L
			internal set
	}

	enum class Kind { URGENT, BUDGET }

	private val byKey = HashMap<Long, Entry<M>>()
	private class Removal(val key: Long, val generation: Int)
	private val removals = ArrayList<Removal>()

	val size: Int get() = byKey.size
	val pendingBytes: Long get() = byKey.values.sumOf { it.bytes }

	fun offer(key: Long, rx: Int, rz: Int, mesh: M?, generation: Int, bytes: Long, frame: Long) {
		if (mesh == null) {

			byKey.remove(key)?.mesh?.let(close)
			removals.add(Removal(key, generation))
			return
		}
		removals.removeIf { it.key == key }
		val e = byKey.getOrPut(key) { Entry(key, rx, rz) }
		e.mesh?.let { old -> if (old !== mesh) close(old) } ?: run { e.since = frame }
		e.mesh = mesh
		e.generation = generation
		e.bytes = bytes
	}

	fun dropOtherGenerations(generation: Int) {
		val it = byKey.values.iterator()
		while (it.hasNext()) { val e = it.next(); if (e.generation != generation) { e.mesh?.let(close); it.remove() } }
	}

	fun closeAll() {
		for (e in byKey.values) e.mesh?.let(close)
		byKey.clear()
		removals.clear()
	}

	fun drain(
		frame: Long, generation: Int, ccx: Int, ccz: Int, budget: Budget,
		resident: (Long) -> Boolean, clock: () -> Long,
		remove: (Long) -> Unit, upload: (Entry<M>, Kind) -> Long,
	): Int {

		for (r in removals) if (r.generation == generation) remove(r.key)
		removals.clear()
		if (byKey.isEmpty()) return 0
		dropOtherGenerations(generation)
		val reach = budget.urgentReachChunks.toLong() + URGENT_SLACK
		val reachSq = if (budget.urgentReachChunks <= 0) -1L else reach * reach
		var n = 0
		val t0 = clock()
		var bytes = 0L

		var urgent = 0
		while (urgent < budget.urgentMax) {
			val e = best(frame, ccx, ccz, budget, resident, reachSq, urgentOnly = true) ?: break
			bytes += take(e, Kind.URGENT, upload)
			urgent++; n++
		}

		var budgeted = 0
		while (budgeted < budget.maxCount) {
			val e = best(frame, ccx, ccz, budget, resident, reachSq, urgentOnly = false) ?: break
			if (budgeted > 0 && (bytes + e.bytes > budget.maxBytes || clock() - t0 >= budget.maxNanos)) break
			bytes += take(e, Kind.BUDGET, upload)
			budgeted++; n++
		}
		return n
	}

	private fun take(e: Entry<M>, kind: Kind, upload: (Entry<M>, Kind) -> Long): Long {
		byKey.remove(e.key)
		return try { upload(e, kind) } finally { e.mesh = null }
	}

	private fun best(frame: Long, ccx: Int, ccz: Int, budget: Budget, resident: (Long) -> Boolean, reachSq: Long, urgentOnly: Boolean): Entry<M>? {
		var best: Entry<M>? = null
		var bestAged = false
		var bestResident = false
		var bestDist = Long.MAX_VALUE
		var bestSince = Long.MAX_VALUE
		for (e in byKey.values) {
			val dist = distSq(e.rx, e.rz, ccx, ccz)
			if (urgentOnly && dist > reachSq) continue
			val aged = frame - e.since >= budget.ageFrames
			val res = resident(e.key)
			val better = when {
				best == null -> true
				urgentOnly -> dist < bestDist
				aged != bestAged -> aged
				aged -> e.since < bestSince || (e.since == bestSince && dist < bestDist)
				res != bestResident -> !res
				else -> dist < bestDist
			}
			if (better) { best = e; bestAged = aged; bestResident = res; bestDist = dist; bestSince = e.since }
		}
		return best
	}

	companion object {

		const val URGENT_SLACK = 6L

		fun distSq(rx: Int, rz: Int, ccx: Int, ccz: Int): Long {
			val dx = (rx * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - ccx).toLong()
			val dz = (rz * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - ccz).toLong()
			return dx * dx + dz * dz
		}
	}
}
