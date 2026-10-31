// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunkFrustum
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceManager
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.joml.Matrix4f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

class SurfaceChunkFrustumTest {
	private val slots = SurfaceRegions.SLOTS

	private class Cam(fovDeg: Float, yaw: Float, pitch: Float, aspect: Float = 16f / 9f) {
		val proj: Matrix4f = Matrix4f().perspective(Math.toRadians(fovDeg.toDouble()).toFloat(), aspect, 0.05f, 4096f, true)
		val view: Matrix4f = Matrix4f().rotationX(pitch).rotateY(yaw)
		val frustum = SurfaceChunkFrustum().also { assertTrue(it.set(proj, view)) }

		fun onScreen(x: Double, y: Double, z: Double, roll: Float = 0f, tilt: Float = 0f): Boolean {
			val v = Matrix4f().rotationZ(roll).rotateX(tilt).mul(view)
			val c = Matrix4f(proj).mul(v).transform(Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), 1f))
			return c.w > 0 && Math.abs(c.x) <= c.w && Math.abs(c.y) <= c.w
		}

		fun boxOnScreen(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, roll: Float = 0f, tilt: Float = 0f): Boolean {
			val n = 7
			for (i in 0..n) for (j in 0..n) for (k in 0..n) {
				if (onScreen(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * j / n, z0 + (z1 - z0) * k / n, roll, tilt)) return true
			}
			return false
		}
	}

	private fun forward(c: Cam): DoubleArray {
		val v = Matrix4f(c.view).invert().transform(Vector4f(0f, 0f, -1f, 0f))
		return doubleArrayOf(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
	}

	@Test
	fun chunkWhollyOutsideIsSkippedAndChunkOnScreenOrCrossingTheEdgeIsKept() {
		val c = Cam(70f, 0f, 0f)
		val f = c.frustum

		assertFalse(f.chunkOutside(0, -20, 60, 70, 8.0, 64.0, 8.0))

		assertTrue(f.chunkOutside(0, 20, 60, 70, 8.0, 64.0, 8.0))

		assertTrue(f.chunkOutside(60, -10, 60, 70, 8.0, 64.0, 8.0))

		val depth = 20 * 16.0
		val halfW = Math.tan(Math.toRadians(35.0)) * 16.0 / 9.0 * depth
		val edgeChunk = Math.floor((8.0 + halfW) / 16.0).toInt()
		assertFalse(f.chunkOutside(edgeChunk, -20, 60, 70, 8.0, 64.0, 8.0))

		assertFalse(f.chunkOutside(0, 0, 60, 70, 8.0, 64.0, 8.0))

		assertFalse(f.chunkOutside(0, 20, 70, 70, 8.0, 64.0, 8.0))
	}

	@Test
	fun slackKeepsAChunkWhoseBlocksEndJustOffScreen() {

		val c = Cam(70f, 0f, 0f, aspect = 1f)
		val f = c.frustum
		val depth = 160.0
		val half = Math.tan(Math.toRadians(35.0))
		val edgeX = -half * depth

		val far = 176.0
		val rightSide = -half * far - 0.5
		val cx = Math.floorDiv(Math.floor(rightSide).toInt() - 16 + 1, 16)

		val camX = cx * 16.0 + 16.0 - rightSide
		assertFalse(c.boxOnScreen(cx * 16.0 - camX, -1.0, -176.0, cx * 16.0 + 16 - camX, 1.0, -160.0), "blocks are off screen")

		val x0 = cx * 16.0 - camX
		assertTrue(f.outside(x0, -1.0, -176.0, x0 + 16, 1.0, -160.0, 0.0), "bare box is outside")
		assertFalse(f.outside(x0 - 1, -2.0, -177.0, x0 + 17, 2.0, -159.0, 0.0), "box grown by the 1-block slack is kept")
		assertFalse(c.frustum.chunkOutside(cx, -11, 63, 65, camX, 64.0, 0.0), "within slack: kept")
		assertTrue(c.frustum.chunkOutside(cx, -11, 63, 65, camX + 40.0, 64.0, 0.0), "well outside: skipped")
		assertTrue(edgeX < 0)
	}

	@Test
	fun neverSkipsAChunkWithAnyPartOnScreenEvenWithTheViewTurnedWithinTheMargin() {
		val rnd = Random(1234)
		var skipped = 0
		var cases = 0
		repeat(400) {
			val c = Cam(30f + rnd.nextFloat() * 80f, rnd.nextFloat() * 6.2832f, (rnd.nextFloat() - 0.5f) * 3f, aspect = 1f + rnd.nextFloat() * 1.5f)
			val camX = rnd.nextDouble(-2000.0, 2000.0); val camY = rnd.nextDouble(0.0, 250.0); val camZ = rnd.nextDouble(-2000.0, 2000.0)
			repeat(60) {
				val cx = Math.floorDiv(camX.toInt(), 16) + rnd.nextInt(-80, 81)
				val cz = Math.floorDiv(camZ.toInt(), 16) + rnd.nextInt(-80, 81)
				val minY = rnd.nextInt(0, 200); val maxY = minY + 1 + rnd.nextInt(0, 60)
				cases++
				if (!c.frustum.chunkOutside(cx, cz, minY, maxY, camX, camY, camZ)) return@repeat
				skipped++

				val x0 = cx * 16.0 - 1 - camX; val z0 = cz * 16.0 - 1 - camZ
				for (turn in listOf(0f to 0f, 0.026f to 0f, -0.026f to 0f, 0f to 0.026f, 0f to -0.026f, 0.018f to 0.018f)) {
					assertFalse(c.boxOnScreen(x0, minY - 1 - camY, z0, x0 + 18, maxY + 1 - camY, z0 + 18, turn.first, turn.second),
						"skipped chunk $cx,$cz y $minY..$maxY is on screen (turn $turn)")
				}
			}
		}
		assertTrue(skipped > cases / 4, "the test must exercise skipping: $skipped of $cases")
	}

	@Test
	fun regionStraddlingTheViewSkipsItsChunksBehindTheCamera() {

		val c = Cam(70f, 0f, 0f)
		val skip = BooleanArray(slots)
		val off = BooleanArray(slots)
		val n = c.frustum.markRegion(0, 0, 60, 70, IntArray(slots) { 60 }, IntArray(slots) { 70 }, skip, off, 64.0, 80.0, 64.0)
		assertTrue(n > 0)
		for (slot in 0 until slots) {
			val sz = slot / SurfaceRegions.SIZE

			if (sz >= 6) assertTrue(off[slot], "slot $slot behind the camera is skipped")

			if (sz <= 2 && slot % SurfaceRegions.SIZE in 3..4) assertFalse(off[slot], "slot $slot ahead is kept")
		}

		skip[63] = true
		c.frustum.markRegion(0, 0, 60, 70, IntArray(slots) { 60 }, IntArray(slots) { 70 }, skip, off, 64.0, 80.0, 64.0)
		assertFalse(off[63])
	}

	@Test
	fun regionWhollyOnScreenIsNotTestedChunkByChunk() {
		val c = Cam(70f, 0f, 0f)
		val off = BooleanArray(slots) { true }

		assertEquals(0, c.frustum.markRegion(0, -10, 60, 70, IntArray(slots) { 60 }, IntArray(slots) { 70 }, BooleanArray(slots), off, 64.0, 64.0, 64.0))
		assertTrue(off.none { it })
	}

	@Test
	fun safeFramesOnly() {
		assertTrue(SurfaceChunkFrustum.safeFrame(0f, 0f, false, 0f, 0f, false, false))
		assertFalse(SurfaceChunkFrustum.safeFrame(3f, 0f, false, 0f, 0f, false, false), "hurt tilt")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 1f, false, 0f, 0f, false, false), "death tilt")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 0f, true, 0f, 0f, false, false), "dying")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 0f, false, 0.2f, 0f, false, false), "portal warp")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 0f, false, 0f, 0.5f, false, false), "nausea warp")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 0f, false, 0f, 0f, true, false), "captured frustum")
		assertFalse(SurfaceChunkFrustum.safeFrame(0f, 0f, false, 0f, 0f, false, true), "panoramic")
		assertTrue(SurfaceChunkFrustum.use(true, true))
		assertFalse(SurfaceChunkFrustum.use(false, true))
		assertFalse(SurfaceChunkFrustum.use(true, false))
		val f = SurfaceChunkFrustum()
		assertFalse(f.chunkOutside(0, 100, 0, 10, 0.0, 0.0, 0.0), "no matrix yet: nothing skipped")
		assertFalse(f.set(Matrix4f().zero(), Matrix4f()), "degenerate matrix")
		assertFalse(f.chunkOutside(0, 100, 0, 10, 0.0, 0.0, 0.0))
	}

	private fun layout(count: IntArray): IntArray = IntArray(count.size).also { var a = 0; for (i in it.indices) { it[i] = a; a += count[i] } }

	@Test
	fun offScreenChunksAreLeftOutAndSmallGapsBridgedButNeverHandOffChunksOrCulledDirections() {
		val count = IntArray(SurfaceFaceCull.BUCKETS * slots)
		val b = SurfaceFaceCull.POS_Y * slots
		count[b + 0] = 400; count[b + 1] = 400; count[b + 2] = 4000; count[b + 3] = 400; count[b + 4] = 400
		val start = layout(count)
		val skip = BooleanArray(slots)
		val off = BooleanArray(slots)

		assertEquals(SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip).runs,
			SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip, off, SurfaceDrawRuns.BRIDGE_VERTICES).runs)

		off[2] = true
		var r = SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip, off, 1024)
		assertEquals(listOf(SurfaceDrawRuns.Run(0, 800), SurfaceDrawRuns.Run(4800, 800)), r.runs)
		assertEquals(1000, r.offScreenFaces)

		off[2] = false; off[1] = true
		r = SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip, off, 1024)
		assertEquals(listOf(SurfaceDrawRuns.Run(0, 5600)), r.runs)
		assertEquals(0, r.offScreenFaces)

		off[1] = false; off[3] = true; off[4] = true
		r = SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip, off, 1024)
		assertEquals(listOf(SurfaceDrawRuns.Run(0, 4800)), r.runs)
		assertEquals(200, r.offScreenFaces)

		off.fill(false); off[1] = true; skip[2] = true
		r = SurfaceDrawRuns.bucketRuns(start, count, slots, SurfaceFaceCull.ALL, skip, off, 1_000_000)
		assertEquals(listOf(SurfaceDrawRuns.Run(0, 400), SurfaceDrawRuns.Run(4800, 800)), r.runs)

		skip.fill(false); off.fill(false)
		val c2 = IntArray(SurfaceFaceCull.BUCKETS * slots)
		c2[SurfaceFaceCull.POS_X * slots + 0] = 8
		c2[SurfaceFaceCull.POS_X * slots + 1] = 8
		c2[SurfaceFaceCull.NEG_X * slots + 0] = 8
		c2[SurfaceFaceCull.POS_Y * slots + 0] = 8
		val s2 = layout(c2)
		off[1] = true
		val mask = SurfaceFaceCull.ALL and (1 shl SurfaceFaceCull.NEG_X).inv()
		r = SurfaceDrawRuns.bucketRuns(s2, c2, slots, mask, skip, off, 1_000_000)
		assertEquals(listOf(SurfaceDrawRuns.Run(0, 8), SurfaceDrawRuns.Run(24, 8)), r.runs)
		assertEquals(2, r.culledFaces)
		assertEquals(2, r.offScreenFaces)
	}

	@Test
	fun bridgedRunsNeverDrawAHandOffChunkOrACulledDirection() {
		val rnd = Random(77)
		repeat(500) {
			val count = IntArray(SurfaceFaceCull.BUCKETS * slots) { if (rnd.nextInt(3) == 0) 0 else 4 * rnd.nextInt(1, 200) }
			val start = layout(count)
			val skip = BooleanArray(slots) { rnd.nextInt(5) == 0 }
			val off = BooleanArray(slots) { rnd.nextInt(3) == 0 }
			val mask = rnd.nextInt(1 shl SurfaceFaceCull.BUCKETS) or (1 shl SurfaceFaceCull.ALWAYS)
			val r = SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip, off, SurfaceDrawRuns.BRIDGE_VERTICES)
			val plain = SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip)
			val drawn = BooleanArray(count.size)
			for (run in r.runs) for (i in count.indices) if (count[i] > 0 && start[i] >= run.firstVertex && start[i] < run.firstVertex + run.vertexCount) {
				assertTrue(start[i] + count[i] <= run.firstVertex + run.vertexCount, "runs cover whole entries")
				drawn[i] = true
			}
			for (i in count.indices) {
				if (count[i] == 0) continue
				val slot = i % slots
				val bucketOn = (mask ushr (i / slots)) and 1 == 1
				if (skip[slot] || !bucketOn) assertFalse(drawn[i], "hand-off chunk or culled direction drawn")
				else if (!off[slot]) assertTrue(drawn[i], "on-screen geometry dropped")
			}
			assertTrue(r.runs.size <= plain.runs.size + slots, "draw calls stay bounded")
			assertEquals(plain.culledFaces, r.culledFaces)
		}
	}

	@Test
	fun settingOffIsExactlyTheWholeRegionPath() {
		val rnd = Random(5)
		repeat(200) {
			val count = IntArray(SurfaceFaceCull.BUCKETS * slots) { if (rnd.nextBoolean()) 0 else 4 * rnd.nextInt(1, 100) }
			val start = layout(count)
			val skip = BooleanArray(slots) { rnd.nextInt(4) == 0 }
			val off = BooleanArray(slots) { rnd.nextInt(2) == 0 }
			val mask = rnd.nextInt(1 shl SurfaceFaceCull.BUCKETS)

			val offPath = SurfaceDrawRuns.layerRuns(start, count, mask, skip, if (SurfaceChunkFrustum.use(false, true)) off else null)
			val plain = SurfaceDrawRuns.bucketRuns(start, count, slots, mask, skip)
			assertEquals(plain.runs, offPath.runs)
			assertEquals(plain.culledFaces, offPath.culledFaces)
			assertEquals(0, offPath.offScreenFaces)

			val onPath = SurfaceDrawRuns.layerRuns(start, count, mask, skip, if (SurfaceChunkFrustum.use(true, true)) off else null)
			assertTrue(onPath.runs.sumOf { it.vertexCount } <= plain.runs.sumOf { it.vertexCount })
		}
	}

	@Test
	fun settingDefaultsOnAndASavedValueIsKept() {
		val c = SurfaceManager.TConfig
		c.explicitDefaultLoad()
		assertTrue(c.chunkFrustumCulling)
		assertTrue(c.faceCulling)
		c.loadFrom(Unit, Json.parseToJsonElement("""{"apex-far-terrain":{"per-chunk-frustum-culling":false}}""").jsonObject)
		assertFalse(c.chunkFrustumCulling)
		assertTrue(c.faceCulling)
		val saved = c.saveTo(Unit)["apex-far-terrain"]!!.jsonObject
		assertEquals("false", saved["per-chunk-frustum-culling"].toString())
		assertTrue("per-chunk-frustum-culling" in c.allOptions)
		c.explicitDefaultLoad()
		assertTrue(c.chunkFrustumCulling)
	}

	private fun projectFile(path: String): File =
		generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, path) }.first { it.isFile }

	@Test
	fun translationsAndMigrationRowsInOrder() {
		val prefix = "apex.config.apex-far-terrain"
		val keys = Regex("^\\s*\"([^\"]+)\"\\s*:", RegexOption.MULTILINE).findAll(projectFile("translations/en_us.json").readText())
			.map { it.groupValues[1] }.filter { it.startsWith(prefix) }.toList()
		assertTrue("$prefix.per-chunk-frustum-culling" in keys, "name translated")
		assertTrue("$prefix.per-chunk-frustum-culling.description" in keys, "description translated")
		assertEquals(keys.sorted(), keys, "far-terrain translation keys stay in order")
		assertEquals(keys.toSet().size, keys.size, "no duplicate keys")
		val mig = projectFile("src/main/resources/assets/apex/settings/migration-map.tsv").readLines()
			.filter { it.startsWith("apex-far-terrain.") }.map { it.substringBefore('\t') }
		assertTrue("apex-far-terrain.per-chunk-frustum-culling" in mig)
		assertEquals(mig.sorted(), mig, "far-terrain migration rows stay in order")
		val row = projectFile("src/main/resources/assets/apex/settings/migration-map.tsv").readLines()
			.first { it.startsWith("apex-far-terrain.per-chunk-frustum-culling\t") }.split('\t')
		assertEquals(listOf("misc.textures-terrain.per-chunk-frustum-culling", "toggle", "render"), row.drop(1))
	}

	@Test
	fun cameraInsideARegionAcrossRotationsKeepsTheCameraChunkAndOnlySkipsOffScreenChunks() {

		for (yawStep in 0 until 16) for (pitch in listOf(-1.4f, -0.6f, 0f, 0.6f, 1.4f)) {
			val c = Cam(70f, yawStep * (6.2832f / 16), pitch)
			val off = BooleanArray(slots)
			val n = c.frustum.markRegion(0, 0, 40, 120, IntArray(slots) { 40 }, IntArray(slots) { 120 }, BooleanArray(slots), off, 52.0, 80.0, 76.0)

			assertFalse(off[4 * 8 + 3], "the camera's own chunk skipped (yaw $yawStep, pitch $pitch)")

			for (slot in 0 until slots) if (off[slot]) {
				val x0 = (slot % 8) * 16.0 - 1 - 52.0; val z0 = (slot / 8) * 16.0 - 1 - 76.0
				for (turn in listOf(0f to 0f, 0.026f to 0f, 0f to 0.026f, 0f to -0.026f))
					assertFalse(c.boxOnScreen(x0, 39.0 - 80.0, z0, x0 + 18, 121.0 - 80.0, z0 + 18, turn.first, turn.second),
						"skipped slot $slot is on screen (yaw $yawStep, pitch $pitch, turn $turn)")
			}
			if (Math.abs(pitch) < 1f) assertTrue(n > 0, "something behind is skipped (yaw $yawStep, pitch $pitch)")
		}
	}

	@Test
	fun simulatedSaving() {
		val rnd = Random(9)
		var wholeV = 0L; var chunkV = 0L; var wholeCalls = 0L; var chunkCalls = 0L
		var offChunks = 0L; var farChunks = 0L
		val bridges = intArrayOf(0, 1024, 2048, 4096)
		val bridgeV = LongArray(bridges.size); val bridgeCalls = LongArray(bridges.size)
		val reach = 48
		val regions = (-reach / 8 - 1..reach / 8).flatMap { rx -> (-reach / 8 - 1..reach / 8).map { rz -> rx to rz } }
		val counts = regions.associateWith { IntArray(SurfaceFaceCull.BUCKETS * slots) { 4 * rnd.nextInt(10, 120) } }
		val heights = regions.associateWith { IntArray(slots) { 60 + rnd.nextInt(0, 30) } }
		repeat(72) { step ->
			val yaw = step * (6.2832f / 72)
			val c = Cam(70f, yaw, -0.1f)
			val camX = 4.0; val camY = 75.0; val camZ = 4.0
			for ((rx, rz) in regions) {
				val size = 128.0
				val bx = rx * size - camX; val bz = rz * size - camZ
				if (c.frustum.outside(bx - 1, 50.0 - camY, bz - 1, bx + size + 1, 121.0 - camY, bz + size + 1, 0.0)) continue
				val cnt = counts.getValue(rx to rz)
				val start = layout(cnt)

				val skip = BooleanArray(slots) { s ->
					val cx = rx * 8 + s % 8; val cz = rz * 8 + s / 8
					val d2 = cx * cx + cz * cz
					d2 <= 12 * 12 || d2 > reach * reach
				}
				if (skip.all { it }) continue
				val off = BooleanArray(slots)
				val ys = heights.getValue(rx to rz)
				val marked = c.frustum.markRegion(rx, rz, 50, 121, ys, IntArray(slots) { ys[it] + 6 }, skip, off, camX, camY, camZ)
				val any = marked > 0
				offChunks += marked; farChunks += skip.count { !it }
				val whole = SurfaceDrawRuns.layerRuns(start, cnt, SurfaceFaceCull.ALL, skip, null)
				val per = SurfaceDrawRuns.layerRuns(start, cnt, SurfaceFaceCull.ALL, skip, if (any) off else null)
				for ((k, bridge) in bridges.withIndex()) {
					val b = if (any) SurfaceDrawRuns.bucketRuns(start, cnt, slots, SurfaceFaceCull.ALL, skip, off, bridge) else whole
					bridgeV[k] += b.runs.sumOf { it.vertexCount }; bridgeCalls[k] += b.runs.size
				}
				wholeV += whole.runs.sumOf { it.vertexCount }; chunkV += per.runs.sumOf { it.vertexCount }
				wholeCalls += whole.runs.size; chunkCalls += per.runs.size
			}
		}
		val saved = 100.0 * (wholeV - chunkV) / wholeV
		println("[sim] per-chunk frustum culling: %.1f%% fewer far vertices (%d -> %d), draw calls %d -> %d".format(saved, wholeV, chunkV, wholeCalls, chunkCalls))
		println("[sim] chunks frustum-skipped per frame: %.1f of %.1f far chunks in kept regions (avg over 72 yaws)".format(offChunks / 72.0, farChunks / 72.0))
		for ((k, bridge) in bridges.withIndex()) println("[sim] bridge %d: vertices %d, draw calls %d".format(bridge, bridgeV[k], bridgeCalls[k]))
		assertTrue(chunkV < wholeV)
		assertTrue(chunkCalls <= wholeCalls * 105 / 100, "bridging keeps draw calls near the whole-region path's")
	}
}
