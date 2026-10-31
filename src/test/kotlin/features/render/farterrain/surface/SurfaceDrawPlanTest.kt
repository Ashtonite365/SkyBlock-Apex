// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawMode
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawPlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndexPlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegionDrawCache
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import com.mojang.renderpearl.api.device.DeviceFeatures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.random.Random

class SurfaceDrawPlanTest {
	private val slots = SurfaceRegions.SLOTS

	private fun layer(rnd: Random, fill: Double = 0.6): Pair<IntArray, IntArray> {
		val count = IntArray(SurfaceFaceCull.BUCKETS * slots) { if (rnd.nextDouble() < fill) rnd.nextInt(1, 40) * 4 else 0 }
		val start = IntArray(count.size)
		var a = 0
		for (i in count.indices) { start[i] = a; a += count[i] }
		return start to count
	}

	private fun skipOf(rnd: Random): BooleanArray {
		val p = rnd.nextDouble()
		return BooleanArray(slots) { rnd.nextDouble() < p }
	}

	private fun oldDraws(start: IntArray, count: IntArray, mask: Int, skip: BooleanArray): List<Pair<Int, Int>> =
		SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip).runs
			.map { (it.vertexCount / 4 * 6) to it.firstVertex }.filter { it.first != 0 }

	private fun newDraws(p: SurfaceDrawPlan): List<Pair<Int, Int>> {
		assertEquals(0, p.indexCounts.position()); assertEquals(0, p.baseVertices.position())
		assertEquals(p.drawCount, p.indexCounts.limit()); assertEquals(p.drawCount, p.baseVertices.limit())
		assertTrue(p.indexCounts.isDirect && p.baseVertices.isDirect)
		return (0 until p.drawCount).map { p.indexCounts.get(it) to p.baseVertices.get(it) }
	}

	@Test
	fun multiDrawArraysEqualOldPerRunDrawsForRandomMasks() {
		val rnd = Random(1025)
		val plan = SurfaceDrawPlan()
		repeat(3000) {
			val (start, count) = layer(rnd, rnd.nextDouble())
			val skip = skipOf(rnd)
			val mask = if (rnd.nextInt(4) == 0) SurfaceFaceCull.ALL else rnd.nextInt(SurfaceFaceCull.ALL + 1)
			val old = SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip)
			plan.rebuild(start, count, slots, mask, SurfaceDrawPlan.maskOf(skip))
			assertEquals(old.runs, plan.runs())
			assertEquals(old.culledFaces, plan.culledFaces)
			assertEquals(SurfaceIndexPlan.maxIndices(old.runs), plan.maxIndices)
			assertEquals(oldDraws(start, count, mask, skip), newDraws(plan))
			assertEquals(old.runs.sumOf { it.vertexCount.toLong() }, plan.drawnVertices)
		}
	}

	@Test
	fun zeroVertexRunsAreLeftOutOfTheSubmissionLikeBefore() {

		val count = IntArray(slots); count[0] = 2; count[5] = 8
		val start = IntArray(slots).also { var a = 0; for (i in it.indices) { it[i] = a; a += count[i] } }
		val skip = BooleanArray(slots).also { it[1] = true }
		val p = SurfaceDrawPlan().apply { rebuild(start, count, slots, 1, SurfaceDrawPlan.maskOf(skip)) }
		assertEquals(1, p.runCount)
		assertEquals(oldDraws(start, count, 1, skip), newDraws(p))
	}

	@Test
	fun maskOfAndSlotMaskMatchTheOldPerSlotChecks() {
		val rnd = Random(7)
		repeat(500) {
			val skip = skipOf(rnd)
			val m = SurfaceDrawPlan.maskOf(skip)
			for (s in 0 until slots) assertEquals(skip[s], (m ushr s) and 1L != 0L)
			val layers = List(rnd.nextInt(1, 4)) { layer(rnd, rnd.nextDouble() * 0.05).second }
			val mask = SurfaceDrawPlan.slotMask(layers)
			for (s in 0 until slots) {

				var old = false
				for (c in layers) { var i = s; while (i < c.size) { if (c[i] > 0) old = true; i += slots } }
				assertEquals(old, (mask ushr s) and 1L != 0L)
			}
		}
	}

	@Test
	fun cacheHitsOnlyForTheSameKey() {
		val rnd = Random(3)
		val (start, count) = layer(rnd)
		val c = SurfaceRegionDrawCache(uploadId = 1, layers = 3)
		val p1 = c.plan(0, start, count, slots, 0b1010101, 5L)
		assertEquals(1, c.misses); assertEquals(0, c.hits)
		assertSame(p1, c.plan(0, start, count, slots, 0b1010101, 5L))
		assertEquals(1, c.hits)

		c.plan(0, start, count, slots, 0b1010101, 7L); assertEquals(2, c.misses)

		c.plan(0, start, count, slots, 0b0101010, 7L); assertEquals(3, c.misses)

		val all = c.plan(0, start, count, slots, SurfaceFaceCull.ALL, 7L); assertEquals(4, c.misses)
		assertEquals(SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, BooleanArray(slots) { (7L ushr it) and 1L != 0L }).runs, all.runs())

		c.plan(0, start, count, slots, 0b0101010, 7L); assertEquals(5, c.misses)

		c.plan(1, start, count, slots, 0b0101010, 7L); assertEquals(6, c.misses)
		c.plan(0, start, count, slots, 0b0101010, 7L); assertEquals(6, c.misses)

		c.invalidate()
		c.plan(0, start, count, slots, 0b0101010, 7L); assertEquals(7, c.misses)
	}

	@Test
	fun aNewUploadStartsWithAnEmptyCache() {

		val rnd = Random(4)
		val (s1, c1) = layer(rnd)
		val (s2, c2) = layer(rnd)
		val a = SurfaceRegionDrawCache(1, 3)
		a.plan(0, s1, c1, slots, SurfaceFaceCull.ALL, 0L)
		val b = SurfaceRegionDrawCache(2, 3)
		val p = b.plan(0, s2, c2, slots, SurfaceFaceCull.ALL, 0L)
		assertEquals(1, b.misses)
		assertEquals(SurfaceDrawRuns.bucketRuns(s2, c2, slots, SurfaceFaceCull.ALL, BooleanArray(slots)).runs, p.runs())
	}

	@Test
	fun cachedPathDoesNotAllocate() {
		val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
		if (!bean.isThreadAllocatedMemorySupported) return
		bean.isThreadAllocatedMemoryEnabled = true
		val rnd = Random(5)
		val (start, count) = layer(rnd)
		val skip = skipOf(rnd)
		val caches = Array(64) { SurfaceRegionDrawCache(it.toLong(), 3) }
		val tid = Thread.currentThread().threadId()
		var before = 0L
		for (f in 0 until 3000) {
			if (f == 2000) before = bean.getThreadAllocatedBytes(tid)
			val m = SurfaceDrawPlan.maskOf(skip)
			for (i in caches.indices) for (l in 0 until 3) caches[i].plan(l, start, count, slots, 0b1110001, m)
		}
		val used = bean.getThreadAllocatedBytes(tid) - before
		assertTrue(used < 4096, "cached frames allocated $used bytes")
	}

	private class Item(override val distSq: Double, val id: Int) : SurfaceDrawOrder.Item

	@Test
	fun drawOrderEqualsSortedByAndSortedByDescending() {
		val rnd = Random(6)
		val scratch = ArrayList<Item>()
		val out = ArrayList<Item>()
		repeat(400) {
			val n = rnd.nextInt(0, 300)

			val items = List(n) { Item(rnd.nextInt(0, 12).toDouble() * 64.0, it) }
			val asc = ArrayList(items)
			SurfaceDrawOrder.sortAscending(asc, scratch)
			assertEquals(items.sortedBy { it.distSq }.map { it.id }, asc.map { it.id })
			SurfaceDrawOrder.descendingOf(asc, out)
			assertEquals(items.sortedByDescending { it.distSq }.map { it.id }, out.map { it.id })
		}
	}

	@Test
	fun multiDrawOnlyWhereTheDeviceHasIt() {

		assertTrue(SurfaceDrawMode.multiDrawAllowed(deviceHasMultiDraw = true, failedBefore = false))
		assertFalse(SurfaceDrawMode.multiDrawAllowed(deviceHasMultiDraw = false, failedBefore = false))
		assertFalse(SurfaceDrawMode.multiDrawAllowed(deviceHasMultiDraw = true, failedBefore = true))
		assertEquals(SurfaceDrawMode.Mode.MULTI_DRAW, SurfaceDrawMode.select(true, true))
		assertEquals(SurfaceDrawMode.Mode.PER_RANGE, SurfaceDrawMode.select(false, true))
		assertEquals(SurfaceDrawMode.Mode.PER_RANGE, SurfaceDrawMode.select(true, false))
		assertEquals(SurfaceDrawMode.Mode.PER_RANGE, SurfaceDrawMode.select(false, false))
	}

	private fun features(separate: Boolean, others: Boolean) =
		DeviceFeatures(others, others, others, separate, others, others, others, others)

	@Test
	fun backendFeatureCheckSelectsTheFallbackWhenUnsupported() {

		assertFalse(SurfaceDrawMode.deviceHasMultiDraw { features(separate = false, others = true) })
		assertFalse(SurfaceDrawMode.deviceHasMultiDraw { features(separate = false, others = false) })
		assertTrue(features(separate = true, others = false).multiDrawDirectSeparate())
		assertTrue(SurfaceDrawMode.deviceHasMultiDraw { features(separate = true, others = false) })

		assertFalse(SurfaceDrawMode.deviceHasMultiDraw { DeviceFeatures(true, true, true, false, true, true, true, true) })

		assertFalse(SurfaceDrawMode.deviceHasMultiDraw { null })
		assertFalse(SurfaceDrawMode.deviceHasMultiDraw { throw IllegalStateException("no device") })

		for (supported in listOf(false, true)) {
			val allowed = SurfaceDrawMode.multiDrawAllowed(SurfaceDrawMode.deviceHasMultiDraw { features(supported, !supported) }, failedBefore = false)
			assertEquals(if (supported) SurfaceDrawMode.Mode.MULTI_DRAW else SurfaceDrawMode.Mode.PER_RANGE, SurfaceDrawMode.select(allowed, true))
		}
	}

	@Test
	fun hubSimDrawCallEstimate() {
		val rnd = Random(2026)
		val rd = 12
		val far = 64
		var oldCalls = 0
		var newCalls = 0
		var runs = 0
		var regionsDrawn = 0
		val rr = far / SurfaceRegions.SIZE + 1
		for (rx in -rr..rr) for (rz in -rr..rr) {
			val cxMid = rx * 8 + 4
			val czMid = rz * 8 + 4
			if (cxMid * cxMid + czMid * czMid > (far + 8) * (far + 8)) continue

			val ang = Math.toDegrees(Math.atan2(czMid.toDouble(), cxMid.toDouble()))
			if (Math.abs(ang) > 60 && (Math.abs(rx) > 1 || Math.abs(rz) > 1)) continue
			val skip = BooleanArray(slots) { s ->
				val cx = rx * 8 + s % 8
				val cz = rz * 8 + s / 8
				val d2 = cx * cx + cz * cz
				d2 > far * far || (d2 <= rd * rd && rnd.nextDouble() < 0.97)
			}
			if (skip.all { it }) continue
			regionsDrawn++

			val mask = (if (cxMid > 0) 1 shl 1 else 1 shl 0) or (if (czMid > 0) 1 shl 5 else 1 shl 4) or (1 shl 2) or (1 shl 6)
			for (layerFill in doubleArrayOf(0.95, 0.5, 0.15)) {
				val (start, count) = layer(rnd, layerFill)
				val p = SurfaceDrawPlan().apply { rebuild(start, count, slots, mask, SurfaceDrawPlan.maskOf(skip)) }
				oldCalls += p.drawCount
				runs += p.drawCount
				if (p.drawCount > 0) newCalls++
			}
		}
		println("Hub sim: $regionsDrawn regions, $runs ranges: draw calls $oldCalls per range -> $newCalls multi-draws " +
			"(%.1fx fewer)".format(oldCalls.toDouble() / newCalls))
		assertTrue(newCalls < oldCalls)
		assertTrue(newCalls <= regionsDrawn * 3)
	}
}
