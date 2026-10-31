// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuiltWait
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffOrder
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceHandoffBuiltTest {
	@Test
	fun lowerSectionsGetAShortWaitOnly() {
		val w = SurfaceBuiltWait()
		assertEquals(100L, SurfaceBuiltWait.LOWER_WAIT_MS)
		assertFalse(w.built(1, 0, topBuilt = false, allBuilt = false))
		assertTrue(w.built(1, 0, topBuilt = true, allBuilt = true))
		assertFalse(w.built(2, 1000, true, false))
		assertFalse(w.built(2, 1099, true, false))
		assertTrue(w.built(2, 1100, true, false))

		assertFalse(w.built(2, 1116, false, false))
		assertFalse(w.built(2, 1132, true, false))
		assertTrue(w.built(2, 1232, true, false))
		w.release(2)
		assertEquals(0, w.size)
	}

	private class Run(val farFacesInside: Double, val bothColumns: Double, val stuckColumns: Int)

	private fun run(hidesUnseen: Boolean, allSections: Boolean, seconds: Int = 12, rd: Int = 12): Run {
		val faces = 400
		val timer = SurfaceHandoffTimer()
		val wait = SurfaceBuiltWait()
		val since = HashMap<Long, Long>()
		var farFaces = 0L
		var both = 0L
		var frames = 0
		val stuck = HashSet<Long>()
		var ms = 0L
		while (ms <= seconds * 1000L) {
			val x = 8.0 + if (ms < 6000) 0.0 else (ms - 6000) * 4.3 / 1000.0
			val z = 8.0
			val limit = SurfaceHandoffGeometry.limit(rd)
			val ccx = SurfaceHandoff.chunkOf(x); val ccz = SurfaceHandoff.chunkOf(z)
			val loaded = { _: Int, _: Int -> true }
			val measure = ms >= 2000 && (ms < 5900 || ms >= 8000)
			if (measure) frames++
			for (cx in ccx - rd - 1..ccx + rd + 1) for (cz in ccz - rd - 1..ccz + rd + 1) {
				val k = SurfaceIndex.key(cx, cz)
				val present = SurfaceHandoffGeometry.gameOwns(cx, cz, x, z, rd, limit) && SurfaceHandoff.fallbackOwned(ccx, ccz, rd, cx, cz, loaded)
				if (!present) { since.remove(k); timer.release(k); wait.release(k); continue }
				val t = since.getOrPut(k) { ms }
				val topBuilt = ms - t >= 48
				val hidden = hidesUnseen && Math.floorMod(cx * 31 + cz * 17, 3) != 0
				val allBuilt = topBuilt && !hidden
				val built = if (allSections) allBuilt else wait.built(k, ms, topBuilt, allBuilt)
				val shown = timer.update(k, ms, built, 1, true)
				val preDraw = shown && SurfaceHandoffGeometry.outerRings(cx, cz, x, z, rd, limit)
				val d = SurfaceHandoffOrder.decide(true, shown, preDraw)
				if (measure) {
					if (d and SurfaceHandoffOrder.APEX != 0) farFaces += faces
					if (d == SurfaceHandoffOrder.BOTH) both++
				}
				if (ms == seconds * 1000L && !shown && ms - t > 1000) stuck.add(k)
			}
			ms += 16
		}
		return Run(farFaces.toDouble() / frames, both.toDouble() / frames, stuck.size)
	}

	@Test
	fun columnsWithNeverBuiltHiddenSectionsAreStillHandedOff() {
		val oldHidden = run(hidesUnseen = true, allSections = true)
		val newHidden = run(hidesUnseen = true, allSections = false)
		val newAll = run(hidesUnseen = false, allSections = false)
		println(("far-terrain faces drawn inside the render distance per frame: old rule %.0f, new rule %.0f (renderer building everything: %.0f); " +
			"both-drawn edge columns per frame %.1f; columns never handed off: old %d, new %d")
				.format(oldHidden.farFacesInside, newHidden.farFacesInside, newAll.farFacesInside, newHidden.bothColumns, oldHidden.stuckColumns, newHidden.stuckColumns))
		assertTrue(oldHidden.stuckColumns > 200, "the old rule reproduces the stuck columns (${oldHidden.stuckColumns})")
		assertEquals(0, newHidden.stuckColumns)

		assertTrue(newHidden.farFacesInside <= newAll.farFacesInside * 1.05 + faces, "${newHidden.farFacesInside} vs ${newAll.farFacesInside}")
		assertTrue(oldHidden.farFacesInside > newHidden.farFacesInside * 2)
	}

	private val faces = 400
}
