/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, one-time re-mesh of every region when a setting its meshes depend on changes
 * ("Connected textures in far terrain"). Regions are handed to the mesh worker a few at a time, nearest first, and only
 * while the worker's mesh queue is short, so a toggle never re-meshes everything in one frame and never floods the
 * worker. The old meshes stay drawn until their replacements arrive.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfaceRemeshAll(private val maxQueued: Int = 2, private val perTick: Int = 1) {
	private var lastSignature = Int.MIN_VALUE
	private val pending = ArrayDeque<Long>()

	fun changed(signature: Int): Boolean {
		if (lastSignature == Int.MIN_VALUE) { lastSignature = signature; return false }
		if (signature == lastSignature) return false
		lastSignature = signature
		return true
	}

	@Synchronized fun replace(regions: List<Long>) { pending.clear(); pending.addAll(regions) }

	@Synchronized fun clear() = pending.clear()

	val remaining: Int @Synchronized get() = pending.size

	@Synchronized
	fun take(queued: Int): LongArray {
		val n = minOf(perTick, maxQueued - queued, pending.size)
		if (n <= 0) return EMPTY
		val out = LongArray(n)
		for (i in 0 until n) out[i] = pending.removeFirst()
		return out
	}

	companion object {
		private val EMPTY = LongArray(0)

		fun regionsNearestFirst(chunkKeys: Collection<Long>, pcx: Int, pcz: Int): List<Long> {
			val regions = LinkedHashSet<Long>()
			for (k in chunkKeys) regions.add(SurfaceIndex.key(SurfaceRegions.regionOf(SurfaceIndex.keyX(k)), SurfaceRegions.regionOf(SurfaceIndex.keyZ(k))))
			return regions.sortedBy { distSq(SurfaceIndex.keyX(it), SurfaceIndex.keyZ(it), pcx, pcz) }
		}

		private fun distSq(rx: Int, rz: Int, pcx: Int, pcz: Int): Long {
			val dx = (rx * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - pcx).toLong()
			val dz = (rz * SurfaceRegions.SIZE + SurfaceRegions.SIZE / 2 - pcz).toLong()
			return dx * dx + dz * dz
		}
	}
}
