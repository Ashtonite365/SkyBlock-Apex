// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns.Run
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceDrawRunsTest {
	@Test
	fun regionsAndSlots() {
		assertEquals(0, SurfaceRegions.regionOf(7))
		assertEquals(-1, SurfaceRegions.regionOf(-1))
		assertEquals(63, SurfaceRegions.slot(-1, -1))
		assertEquals(9, SurfaceRegions.slot(1, 1))
		assertEquals(setOf(SurfaceIndex.key(0, 0)), SurfaceRegions.affectedRegions(3, 3))
		assertEquals(4, SurfaceRegions.affectedRegions(0, 0).size)
		assertEquals(2, SurfaceRegions.affectedRegions(7, 3).size)
	}

	@Test
	fun runsMergeContiguousChunksAndSkipVanillaOnes() {
		val count = IntArray(SurfaceRegions.SLOTS)
		count[0] = 8; count[1] = 4; count[2] = 0; count[3] = 12; count[10] = 4
		val start = IntArray(SurfaceRegions.SLOTS).also { var a = 0; for (i in it.indices) { it[i] = a; a += count[i] } }

		assertEquals(listOf(Run(0, 28)), SurfaceDrawRuns.runs(start, count) { false })

		assertEquals(listOf(Run(0, 8), Run(12, 16)), SurfaceDrawRuns.runs(start, count) { it == 1 })
		assertEquals(listOf(Run(0, 24)), SurfaceDrawRuns.runs(start, count) { it == 10 })
		assertTrue(SurfaceDrawRuns.runs(start, count) { true }.isEmpty())
	}

	@Test
	fun statsLinesAndDump() {
		val s = SurfaceStats()
		s.resetVisit("hub")
		s.onRegionLoaded("r.0.0.asr", 12, 3.5)
		s.onIslandLoaded("hypixel/hub", 12, 1, 4.0)
		s.onMeshBuilt("0,0", 2.0, 100)
		s.onSaved("r.0.0.asr", 12, 2048)
		val lines = s.lines()
		assertTrue(lines.any { "hub" in it })
		assertTrue(lines.any { it.startsWith("mesh:") && "1 built" in it })
		val dump = s.dump(listOf("header"))
		assertTrue(dump.startsWith("header"))
		assertTrue("disk load region r.0.0.asr" in dump)
		assertTrue("save region r.0.0.asr" in dump)
		repeat(SurfaceStats.MAX_EVENTS + 10) { s.event("x$it") }
		assertEquals(SurfaceStats.MAX_EVENTS, s.recentEvents().size)
	}
}
