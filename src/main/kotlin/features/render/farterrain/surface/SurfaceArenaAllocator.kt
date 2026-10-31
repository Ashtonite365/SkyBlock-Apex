/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, sub-allocator for the shared far-terrain vertex buffer (pure Kotlin, unit-tested).
 *
 * Hands out ranges of a linear space measured in units (far terrain: vertices). Free space is a list of
 * [start, length) blocks kept sorted by start in two plain int arrays; allocation takes the smallest block that fits
 * (best fit, which keeps big blocks for big meshes), freeing merges a block with its free neighbours on both sides, so
 * the list never holds two touching blocks. Growing the space adds to the end (merged with a free tail). No objects
 * per allocation and none per call: the caller keeps (offset, size). Written for Apex from the textbook free-list
 * technique; not thread-safe (render thread only).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfaceArenaAllocator(capacity: Int = 0) {

	var capacity = capacity
		private set

	var used = 0
		private set

	var liveRanges = 0
		private set

	private var freeStart = IntArray(16)
	private var freeLen = IntArray(16)

	var freeBlocks = 0
		private set

	init {
		require(capacity >= 0)
		if (capacity > 0) { freeStart[0] = 0; freeLen[0] = capacity; freeBlocks = 1 }
	}

	val freeUnits: Int get() = capacity - used

	fun alloc(size: Int): Int {
		require(size > 0) { "size $size" }
		var best = -1
		var bestLen = Int.MAX_VALUE
		for (i in 0 until freeBlocks) {
			val l = freeLen[i]
			if (l >= size && l < bestLen) {
				best = i; bestLen = l
				if (l == size) break
			}
		}
		if (best < 0) return -1
		val start = freeStart[best]
		if (bestLen == size) removeAt(best) else { freeStart[best] = start + size; freeLen[best] = bestLen - size }
		used += size
		liveRanges++
		return start
	}

	fun free(start: Int, size: Int) {
		require(size > 0 && start >= 0 && start.toLong() + size <= capacity) { "free $start+$size of $capacity" }

		var lo = 0
		var hi = freeBlocks
		while (lo < hi) {
			val m = (lo + hi) ushr 1
			if (freeStart[m] < start) lo = m + 1 else hi = m
		}
		check(lo == 0 || freeStart[lo - 1] + freeLen[lo - 1] <= start) { "double free at $start" }
		check(lo == freeBlocks || start + size <= freeStart[lo]) { "double free at $start+$size" }
		val joinPrev = lo > 0 && freeStart[lo - 1] + freeLen[lo - 1] == start
		val joinNext = lo < freeBlocks && start + size == freeStart[lo]
		when {
			joinPrev && joinNext -> { freeLen[lo - 1] += size + freeLen[lo]; removeAt(lo) }
			joinPrev -> freeLen[lo - 1] += size
			joinNext -> { freeStart[lo] = start; freeLen[lo] += size }
			else -> insertAt(lo, start, size)
		}
		used -= size
		liveRanges--
	}

	fun grow(newCapacity: Int) {
		require(newCapacity >= capacity) { "shrink $capacity -> $newCapacity" }
		val add = newCapacity - capacity
		if (add == 0) return
		if (freeBlocks > 0 && freeStart[freeBlocks - 1] + freeLen[freeBlocks - 1] == capacity) freeLen[freeBlocks - 1] += add
		else insertAt(freeBlocks, capacity, add)
		capacity = newCapacity
	}

	fun clear() {
		used = 0; liveRanges = 0
		freeBlocks = 0
		if (capacity > 0) { freeStart[0] = 0; freeLen[0] = capacity; freeBlocks = 1 }
	}

	fun largestFree(): Int {
		var m = 0
		for (i in 0 until freeBlocks) if (freeLen[i] > m) m = freeLen[i]
		return m
	}

	fun highWater(): Int =
		if (freeBlocks > 0 && freeStart[freeBlocks - 1] + freeLen[freeBlocks - 1] == capacity) freeStart[freeBlocks - 1] else capacity

	fun fragmentation(): Double {
		val free = freeUnits
		return if (free <= 0) 0.0 else 1.0 - largestFree().toDouble() / free
	}

	private fun removeAt(i: Int) {
		System.arraycopy(freeStart, i + 1, freeStart, i, freeBlocks - i - 1)
		System.arraycopy(freeLen, i + 1, freeLen, i, freeBlocks - i - 1)
		freeBlocks--
	}

	private fun insertAt(i: Int, start: Int, len: Int) {
		if (freeBlocks == freeStart.size) {
			freeStart = freeStart.copyOf(freeBlocks * 2)
			freeLen = freeLen.copyOf(freeBlocks * 2)
		}
		System.arraycopy(freeStart, i, freeStart, i + 1, freeBlocks - i)
		System.arraycopy(freeLen, i, freeLen, i + 1, freeBlocks - i)
		freeStart[i] = start
		freeLen[i] = len
		freeBlocks++
	}
}
