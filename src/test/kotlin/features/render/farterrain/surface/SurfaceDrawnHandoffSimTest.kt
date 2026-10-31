// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuiltWait
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceCameraMotion
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnMode
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSource
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSources
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceGameArea
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffDecider
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGrid
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceSectionSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceDrawnHandoffSimTest {
	private class Result {
		var frames = 0; var gaps = 0; var gapColumns = HashSet<Long>()
		var doublePreDraw = 0; var doubleOther = 0; var longestOtherMs = 0L
		var flips = 0
		var behindStillGame = 0; var behindFarDraws = 0
		var behindApex = 0
		var occludedFar = 0
		var takenBack = 0
		var shownMax = 0
		override fun toString() = "frames $frames gaps $gaps doubleDraw preDraw $doublePreDraw other $doubleOther " +
			"(longest other ${longestOtherMs} ms) flips $flips behind: game $behindStillGame far $behindFarDraws apex $behindApex " +
			"occluded-far $occludedFar taken-back $takenBack shown $shownMax"
	}

	private fun hash(x: Int, z: Int): Int { var h = x * 73856093 xor z * 19349663; h = h xor (h ushr 13); return h and 0x7fffffff }
	private fun topSection(cx: Int, cz: Int) = 4 + hash(cx, cz) % 2
	private fun occluded(cx: Int, cz: Int, ccx: Int, ccz: Int) =
		hash(cx + 7, cz - 3) % 7 == 0 && maxOf(Math.abs(cx - ccx), Math.abs(cz - ccz)) > 4

	private class Path(val pos: (Long) -> DoubleArray, val yaw: (Long) -> Double)

	private fun inFrustum(cx: Int, cz: Int, camX: Double, camZ: Double, yaw: Double): Boolean {
		val dx = cx * 16 + 8 - camX; val dz = cz * 16 + 8 - camZ
		if (dx * dx + dz * dz < 24.0 * 24.0) return true
		var a = Math.atan2(dz, dx) - yaw
		while (a > Math.PI) a -= 2 * Math.PI
		while (a < -Math.PI) a += 2 * Math.PI
		return Math.abs(a) <= Math.toRadians(55.0)
	}

	private fun run(mode: SurfaceDrawnMode, path: Path, ms: Long, rd: Int = 12, server: Int = 14, spinCheckAt: Long = -1): Result {
		val r = Result()
		val holdBack = mode == SurfaceDrawnMode.VANILLA || mode == SurfaceDrawnMode.PREDICTION
		val motion = SurfaceCameraMotion()
		val drawn = SurfaceDrawnList()
		val handoff = SurfaceHandoffDecider(SurfaceHandoffTimer(), SurfaceBuiltWait(), drawn)
		val area = SurfaceGameArea()
		val level = Any()
		val readyAt = HashMap<Long, Long>()
		val wasShown = HashSet<Long>()
		val otherSince = HashMap<Long, Long>()

		var frameList: List<IntArray> = emptyList()
		val adapter = object : SurfaceDrawnSource {
			override fun collect(sink: SurfaceSectionSink): Boolean { for (s in frameList) sink.drawn(s[0], s[1], s[2]); return true }
		}
		val sources = SurfaceDrawnSources({ it == SurfaceDrawnSources.RENDERER_MOD_ID }, { adapter }, { m, t -> throw AssertionError(m, t) })
		var t = 0L
		while (t <= ms) {
			val p = path.pos(t); val camX = p[0]; val camZ = p[1]; val yaw = path.yaw(t)
			val ccx = SurfaceHandoff.chunkOf(camX); val ccz = SurfaceHandoff.chunkOf(camZ)

			val loaded = { x: Int, z: Int -> Math.abs(x - ccx) <= server && Math.abs(z - ccz) <= server }
			val box = maxOf(rd, server) + 3
			val built = HashSet<Long>()
			for (x in ccx - box..ccx + box) for (z in ccz - box..ccz + box) {
				val k = SurfaceIndex.key(x, z)
				if (SurfaceHandoff.fallbackOwned(ccx, ccz, rd, x, z, loaded)) {
					val since = readyAt.getOrPut(k) { t }
					if (t - since >= 48) built.add(k)
				} else readyAt.remove(k)
			}
			fun visibleSpot(x: Int, z: Int) = inFrustum(x, z, camX, camZ, yaw) && !occluded(x, z, ccx, ccz)
			fun rendererDistance(x: Int, z: Int) = SurfaceHandoffGeometry.nearestSq(x, z, camX, camZ) < (rd * 16.0) * (rd * 16.0)
			fun viewArea(x: Int, z: Int) = Math.abs(x - ccx) <= rd + 1 && Math.abs(z - ccz) <= rd + 1

			drawn.begin(t, camX, CAM_Y, camZ)
			when (mode) {
				SurfaceDrawnMode.VANILLA -> for (x in ccx - box..ccx + box) for (z in ccz - box..ccz + box) {

					if (SurfaceHandoff.inDisc(ccx, ccz, rd, x, z) && loaded(x, z) && visibleSpot(x, z))
						for (y in 2..topSection(x, z)) drawn.drawn(x, y, z)
				}
				SurfaceDrawnMode.RENDERER -> {
					val l = ArrayList<IntArray>()
					for (x in ccx - box..ccx + box) for (z in ccz - box..ccz + box)
						if (SurfaceIndex.key(x, z) in built && SurfaceHandoff.inDisc(ccx, ccz, rd, x, z) && rendererDistance(x, z) && visibleSpot(x, z))
							for (y in 2..topSection(x, z)) l.add(intArrayOf(x, y, z))
					frameList = l
					assertTrue(sources.collect(drawn))
				}
				SurfaceDrawnMode.PREDICTION -> {}
			}

			motion.frame(camX, camZ, t)
			val predict = if (motion.reliable(t)) SurfaceHandoffGeometry.Prediction(camX, camZ, motion.vx, motion.vz) else null
			area.refresh(level, ccx, ccz, loaded)
			area.drawnFrame(drawn.frameReach(), t)
			val grid = SurfaceHandoffGrid(ccx, ccz, area.extent)
			val game = object : SurfaceHandoffDecider.Game {
				override fun loaded(x: Int, z: Int) = loaded(x, z)
				override fun inView(x: Int, z: Int) = inFrustum(x, z, camX, camZ, yaw)
				override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {
					val k = SurfaceIndex.key(x, z)
					when (mode) {

						SurfaceDrawnMode.VANILLA -> { p.present = viewArea(x, z); p.topBuilt = k in built; p.allBuilt = p.topBuilt }
						SurfaceDrawnMode.RENDERER -> { p.topBuilt = k in built; p.allBuilt = p.topBuilt }

						SurfaceDrawnMode.PREDICTION -> if (settled) p.present = k in built else { p.topBuilt = k in built; p.allBuilt = p.topBuilt }
					}
					p.signature = 1
				}
			}
			val frame = handoff.Frame(mode, camX, CAM_Y, camZ, t, Double.POSITIVE_INFINITY, area.radius(t), predict, holdBack, game)
			if (t % 5000 == 0L) handoff.prune { k -> grid.inside(SurfaceIndex.keyX(k), SurfaceIndex.keyZ(k)) }
			var shownNow = 0
			for (x in ccx - box..ccx + box) for (z in ccz - box..ccz + box) {
				val k = SurfaceIndex.key(x, z)
				val hi = topSection(x, z)
				var d = SurfaceHandoffGrid.OUTSIDE
				var shown = false; var preDraw = false; var present = false
				if (grid.inside(x, z)) {
					d = frame.decide(x, z, true, 32, hi * 16 + 16, 2, hi)
					shown = frame.lastShown; preDraw = frame.lastPreDraw; present = frame.lastPresent
				}
				if (shown) shownNow++

				val gameDraws = if (mode == SurfaceDrawnMode.PREDICTION) present else present && drawn.columnDrawnThisFrame(x, z)
				if (k in wasShown && !shown && gameDraws) r.flips++
				if (shown) wasShown.add(k) else wasShown.remove(k)
				val apex = d and SurfaceHandoffOrder.APEX != 0
				if (grid.inside(x, z) && frame.lastTakenBack) { r.takenBack++; assertTrue(apex) }

				val inRange = SurfaceHandoff.inDisc(ccx, ccz, rd, x, z) && (mode != SurfaceDrawnMode.RENDERER || rendererDistance(x, z))
				val gameRenders = k in built && inRange && visibleSpot(x, z) && (d and SurfaceHandoffOrder.GAME != 0)
				if (visibleSpot(x, z) && !apex && !gameRenders) { r.gaps++; r.gapColumns.add(k) }
				if (apex && gameRenders) {
					if (preDraw) { r.doublePreDraw++; otherSince.remove(k) } else {
						r.doubleOther++
						val s = otherSince.getOrPut(k) { t }
						r.longestOtherMs = maxOf(r.longestOtherMs, t - s + 16)
					}
				} else otherSince.remove(k)
				if (spinCheckAt in 0..t && t - spinCheckAt < 16 && present && k in built) {
					if (!inFrustum(x, z, camX, camZ, yaw)) {
						if (shown) { r.behindStillGame++; if (apex && !preDraw) r.behindApex++ } else if (!occluded(x, z, ccx, ccz) && inRange) r.behindFarDraws++
					}
					if (occluded(x, z, ccx, ccz) && !shown) r.occludedFar++
				}
			}
			r.shownMax = maxOf(r.shownMax, shownNow)
			r.frames++
			t += 16
		}
		return r
	}

	private val walkSpin = Path({ t -> doubleArrayOf(8.3 + t * 5.6 / 1000.0, -3.7 + t * 1.3 / 1000.0) }, { t -> t / 1000.0 * 0.9 })
	private val sprintNorth = Path({ t -> doubleArrayOf(1000.5, 500.5 - t * 20.0 / 1000.0) }, { _ -> -Math.PI / 2 })
	private val flyDiagonal = Path({ t -> doubleArrayOf(-300.2 + t * 40.0 / 1000.0, 77.7 + t * 38.0 / 1000.0) }, { _ -> Math.PI / 4 })
	private val zigzag = Path({ t -> val s = t / 1000.0; doubleArrayOf(s * 11.0, if ((s.toInt() / 3) % 2 == 0) (s % 3) * 15 else 45 - (s % 3) * 15) },
		{ t -> Math.sin(t / 700.0) * 2.5 })
	private companion object { const val CAM_Y = 70.0 }

	private val paths = listOf("walk+spin" to walkSpin, "sprint" to sprintNorth, "fly" to flyDiagonal, "zigzag" to zigzag)

	@Test
	fun vanillaPathFromTheVisibleListLeavesNoGapAndNeverDrawsTwice() {
		for ((name, path) in paths) for (server in intArrayOf(14, 8)) for (rd in intArrayOf(12, 6)) {
			val r = run(SurfaceDrawnMode.VANILLA, path, 12_000, rd = rd, server = server)
			println("vanilla $name rd $rd server $server: $r")
			assertEquals(0, r.gaps, "vanilla $name rd $rd server $server gaps at ${r.gapColumns.take(5).map { SurfaceIndex.keyX(it) to SurfaceIndex.keyZ(it) }}")
			assertEquals(0, r.flips, "ownership never flips while the game has the column")

			assertEquals(0, r.doubleOther, "vanilla $name: $r")
			assertEquals(0, r.doublePreDraw, "vanilla $name: $r")
			assertTrue(r.shownMax > 50, "the game takes columns over ($r)")
		}
	}

	@Test
	fun rendererPathBehindAFakeAdapterLeavesNoGapAndReportsItsShortDoubleDraw() {
		for ((name, path) in paths) for (server in intArrayOf(14, 8)) {
			val r = run(SurfaceDrawnMode.RENDERER, path, 12_000, server = server)
			println("renderer $name server $server: $r")
			assertEquals(0, r.gaps, "renderer $name server $server gaps")
			assertEquals(0, r.flips)

			assertTrue(r.longestOtherMs <= SurfaceHandoffTimer.HOLD_MS + SurfaceBuiltWait.LOWER_WAIT_MS + 48, "renderer $name: $r")
			assertTrue(r.shownMax > 100)
		}
	}

	@Test
	fun anyGameRenderDistanceFrom2To32StaysGapFree() {

		for (rd in intArrayOf(2, 3, 4, 8, 16, 24, 32)) {
			val v = run(SurfaceDrawnMode.VANILLA, walkSpin, 3_000, rd = rd, server = 14)
			println("vanilla walk+spin rd $rd: $v")
			assertEquals(0, v.gaps, "vanilla rd $rd: $v"); assertEquals(0, v.flips, "vanilla rd $rd")
			assertEquals(0, v.doubleOther, "vanilla rd $rd"); assertEquals(0, v.doublePreDraw, "vanilla rd $rd")
			val r = run(SurfaceDrawnMode.RENDERER, walkSpin, 3_000, rd = rd, server = 14)
			println("renderer walk+spin rd $rd: $r")
			assertEquals(0, r.gaps, "renderer rd $rd: $r"); assertEquals(0, r.flips, "renderer rd $rd")
		}
	}

	@Test
	fun predictionPathStillLeavesNoGap() {
		for ((name, path) in paths) {
			val r = run(SurfaceDrawnMode.PREDICTION, path, 8_000, server = 8)
			println("prediction $name: $r")
			assertEquals(0, r.gaps); assertEquals(0, r.flips); assertEquals(0, r.doubleOther)
		}
	}

	@Test
	fun lookingAwayNeverHandsAColumnBackAndHillsKeepTheirFarCopy() {

		val spin = Path({ _ -> doubleArrayOf(8.5, 8.5) }, { t -> if (t < 4000) t / 4000.0 * 2 * Math.PI else 0.3 })
		for (mode in listOf(SurfaceDrawnMode.VANILLA, SurfaceDrawnMode.RENDERER)) {
			val r = run(mode, spin, 6000, spinCheckAt = 5600)
			println("spin $mode: $r")
			assertEquals(0, r.gaps); assertEquals(0, r.flips)
			assertTrue(r.behindStillGame > 100, "columns behind the camera stay the game's ($r)")
			assertEquals(0, r.behindFarDraws, "far terrain never takes back a seen column the game culls ($r)")
			assertEquals(0, r.behindApex, "nor draws it behind the camera ($r)")

			assertTrue(r.occludedFar > 0)
		}
	}
}
