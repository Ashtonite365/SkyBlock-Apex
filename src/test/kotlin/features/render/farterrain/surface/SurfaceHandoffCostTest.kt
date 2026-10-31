// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceCameraMotion
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnMode
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceGameArea
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffDecider
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGrid
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceHandoffCostTest {
	private class Result(val avgUs: Double, val p99Us: Double, val maxUs: Double, val crossingAvgUs: Double)

	private fun run(rd: Int, reachChunks: Int, speed: Double, frames: Int, mode: SurfaceDrawnMode): Result {
		val motion = SurfaceCameraMotion()
		val drawn = SurfaceDrawnList()
		val handoff = SurfaceHandoffDecider(drawn = drawn)
		val area = SurfaceGameArea()
		val level = Any()
		val sectionsPerColumn = 24
		val times = LongArray(frames)
		var crossingNs = 0L
		var crossings = 0
		var lastCcx = Int.MIN_VALUE
		var lastCcz = Int.MIN_VALUE
		val held: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
		val meshes = Array(sectionsPerColumn) { Any() }
		for (f in 0 until frames) {
			val now = f * 1000L / 60
			val x = 3.3 + speed * now / 1000.0
			val z = 7.9 + speed * 0.37 * now / 1000.0
			val ccx = SurfaceHandoff.chunkOf(x); val ccz = SurfaceHandoff.chunkOf(z)
			val loaded = { cx: Int, cz: Int -> Math.abs(cx - ccx) <= rd + 1 && Math.abs(cz - ccz) <= rd + 1 }
			val t0 = System.nanoTime()

			drawn.begin(now, x, 150.0, z)
			if (mode != SurfaceDrawnMode.PREDICTION) for (cx in ccx - rd..ccx + rd) for (cz in ccz - rd..ccz + rd)
				if (SurfaceHandoff.inDisc(ccx, ccz, rd, cx, cz)) for (s in 0 until sectionsPerColumn) drawn.drawn(cx, s - 4, cz)
			motion.frame(x, z, now)
			val predict = if (motion.reliable(now)) SurfaceHandoffGeometry.Prediction(x, z, motion.vx, motion.vz) else null
			area.refresh(level, ccx, ccz, loaded)
			area.drawnFrame(drawn.frameReach(), now)
			val grid = SurfaceHandoffGrid(ccx, ccz, area.extent)
			val game = object : SurfaceHandoffDecider.Game {
				override fun loaded(x: Int, z: Int) = loaded(x, z)
				override fun inView(x: Int, z: Int) = true
				override fun probe(x: Int, z: Int, lo: Int, hi: Int, settled: Boolean, p: SurfaceHandoffDecider.Probe) {}
			}
			val frame = handoff.Frame(mode, x, 150.0, z, now, 900.0, area.radius(now), predict, mode != SurfaceDrawnMode.RENDERER, game)
			if (f % 300 == 0 || ccx != lastCcx || ccz != lastCcz) {
				val keep = grid.radius
				handoff.prune { k -> Math.abs(SurfaceIndex.keyX(k) - ccx) <= keep && Math.abs(SurfaceIndex.keyZ(k) - ccz) <= keep }
			}
			fun decision(cx: Int, cz: Int): Int {
				if (!grid.inside(cx, cz)) return SurfaceHandoffGrid.OUTSIDE
				val c = grid.get(cx, cz)
				if (c != 0) return c
				val yLo = 40 + Math.floorMod(cx * 7 + cz * 13, 50); val yHi = yLo + 30
				val d = frame.decide(cx, cz, true, yLo, yHi, yLo shr 4, (yHi - 1) shr 4)
				grid.put(cx, cz, d)
				return d
			}

			held.clear()
			var heldColumns = 0
			for (cx in ccx - rd..ccx + rd) for (cz in ccz - rd..ccz + rd) {
				if (!SurfaceHandoff.inDisc(ccx, ccz, rd, cx, cz)) continue
				for (s in 0 until sectionsPerColumn) {
					if (decision(cx, cz) and SurfaceHandoffOrder.GAME != 0) continue
					held.add(meshes[s])
					if (grid.markHeld(cx, cz)) heldColumns++
				}
			}

			for (cx in ccx - reachChunks..ccx + reachChunks) for (cz in ccz - reachChunks..ccz + reachChunks) decision(cx, cz)
			val ns = System.nanoTime() - t0
			times[f] = ns
			if (ccx != lastCcx || ccz != lastCcz) {
				if (lastCcx != Int.MIN_VALUE) { crossingNs += ns; crossings++ }
				lastCcx = ccx; lastCcz = ccz
			}
		}
		val warm = times.copyOfRange(frames / 5, frames)
		val sorted = warm.sortedArray()
		return Result(warm.average() / 1000.0, sorted[(sorted.size * 0.99).toInt()] / 1000.0, sorted.last() / 1000.0,
			if (crossings == 0) 0.0 else crossingNs / crossings / 1000.0)
	}

	@Test
	fun handoffAndPreDrawCostPerFrameIsTiny() {

		for ((rd, reach, speed) in listOf(Triple(12, 40, 5.6), Triple(12, 40, 20.0), Triple(16, 40, 40.0), Triple(32, 64, 20.0))) {
			for (mode in SurfaceDrawnMode.entries) {
			val r = run(rd, reach, speed, 1800, mode)
			println("[hand-off cost] $mode game draws $rd reach=$reach speed=$speed b/s: avg %.1f us, p99 %.1f us, max %.1f us per frame; chunk-crossing frames avg %.1f us"
				.format(r.avgUs, r.p99Us, r.maxUs, r.crossingAvgUs))

			assertTrue(r.avgUs < 5_000.0, "$mode $rd: ${r.avgUs} us per frame")
			}
		}
	}
}
