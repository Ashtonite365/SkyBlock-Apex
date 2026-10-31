// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder.APEX
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder.BOTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder.GAME
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceCameraMotion
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceHandoffTimingTest {
	private val k = 42L

	@Test
	fun gameShownOnlyAHundredMsAfterItIsBuilt() {
		val t = SurfaceHandoffTimer()
		assertEquals(100L, SurfaceHandoffTimer.HOLD_MS)
		assertFalse(t.update(k, 10_000, built = true, signature = 1, fadeDone = true))
		assertFalse(t.update(k, 10_050, true, 1, true))
		assertFalse(t.update(k, 10_099, true, 1, true))
		assertTrue(t.update(k, 10_100, true, 1, true))
		assertTrue(t.update(k, 10_116, true, 1, true))
	}

	@Test
	fun notBuiltNeverShown() {
		val t = SurfaceHandoffTimer()
		for (ms in 0L..5000L step 16) assertFalse(t.update(k, ms, false, 0, true))
	}

	@Test
	fun rebuildDuringTheWaitRestartsTheWait() {
		val t = SurfaceHandoffTimer()
		t.update(k, 0, true, 1, true)
		assertFalse(t.update(k, 60, true, 2, true))
		assertFalse(t.update(k, 120, true, 2, true))
		assertFalse(t.update(k, 159, true, 2, true))
		assertTrue(t.update(k, 160, true, 2, true))
	}

	@Test
	fun partlyUnbuiltDuringTheWaitRestartsTheWait() {
		val t = SurfaceHandoffTimer()
		t.update(k, 0, true, 1, true)
		assertFalse(t.update(k, 70, false, 0, true))
		assertFalse(t.update(k, 80, true, 1, true))
		assertFalse(t.update(k, 150, true, 1, true))
		assertTrue(t.update(k, 180, true, 1, true))
	}

	@Test
	fun waitsForTheGamesFadeIn() {
		val t = SurfaceHandoffTimer()
		t.update(k, 0, true, 1, false)
		assertFalse(t.update(k, 1000, true, 1, false), "still fading in from the fog colour")
		assertFalse(t.update(k, 2000, true, 1, false))
		assertTrue(t.update(k, 2016, true, 1, true))
	}

	@Test
	fun onceTheGameRendersItRebuildsNeverHandItBack() {
		val t = SurfaceHandoffTimer()
		t.update(k, 0, true, 1, true)
		assertTrue(t.update(k, 1000, true, 1, true))
		var sig = 1L
		for (ms in 1016L..20_000L step 16) {

			if (ms % 160 == 0L) sig++
			assertTrue(t.update(k, ms, built = ms % 48 != 0L, signature = sig, fadeDone = ms % 96 != 0L), "taken back at $ms ms")
		}

		t.release(k)
		assertFalse(t.isShown(k))
		assertFalse(t.update(k, 21_000, true, sig, true))
		assertTrue(t.update(k, 22_000, true, sig, true))
	}

	private fun feed(m: SurfaceCameraMotion, x0: Double, z0: Double, vx: Double, vz: Double, fromMs: Long, toMs: Long, fps: Int = 60) {
		var t = fromMs
		while (t <= toMs) { m.frame(x0 + vx * (t - fromMs) / 1000.0, z0 + vz * (t - fromMs) / 1000.0, t); t += 1000L / fps }
	}

	@Test
	fun cameraVelocityFromItsRealPositionChange() {
		val m = SurfaceCameraMotion()
		feed(m, 0.0, 0.0, 20.0, -5.0, 0, 3000)
		assertEquals(20.0, m.vx, 0.5); assertEquals(-5.0, m.vz, 0.5)
		assertTrue(m.reliable(3000))
	}

	@Test
	fun joiningStandingStillAndSlowWalkingAreNotPredicted() {
		val m = SurfaceCameraMotion()
		feed(m, 0.0, 0.0, 5.6, 0.0, 0, 900)
		assertFalse(m.reliable(900), "first second after joining")
		val still = SurfaceCameraMotion(); feed(still, 0.0, 0.0, 0.0, 0.0, 0, 3000)
		assertFalse(still.reliable(3000))
		val slow = SurfaceCameraMotion(); feed(slow, 0.0, 0.0, 1.3, 0.0, 0, 3000)
		assertFalse(slow.reliable(3000))
	}

	@Test
	fun teleportsAndReversalsFallBackForAWhile() {
		val m = SurfaceCameraMotion()
		feed(m, 0.0, 0.0, 10.0, 0.0, 0, 2000)
		assertTrue(m.reliable(2000))
		m.frame(100.0, 0.0, 2016)
		assertFalse(m.reliable(2016))
		feed(m, 100.0, 0.0, 10.0, 0.0, 2032, 2900)
		assertFalse(m.reliable(2900))
		feed(m, 109.0, 0.0, 10.0, 0.0, 3100, 3500)
		assertTrue(m.reliable(3500))
		feed(m, 113.0, 0.0, -10.0, 0.0, 3516, 3700)
		assertFalse(m.reliable(3700))
		m.reset()
		assertFalse(m.reliable(10_000))
	}

	private fun cylinderDraws(cx: Int, cz: Int, camX: Double, camZ: Double, rd: Int) = SurfaceHandoffGeometry.nearestSq(cx, cz, camX, camZ) < rd * 16.0 * rd * 16.0

	private fun vanillaDraws(cx: Int, cz: Int, camX: Double, camZ: Double, rd: Int) =
		SurfaceHandoff.inDisc(SurfaceHandoffGeometry.chunkOf(camX), SurfaceHandoffGeometry.chunkOf(camZ), rd, cx, cz)

	@Test
	fun whatWeCallTheGamesIsDrawnByVanillaAndByACylinderRenderer() {
		var holesBefore = 0
		var holes = 0
		for (rd in 2..32) for (fx in 0 until 16 step 3) for (fz in 0 until 16 step 3) {
			val camX = 1000.0 + fx + 0.5; val camZ = -500.0 + fz + 0.5
			val ccx = SurfaceHandoffGeometry.chunkOf(camX); val ccz = SurfaceHandoffGeometry.chunkOf(camZ)
			for (cx in ccx - rd - 2..ccx + rd + 2) for (cz in ccz - rd - 2..ccz + rd + 2) {
				if (vanillaDraws(cx, cz, camX, camZ, rd) && !cylinderDraws(cx, cz, camX, camZ, rd)) holesBefore++
				if (SurfaceHandoffGeometry.gameOwns(cx, cz, camX, camZ, rd)) {
					assertTrue(vanillaDraws(cx, cz, camX, camZ, rd))
					if (!cylinderDraws(cx, cz, camX, camZ, rd)) holes++
				}
			}
		}
		println("cylinder-renderer holes: vanilla rule alone $holesBefore, with the conservative check $holes")
		assertTrue(holesBefore > 0, "the old ownership left holes on the diagonals")
		assertEquals(0, holes)
	}

	@Test
	fun decisions() {
		assertEquals(GAME, SurfaceHandoffOrder.decide(false, false, false))
		assertEquals(GAME, SurfaceHandoffOrder.decide(false, true, true))
		assertEquals(APEX, SurfaceHandoffOrder.decide(true, false, false))
		assertEquals(APEX, SurfaceHandoffOrder.decide(true, false, true))
		assertEquals(GAME, SurfaceHandoffOrder.decide(true, true, false))
		assertEquals(BOTH, SurfaceHandoffOrder.decide(true, true, true))
	}

	@Test
	fun rebuildsInTheInteriorNeverMakeFarTerrainDraw() {
		val t = SurfaceHandoffTimer()
		t.update(k, 0, true, 0, true)
		assertTrue(t.update(k, 1000, true, 0, true))
		for (f in 0 until 600) {
			val shown = t.update(k, 1016L + f * 16, built = f % 7 != 0, signature = (f / 5).toLong(), fadeDone = true)
			assertEquals(GAME, SurfaceHandoffOrder.decide(true, shown, SurfaceHandoffGeometry.outerRings(0, 0, 8.0, 8.0, 12)), "frame $f")
		}
	}

	@Test
	fun movingTowardAChunkFarTerrainHandsOverAfterTheGamePlusTheHold() {
		val t = SurfaceHandoffTimer()
		val d = (0 until 200).map { f -> SurfaceHandoffOrder.decide(true, t.update(k, f * 16L, f >= 30, 7, true), false) }
		val firstGame = d.indexOfFirst { it and GAME != 0 }
		val hold = SurfaceHandoffTimer.HOLD_MS
		assertTrue(firstGame * 16 >= 30 * 16 + hold, "game shown at frame $firstGame, too early")
		assertTrue(firstGame * 16 <= 30 * 16 + hold + 16)
		assertTrue(d.subList(0, firstGame).all { it == APEX }, "far terrain draws the whole wait")
		assertTrue(d.subList(firstGame, 200).all { it == GAME })
	}

	private class Sim(val gaps: Int, val predictedPreDraws: Long, val fallbackPreDraws: Long, val predictedFrames: Int)

	private fun simulate(seconds: Double, rd: Int, cylinder: Boolean, path: (Long) -> Pair<Double, Double>): Sim {
		val m = SurfaceCameraMotion()
		val timers = HashMap<Long, SurfaceHandoffTimer>()
		var gaps = 0
		var predicted = 0L
		var fallback = 0L
		var predictedFrames = 0
		val frames = (seconds * 60).toInt()
		var prev: HashMap<Long, Int>? = null
		var prevPos = path(0)
		for (f in 0..frames) {
			val now = f * 1000L / 60
			val (x, z) = path(now)
			val jumped = Math.hypot(x - prevPos.first, z - prevPos.second) > SurfaceCameraMotion.TELEPORT
			prevPos = x to z
			m.frame(x, z, now)
			val predict = if (m.reliable(now)) SurfaceHandoffGeometry.Prediction(x, z, m.vx, m.vz) else null
			if (predict != null) predictedFrames++
			val ccx = SurfaceHandoffGeometry.chunkOf(x); val ccz = SurfaceHandoffGeometry.chunkOf(z)
			val decided = HashMap<Long, Int>()
			for (cx in ccx - rd - 3..ccx + rd + 3) for (cz in ccz - rd - 3..ccz + rd + 3) {
				val key = cx * 100_000L + cz
				val t = timers.getOrPut(key) { SurfaceHandoffTimer() }
				val owns = SurfaceHandoffGeometry.gameOwns(cx, cz, x, z, rd)
				val shown = if (!owns) { t.release(key); false } else if (f == 0) { t.update(key, now - 5000, true, 1, true); t.update(key, now, true, 1, true) } else t.update(key, now, true, 1, true)
				val fb = shown && SurfaceHandoffGeometry.outerRings(cx, cz, x, z, rd)
				val pre = shown && (predict?.drops(cx, cz, rd) ?: fb)
				if (predict != null) {
					val p = shown && predict.drops(cx, cz, rd)
					if (p) { predicted++; assertTrue(fb, "prediction outside the two rings at $cx,$cz") }
					if (fb) fallback++
				}
				var d = SurfaceHandoffOrder.decide(true, shown, pre)
				if (cylinder) d = d or GAME
				decided[key] = d
			}
			fun check(dec: Map<Long, Int>) {
				for ((key, d) in dec) {
					val cx = Math.floorDiv(key + 50_000, 100_000L).toInt(); val cz = (key - cx * 100_000L).toInt()
					val game = if (cylinder) cylinderDraws(cx, cz, x, z, rd) else vanillaDraws(cx, cz, x, z, rd)
					if ((d and APEX) == 0 && !((d and GAME) != 0 && game)) gaps++
				}
			}
			check(decided)
			if (!jumped) prev?.let { check(it) }
			prev = decided
		}
		return Sim(gaps, predicted, fallback, predictedFrames)
	}

	private fun straight(speed: Double, dirX: Double, dirZ: Double, x0: Double = 3.3, z0: Double = 7.9): (Long) -> Pair<Double, Double> {
		val l = Math.hypot(dirX, dirZ)
		return { t -> (x0 + speed * dirX / l * t / 1000.0) to (z0 + speed * dirZ / l * t / 1000.0) }
	}

	private val speeds = mapOf("sprint" to 5.6, "skyblock top speed" to 28.0, "elytra" to 30.0, "firework" to 33.0, "dive" to 75.0)

	private fun allClear(name: String, seconds: Double, rd: Int = 12, path: (Long) -> Pair<Double, Double>): Pair<Sim, Sim> {
		val v = simulate(seconds, rd, false, path)
		val c = simulate(seconds, rd, true, path)
		assertEquals(0, v.gaps, "$name: vanilla rule gaps")
		assertEquals(0, c.gaps, "$name: cylinder renderer gaps")
		println("$name: pre-drawn per frame (summed) prediction ${v.predictedPreDraws} vs two rings ${v.fallbackPreDraws} over ${v.predictedFrames} predicted frames")
		return v to c
	}

	@Test
	fun straightAndDiagonalAtEverySpeedNeverShowAGap() {
		for ((name, sp) in speeds) for ((dname, dir) in mapOf("straight" to (1.0 to 0.0), "diagonal" to (1.0 to 1.0), "shallow" to (2.0 to -1.0))) {
			val (v, _) = allClear("$name $dname", if (sp > 20) 6.0 else 12.0, path = straight(sp, dir.first, dir.second))
			assertTrue(v.predictedFrames > 0)
			assertTrue(v.predictedPreDraws < v.fallbackPreDraws, "$name $dname: prediction pre-draws fewer than the two rings")
		}
	}

	@Test
	fun diagonalCornerCrossingsAtLargeRenderDistances() {
		for (rd in listOf(8, 12, 16, 24, 32)) {

			val v = simulate(4.0, rd, false, straight(28.0, 1.0, 1.0, 0.5, 0.5))
			val c = simulate(4.0, rd, true, straight(28.0, 1.0, 1.0, 0.5, 0.5))
			assertEquals(0, v.gaps, "rd $rd vanilla"); assertEquals(0, c.gaps, "rd $rd cylinder")
		}
	}

	@Test
	fun turningAndThirdPersonCameraNeverShowAGap() {

		allClear("circle", 10.0) { t -> val a = t / 1000.0 * 0.6; (200 * Math.cos(a)) to (200 * Math.sin(a)) }

		allClear("zig-zag", 10.0) { t -> val s = t / 1000.0; (28.0 * s) to (if ((s.toInt() % 2) == 0) 28.0 * (s % 1.0) else 28.0 * (1 - s % 1.0)) }

		allClear("third person", 10.0) { t -> val s = t / 1000.0; (5.6 * s - 4 * Math.cos(s * 2)) to (4 * Math.sin(s * 2)) }
	}

	@Test
	fun standingStillSlowWalkAndEtherwarpsNeverShowAGap() {
		allClear("still", 5.0) { _ -> 7.5 to 8.5 }
		allClear("slow walk", 10.0, path = straight(1.3, 1.0, 0.4))

		allClear("etherwarp", 9.0) { t -> val s = t / 1000.0; (5.6 * s + 60.0 * Math.floor(s / 1.5)) to (20.0 * Math.floor(s / 1.5)) }

		allClear("aote", 6.0) { t -> val s = t / 1000.0; (12.0 * Math.floor(s / 0.25)) to 3.0 }
	}
}
