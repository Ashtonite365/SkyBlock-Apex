// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuiltWait
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunkFrustum
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawPlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnMode
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceGameArea
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffDecider
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGrid
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegionDrawCache
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceUploadQueue
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SurfaceFrustumMultiDrawHandoffTest {
	private val slots = SurfaceRegions.SLOTS
	private val size = SurfaceRegions.SIZE

	private class Region(val rx: Int, val rz: Int) {
		val start: IntArray
		val count: IntArray
		val slotMinY = IntArray(SurfaceRegions.SLOTS)
		val slotMaxY = IntArray(SurfaceRegions.SLOTS)
		val minY: Int
		val maxY: Int
		val cache = SurfaceRegionDrawCache(uploadId = SurfaceIndex.key(rx, rz), layers = 1)

		init {
			val rnd = Random(rx * 31 + rz * 1000003)
			count = IntArray(SurfaceFaceCull.BUCKETS * SurfaceRegions.SLOTS) { if (rnd.nextDouble() < 0.7) rnd.nextInt(1, 300) * 4 else 0 }
			start = IntArray(count.size)
			var a = 0
			for (i in count.indices) { start[i] = a; a += count[i] }
			for (s in 0 until SurfaceRegions.SLOTS) { slotMinY[s] = 40 + rnd.nextInt(30); slotMaxY[s] = slotMinY[s] + 1 + rnd.nextInt(60) }
			minY = slotMinY.min(); maxY = slotMaxY.max()
		}

		fun hasSlot(s: Int): Boolean { for (b in 0 until SurfaceFaceCull.BUCKETS) if (count[b * SurfaceRegions.SLOTS + s] > 0) return true; return false }
	}

	private fun drawnPerIndex(r: Region, ranges: List<Pair<Int, Int>>): IntArray {
		val out = IntArray(r.count.size)
		val sorted = ranges.sortedBy { it.first }
		var j = 0
		for (i in r.count.indices) {
			val s0 = r.start[i]; val s1 = s0 + r.count[i]
			if (s1 == s0) continue
			while (j < sorted.size && sorted[j].first + sorted[j].second <= s0) j++
			var k = j
			while (k < sorted.size && sorted[k].first < s1) {
				val lo = maxOf(sorted[k].first, s0); val hi = minOf(sorted[k].first + sorted[k].second, s1)
				if (hi > lo) out[i] += hi - lo
				k++
			}
		}
		return out
	}

	private fun multiDrawRanges(p: SurfaceDrawPlan): List<Pair<Int, Int>> =
		(0 until p.drawCount).map { p.baseVertices.get(it) to p.indexCounts.get(it) / 6 * 4 }

	private fun frustum(yaw: Float, pitch: Float = 0f): SurfaceChunkFrustum {
		val proj = Matrix4f().perspective(Math.toRadians(70.0).toFloat(), 16f / 9f, 0.05f, 4096f, true)
		val view = Matrix4f().rotationX(pitch).rotateY(yaw)
		return SurfaceChunkFrustum().also { assertTrue(it.set(proj, view)) }
	}

	private fun layer(rnd: Random): Pair<IntArray, IntArray> {
		val fill = rnd.nextDouble()
		val count = IntArray(SurfaceFaceCull.BUCKETS * slots) { if (rnd.nextDouble() < fill) rnd.nextInt(1, 600) * 4 else 0 }
		val start = IntArray(count.size)
		var a = 0
		for (i in count.indices) { start[i] = a; a += count[i] }
		return start to count
	}

	@Test
	fun planWithTheOffScreenMaskEqualsTheBridgedLayerRuns() {
		val rnd = Random(26)
		val plan = SurfaceDrawPlan()
		repeat(4000) {
			val (start, count) = layer(rnd)
			val p = rnd.nextDouble()
			val skip = BooleanArray(slots) { rnd.nextDouble() < p * 0.5 }
			val q = rnd.nextDouble()

			val off = BooleanArray(slots) { !skip[it] && rnd.nextDouble() < q }
			val mask = if (rnd.nextInt(3) == 0) SurfaceFaceCull.ALL else rnd.nextInt(SurfaceFaceCull.ALL + 1)
			val ref = SurfaceDrawRuns.layerRuns(start, count, mask, skip, off)
			plan.rebuild(start, count, slots, mask, SurfaceDrawPlan.maskOf(skip), SurfaceDrawPlan.maskOf(off))
			assertEquals(ref.runs, plan.runs())
			assertEquals(ref.culledFaces, plan.culledFaces)
			assertEquals(ref.offScreenFaces, plan.offScreenFaces)
			assertEquals(ref.runs.map { it.firstVertex to it.vertexCount }.filter { it.second >= 4 }, multiDrawRanges(plan))

			plan.rebuild(start, count, slots, mask, SurfaceDrawPlan.maskOf(skip), 0L)
			assertEquals(SurfaceDrawRuns.layerRuns(start, count, mask, skip, null).runs, plan.runs())
			assertEquals(SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip).runs, plan.runs())
			assertEquals(0, plan.offScreenFaces)
		}
	}

	@Test
	fun planCacheInvalidatesWhenTheFrustumSkipMaskChanges() {
		val (start, count) = layer(Random(7))
		val c = SurfaceRegionDrawCache(uploadId = 9, layers = 2)
		val skip = 0b1011L
		val p0 = c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, 0L)
		assertEquals(1, c.misses)
		assertSame(p0, c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, 0L)); assertEquals(1, c.hits)

		val off1 = (1L shl 20) or (1L shl 21) or (1L shl 40)
		val p1 = c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, off1)
		assertEquals(2, c.misses)
		assertEquals(SurfaceDrawRuns.layerRuns(start, count, SurfaceFaceCull.ALL, BooleanArray(slots) { (skip ushr it) and 1L != 0L },
			BooleanArray(slots) { (off1 ushr it) and 1L != 0L }).runs, p1.runs())

		c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, off1); assertEquals(2, c.misses); assertEquals(2, c.hits)

		c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, off1 or (1L shl 63)); assertEquals(3, c.misses)
		val back = c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, 0L); assertEquals(4, c.misses)
		assertEquals(SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, BooleanArray(slots) { (skip ushr it) and 1L != 0L }).runs, back.runs())

		c.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip or 1L.shl(5), 0L); assertEquals(5, c.misses)
		c.plan(0, start, count, slots, 0b0101010, skip or 1L.shl(5), 0L); assertEquals(6, c.misses)

		val fresh = SurfaceRegionDrawCache(uploadId = 10, layers = 2)
		fresh.plan(0, start, count, slots, SurfaceFaceCull.ALL, skip, off1); assertEquals(1, fresh.misses); assertEquals(0, fresh.hits)
	}

	private class Stats { var frames = 0; var onScreenOwned = 0; var offScreenChunks = 0; var bridged = 0; var gameColumns = 0; var planHits = 0L; var planMisses = 0L }

	private fun simulate(server: Int, drawnDisc: Int, frames: Int, dxPerFrame: Double, yawPerFrame: Float, chunkFrustum: Boolean,
		record: ((Int, Long, List<String>) -> Unit)? = null): Stats {
		val st = Stats()
		val drawn = SurfaceDrawnList()
		val handoff = SurfaceHandoffDecider(SurfaceHandoffTimer(), SurfaceBuiltWait(), drawn)
		val area = SurfaceGameArea()
		val level = Any()
		val regions = HashMap<Long, Region>()
		val skipSlot = BooleanArray(slots)
		val offSlot = BooleanArray(slots)
		for (f in 0 until frames) {
			val now = f * 16L
			val camX = 8.0 + f * dxPerFrame; val camY = 70.0; val camZ = 8.0
			val ccx = SurfaceHandoff.chunkOf(camX); val ccz = SurfaceHandoff.chunkOf(camZ)
			val fr = frustum(f * yawPerFrame)
			fun loaded(x: Int, z: Int) = Math.abs(x - ccx) <= server && Math.abs(z - ccz) <= server

			fun gameSees(x: Int, z: Int) = !fr.chunkOutside(x, z, -64, 320, camX, camY, camZ)
			fun gameDrawnList(x: Int, z: Int) = SurfaceHandoff.inDisc(ccx, ccz, drawnDisc, x, z) && SurfaceHandoff.neighboursLoaded(x, z, ::loaded) && gameSees(x, z)
			val box = server + 6
			drawn.begin(now, camX, camY, camZ)
			for (x in ccx - box..ccx + box) for (z in ccz - box..ccz + box) if (gameDrawnList(x, z)) for (y in 3..5) drawn.drawn(x, y, z)
			area.refresh(level, ccx, ccz, ::loaded)
			area.drawnFrame(drawn.frameReach(), now)
			val grid = SurfaceHandoffGrid(ccx, ccz, area.extent)
			val game = object : SurfaceHandoffDecider.Game {
				override fun loaded(x: Int, z: Int) = loaded(x, z)
				override fun inView(x: Int, z: Int) = gameSees(x, z)
				override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {
					val built = SurfaceHandoff.inDisc(ccx, ccz, drawnDisc, x, z) && SurfaceHandoff.neighboursLoaded(x, z, ::loaded)
					if (settled) p.present = built else { p.topBuilt = built; p.allBuilt = built }
				}
			}
			val frame = handoff.Frame(SurfaceDrawnMode.VANILLA, camX, camY, camZ, now, Double.POSITIVE_INFINITY, area.radius(now), null, true, game)
			val decided = HashMap<Long, Int>()
			fun decision(x: Int, z: Int): Int = decided.getOrPut(SurfaceIndex.key(x, z)) {
				if (!grid.inside(x, z)) SurfaceHandoffGrid.OUTSIDE else frame.decide(x, z, true, 48, 96, 3, 5)
			}
			val perChunk = SurfaceChunkFrustum.use(chunkFrustum, safeFrame = true)
			val frameRanges = ArrayList<String>()
			val reach = box + size
			for (rx in SurfaceRegions.regionOf(ccx - reach)..SurfaceRegions.regionOf(ccx + reach))
				for (rz in SurfaceRegions.regionOf(ccz - reach)..SurfaceRegions.regionOf(ccz + reach)) {
					val r = regions.getOrPut(SurfaceIndex.key(rx, rz)) { Region(rx, rz) }

					for (s in 0 until slots) {
						if (!r.hasSlot(s)) { skipSlot[s] = true; continue }
						val cx = rx * size + s % size; val cz = rz * size + s / size
						skipSlot[s] = decision(cx, cz) and SurfaceHandoffOrder.APEX == 0
					}
					val skipMask = SurfaceDrawPlan.maskOf(skipSlot)

					val n = if (perChunk) r.let { fr.markRegion(rx, rz, it.minY, it.maxY, it.slotMinY, it.slotMaxY, skipSlot, offSlot, camX, camY, camZ) } else { offSlot.fill(false); 0 }
					val offMask = if (n > 0) SurfaceDrawPlan.maskOf(offSlot) else 0L
					st.offScreenChunks += n

					val faceMask = if ((f / 8) % 2 == 0) SurfaceFaceCull.ALL else SurfaceFaceCull.ALL and (1 shl SurfaceFaceCull.NEG_Y).inv()
					val plan = r.cache.plan(0, r.start, r.count, slots, faceMask, skipMask, offMask)
					assertEquals(SurfaceDrawRuns.layerRuns(r.start, r.count, faceMask, skipSlot, if (n > 0) offSlot else null).runs, plan.runs(), "frame $f region $rx,$rz")
					val perRange = plan.runs().map { it.firstVertex to it.vertexCount }
					val multi = multiDrawRanges(plan)

					assertArrayEquals(drawnPerIndex(r, perRange), drawnPerIndex(r, multi), "frame $f region $rx,$rz: multi-draw vs per-range")
					val got = drawnPerIndex(r, multi)
					frameRanges.addAll(multi.map { "$rx,$rz:${it.first}+${it.second}" })
					for (s in 0 until slots) {
						if (!r.hasSlot(s)) continue
						val cx = rx * size + s % size; val cz = rz * size + s / size
						val d = decision(cx, cz)
						val apex = d and SurfaceHandoffOrder.APEX != 0
						val gameShows = d and SurfaceHandoffOrder.GAME != 0 && gameDrawnList(cx, cz)
						var any = 0; var all = true
						for (b in 0 until SurfaceFaceCull.BUCKETS) {
							val i = b * slots + s
							if (r.count[i] == 0) continue
							any += got[i]
							val bucketOn = (faceMask ushr b) and 1 != 0
							if (!bucketOn) assertEquals(0, got[i], "frame $f: culled direction $b of $cx,$cz drawn")
							else if (got[i] != r.count[i]) all = false
						}
						if (!apex) {

							assertEquals(0, any, "frame $f: far terrain drew the game's column $cx,$cz (decision $d)")
							st.gameColumns++
						}
						val onScreen = !fr.chunkOutside(cx, cz, r.slotMinY[s], r.slotMaxY[s], camX, camY, camZ)
						if (apex && (onScreen || !perChunk)) {

							assertTrue(all, "frame $f: owned on-screen column $cx,$cz not drawn whole")
							st.onScreenOwned++
						}
						if (apex && perChunk && !onScreen && any > 0) st.bridged++
						if (perChunk && onScreen && grid.inside(cx, cz)) {

							assertTrue(apex || gameShows, "frame $f: on-screen column $cx,$cz shown by nobody (decision $d)")
						}
					}
				}
			record?.invoke(f, now, frameRanges)
			st.frames++
		}
		for (r in regions.values) { st.planHits += r.cache.hits; st.planMisses += r.cache.misses }
		return st
	}

	@Test
	fun handOffFrustumAndMultiDrawTogetherAreGapFreeAndNeverDrawTheGamesChunks() {

		val still = simulate(server = 14, drawnDisc = 10, frames = 60, dxPerFrame = 0.0, yawPerFrame = 0.11f, chunkFrustum = true)
		val walk = simulate(server = 14, drawnDisc = 10, frames = 120, dxPerFrame = 3.0, yawPerFrame = 0.05f, chunkFrustum = true)
		for (s in listOf(still, walk)) {
			assertTrue(s.offScreenChunks > 0, "per-chunk culling left chunks out")
			assertTrue(s.gameColumns > 0, "the game kept its own columns")
			assertTrue(s.onScreenOwned > 0)
			assertTrue(s.planHits > 0, "unchanged regions reuse their plan")
		}
	}

	@Test
	fun settingOffIsTheWholeRegionPathAndStillGapFree() {
		val s = simulate(server = 14, drawnDisc = 10, frames = 40, dxPerFrame = 2.0, yawPerFrame = 0.1f, chunkFrustum = false)
		assertEquals(0, s.offScreenChunks)
		assertTrue(s.onScreenOwned > 0)
	}

	@Test
	fun theCombinedDrawDoesNotDependOnTheRenderDistance() {

		val base = ArrayList<List<String>>()
		simulate(14, 10, 30, 1.0, 0.07f, true) { _, _, ranges -> base.add(ranges) }
		val again = ArrayList<List<String>>()
		simulate(14, 10, 30, 1.0, 0.07f, true) { _, _, ranges -> again.add(ranges) }
		assertEquals(base, again)

		for (rd in intArrayOf(2, 5, 8, 12, 16, 24, 32)) {
			val s = simulate(server = rd + 2, drawnDisc = rd, frames = 24, dxPerFrame = 0.0, yawPerFrame = 0.27f, chunkFrustum = true)
			assertTrue(s.gameColumns > 0, "rd $rd")
		}
	}

	@Test
	fun urgentUploadReachComesFromTheMeasuredGameArea() {
		for (server in intArrayOf(2, 8, 12, 20, 32)) {
			val a = SurfaceGameArea(); val level = Any()
			for (x in -server..server) for (z in -server..server) a.chunkLoaded(level, x, z)
			a.refresh(level, 0, 0) { x, z -> Math.abs(x) <= server && Math.abs(z) <= server }
			assertEquals(server, a.extent)
			val reach = SurfaceGameArea.urgentReach(a.extent)
			assertEquals(server + SurfaceGameArea.URGENT_MARGIN, reach)

			val q = SurfaceUploadQueue<Any> {}
			val budget = SurfaceUploadQueue.Budget(maxCount = 1, urgentMax = 10_000, urgentReachChunks = reach)
			val near = HashSet<Long>()
			for (rx in SurfaceRegions.regionOf(-server)..SurfaceRegions.regionOf(server)) for (rz in SurfaceRegions.regionOf(-server)..SurfaceRegions.regionOf(server)) {
				q.offer(SurfaceIndex.key(rx, rz), rx, rz, Any(), 0, 1L shl 20, 0)
				val nx = (0).coerceIn(rx * 8, rx * 8 + 7); val nz = (0).coerceIn(rz * 8, rz * 8 + 7)
				if (nx * nx + nz * nz <= server * server) near.add(SurfaceIndex.key(rx, rz))
			}

			val farKey = SurfaceIndex.key(40, 40)
			q.offer(farKey, 40, 40, Any(), 0, 1L shl 20, 0)
			val kinds = HashMap<Long, SurfaceUploadQueue.Kind>()
			q.drain(1, 0, 0, 0, budget, { false }, { 0L }, {}) { e, k -> kinds[e.key] = k; e.bytes }
			for (k in near) assertEquals(SurfaceUploadQueue.Kind.URGENT, kinds[k], "server $server: region ${SurfaceIndex.keyX(k)},${SurfaceIndex.keyZ(k)}")
			assertFalse(kinds[farKey] == SurfaceUploadQueue.Kind.URGENT, "server $server: far region not urgent")
		}

		assertEquals(SurfaceGameArea.URGENT_MARGIN, SurfaceGameArea.urgentReach(0))
	}
}
