/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, cached per-region draw decisions (pure Kotlin, unit-tested).
 *
 * A region layer's draw ranges only change when one of these changes: its mesh (a new upload: a new
 * [SurfaceRegionDrawCache], its upload id), the face directions block face culling keeps ([SurfaceFaceCull.visibleMask],
 * or [SurfaceFaceCull.ALL] with the setting off) or the chunk slots left out this frame, as two 64-bit masks: the
 * skip mask (the hand-off and the distance: never drawn) and the off-screen mask (per-chunk frustum culling,
 * SurfaceChunkFrustum: chunks wholly off screen, left out but short stretches of them drawn through, see
 * [SurfaceDrawRuns.bucketRuns]). [SurfaceDrawPlan] keeps the runs for the last (face mask, skip mask, off-screen mask)
 * together with the arrays one multi-draw submission takes (index count and base vertex per run, in direct buffers that
 * are reused), so an unchanged region costs three compares per layer and frame.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import com.mojang.renderpearl.api.device.DeviceFeatures
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class SurfaceDrawPlan {

	var bucketMask = 0
		private set
	var skipMask = 0L
		private set

	var offScreenMask = 0L
		private set
	var valid = false
		private set

	var runCount = 0
		private set
	var firstVertex = IntArray(8)
		private set
	var vertexCount = IntArray(8)
		private set

	var culledFaces = 0
		private set

	var offScreenFaces = 0
		private set

	var maxIndices = 0
		private set

	var drawCount = 0
		private set
	var indexCounts: IntBuffer = directInts(8)
		private set
	var baseVertices: IntBuffer = directInts(8)
		private set

	var drawnVertices = 0L
		private set

	fun invalidate() { valid = false }

	fun matches(bucketMask: Int, skipMask: Long, offScreenMask: Long = 0L): Boolean =
		valid && this.bucketMask == bucketMask && this.skipMask == skipMask && this.offScreenMask == offScreenMask

	fun rebuild(
		start: IntArray, count: IntArray, slots: Int, bucketMask: Int, skipMask: Long,
		offScreenMask: Long = 0L, bridgeVertices: Int = SurfaceDrawRuns.BRIDGE_VERTICES,
	) {
		var n = 0
		var culled = 0
		var offFaces = 0
		var runStart = -1
		var runEnd = -1

		var gapOpen = false
		var gapEnd = 0
		var gapVertices = 0
		for (i in start.indices) {
			val c = count[i]
			if (c == 0) continue
			val slot = i % slots
			if ((skipMask ushr slot) and 1L != 0L) { gapOpen = false; continue }
			if ((bucketMask ushr (i / slots)) and 1 == 0) { culled += c / 4; gapOpen = false; continue }
			if ((offScreenMask ushr slot) and 1L != 0L) {
				offFaces += c / 4
				if (runStart >= 0) {
					if (!gapOpen && start[i] == runEnd) { gapOpen = true; gapEnd = start[i] + c; gapVertices = c }
					else if (gapOpen && start[i] == gapEnd) { gapEnd += c; gapVertices += c }
					else gapOpen = false
				}
				continue
			}
			if (runStart >= 0 && start[i] == runEnd) {
				runEnd += c
			} else if (runStart >= 0 && gapOpen && start[i] == gapEnd && gapVertices <= bridgeVertices) {

				offFaces -= gapVertices / 4
				runEnd = start[i] + c
			} else {
				if (runStart >= 0) { add(n, runStart, runEnd - runStart); n++ }
				runStart = start[i]
				runEnd = start[i] + c
			}
			gapOpen = false
		}
		if (runStart >= 0) { add(n, runStart, runEnd - runStart); n++ }
		runCount = n
		culledFaces = culled
		offScreenFaces = offFaces

		if (indexCounts.capacity() < n) { indexCounts = directInts(firstVertex.size); baseVertices = directInts(firstVertex.size) }
		indexCounts.clear(); baseVertices.clear()
		var d = 0
		var maxIdx = 0
		var verts = 0L
		for (r in 0 until n) {
			val idx = SurfaceIndexPlan.indicesFor(vertexCount[r])
			if (idx > maxIdx) maxIdx = idx
			if (idx == 0) continue
			indexCounts.put(d, idx)
			baseVertices.put(d, firstVertex[r])
			verts += vertexCount[r]
			d++
		}
		drawCount = d
		indexCounts.limit(d)
		baseVertices.limit(d)
		maxIndices = maxIdx
		drawnVertices = verts
		this.bucketMask = bucketMask
		this.skipMask = skipMask
		this.offScreenMask = offScreenMask
		valid = true
	}

	private fun add(i: Int, first: Int, n: Int) {
		if (i == firstVertex.size) {
			firstVertex = firstVertex.copyOf(i * 2)
			vertexCount = vertexCount.copyOf(i * 2)
		}
		firstVertex[i] = first
		vertexCount[i] = n
	}

	fun runs(): List<SurfaceDrawRuns.Run> = List(runCount) { SurfaceDrawRuns.Run(firstVertex[it], vertexCount[it]) }

	companion object {
		fun directInts(n: Int): IntBuffer = ByteBuffer.allocateDirect(n.coerceAtLeast(1) * 4).order(ByteOrder.nativeOrder()).asIntBuffer()

		fun maskOf(skipSlot: BooleanArray): Long {
			var m = 0L
			for (s in 0 until minOf(64, skipSlot.size)) if (skipSlot[s]) m = m or (1L shl s)
			return m
		}

		fun slotMask(counts: Iterable<IntArray>, slots: Int = SurfaceRegions.SLOTS): Long {
			var m = 0L
			for (c in counts) for (i in c.indices) if (c[i] > 0) m = m or (1L shl (i % slots))
			return m
		}
	}
}

class SurfaceRegionDrawCache(val uploadId: Long, layers: Int) {
	private val plans = arrayOfNulls<SurfaceDrawPlan>(layers)
	var hits = 0L
		private set
	var misses = 0L
		private set

	fun plan(layer: Int, start: IntArray, count: IntArray, slots: Int, bucketMask: Int, skipMask: Long, offScreenMask: Long = 0L): SurfaceDrawPlan {
		val p = plans[layer] ?: SurfaceDrawPlan().also { plans[layer] = it }
		if (p.matches(bucketMask, skipMask, offScreenMask)) { hits++; return p }
		misses++
		p.rebuild(start, count, slots, bucketMask, skipMask, offScreenMask)
		return p
	}

	fun invalidate() { for (p in plans) p?.invalidate() }
}

object SurfaceDrawOrder {
	interface Item { val distSq: Double }

	fun <T : Item> sortAscending(list: MutableList<T>, scratch: MutableList<T>) {
		val n = list.size
		if (n <= INSERTION_MAX) { insertion(list, 0, n); return }
		var w = INSERTION_MAX
		var lo = 0
		while (lo < n) { insertion(list, lo, minOf(lo + w, n)); lo += w }
		while (scratch.size < n) scratch.add(list[0])
		var src = list
		var dst = scratch
		while (w < n) {
			lo = 0
			while (lo < n) {
				val mid = minOf(lo + w, n)
				val hi = minOf(lo + 2 * w, n)
				var i = lo
				var j = mid
				var k = lo
				while (i < mid && j < hi) dst[k++] = if (src[j].distSq < src[i].distSq) src[j++] else src[i++]
				while (i < mid) dst[k++] = src[i++]
				while (j < hi) dst[k++] = src[j++]
				lo = hi
			}
			val t = src; src = dst; dst = t
			w *= 2
		}
		if (src !== list) for (i in 0 until n) list[i] = src[i]
	}

	private fun <T : Item> insertion(list: MutableList<T>, from: Int, to: Int) {
		for (i in from + 1 until to) {
			val x = list[i]
			var j = i - 1
			while (j >= from && list[j].distSq > x.distSq) { list[j + 1] = list[j]; j-- }
			list[j + 1] = x
		}
	}

	private const val INSERTION_MAX = 16

	fun <T : Item> descendingOf(ascending: List<T>, out: MutableList<T>) {
		out.clear()
		var end = ascending.size
		while (end > 0) {
			var begin = end - 1
			val d = ascending[begin].distSq
			while (begin > 0 && ascending[begin - 1].distSq == d) begin--
			for (i in begin until end) out.add(ascending[i])
			end = begin
		}
	}
}

object SurfaceDrawMode {
	enum class Mode { MULTI_DRAW, PER_RANGE }

	fun deviceHasMultiDraw(features: () -> DeviceFeatures?): Boolean =
		try { features()?.multiDrawDirectSeparate() == true } catch (_: Throwable) { false }

	fun multiDrawAllowed(deviceHasMultiDraw: Boolean, failedBefore: Boolean): Boolean = deviceHasMultiDraw && !failedBefore

	fun select(multiDrawAllowed: Boolean, indexBufferHoldsLargest: Boolean): Mode =
		if (multiDrawAllowed && indexBufferHoldsLargest) Mode.MULTI_DRAW else Mode.PER_RANGE
}
