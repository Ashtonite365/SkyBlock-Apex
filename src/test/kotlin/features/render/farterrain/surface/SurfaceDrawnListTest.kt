// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSource
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSources
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceSectionSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceDrawnListTest {
	@Test
	fun drawnRecentlyKeepsASectionFor100msThenForgetsIt() {
		val l = SurfaceDrawnList()
		assertEquals(100L, SurfaceDrawnList.RECENT_MS)
		l.begin(1000); l.drawn(3, 4, -7)
		assertTrue(l.drawnRecently(3, 4, -7, 1000))
		assertTrue(l.drawnThisFrame(3, 4, -7))
		assertFalse(l.drawnRecently(3, 5, -7, 1000))
		l.begin(1050)
		assertFalse(l.drawnThisFrame(3, 4, -7))
		assertTrue(l.drawnRecently(3, 4, -7, 1050))
		assertTrue(l.drawnRecently(3, 4, -7, 1100))
		assertFalse(l.drawnRecently(3, 4, -7, 1101))
		l.begin(2000)
		assertEquals(0, l.size)
	}

	@Test
	fun surfaceIsTheTopSectionOrTheOneBelow() {
		val l = SurfaceDrawnList()
		l.begin(0); l.drawn(0, 4, 0)
		assertTrue(l.surfaceDrawn(0, 4, 0, 0))
		assertTrue(l.surfaceDrawn(0, 5, 0, 0), "a thin top culled at the screen edge, the ground under it drawn")
		assertFalse(l.surfaceDrawn(0, 6, 0, 0), "two sections down is not the surface")
		assertFalse(l.surfaceDrawn(0, 3, 0, 0), "only a section above the surface")
		assertTrue(l.columnDrawnThisFrame(0, 2, 6, 0))
		assertFalse(l.columnDrawnThisFrame(1, 2, 6, 0))
	}

	@Test
	fun keysDoNotCollideNearby() {
		val seen = HashSet<Long>()
		for (x in -40..40) for (z in -40..40) for (y in -4..20) assertTrue(seen.add(SurfaceDrawnList.key(x, y, z)))
		assertNotEquals(SurfaceDrawnList.key(1, 0, 0), SurfaceDrawnList.key(0, 0, 1))
	}

	private class Fake(var sections: List<IntArray>, var available: Boolean = true, var fail: Throwable? = null,
		val reason: String? = null) : SurfaceDrawnSource {
		var calls = 0
		override fun unsupportedReason(): String? = reason
		override fun collect(sink: SurfaceSectionSink): Boolean {
			calls++
			fail?.let { throw it }
			if (!available) return false
			for (s in sections) sink.drawn(s[0], s[1], s[2])
			return true
		}
	}

	@Test
	fun rendererPathReadsTheFakeAdaptersList() {
		val fake = Fake(listOf(intArrayOf(1, 4, 1), intArrayOf(2, 5, 1)))
		val logs = ArrayList<String>()
		val src = SurfaceDrawnSources({ it == SurfaceDrawnSources.RENDERER_MOD_ID }, { fake }, { m, _ -> logs.add(m) })
		val l = SurfaceDrawnList()
		l.begin(0)
		assertTrue(src.collect(l))
		assertEquals(2, l.frameSections)
		assertTrue(l.surfaceDrawn(2, 5, 1, 0))
		fake.available = false
		l.begin(16)
		assertFalse(src.collect(l))
		fake.available = true
		assertTrue(src.collect(l))
		assertTrue(src.active)
		assertTrue(logs.isEmpty(), "$logs")
	}

	@Test
	fun withoutTheModNothingIsLoadedOrAsked() {
		var loads = 0
		val logs = ArrayList<String>()
		val src = SurfaceDrawnSources({ false }, { loads++; Fake(emptyList()) }, { m, _ -> logs.add(m) })
		repeat(100) { assertFalse(src.collect(SurfaceDrawnList())) }
		assertFalse(src.active)
		assertEquals(0, loads, "the optional reader is never looked up without its mod")
		assertTrue(logs.isEmpty(), "nothing to log when the mod is simply not installed")
	}

	@Test
	fun aReaderThatBreaksFallsBackAndLogsOnce() {

		val fake = Fake(listOf(intArrayOf(0, 0, 0)))
		val logs = ArrayList<Pair<String, Throwable?>>()
		val src = SurfaceDrawnSources({ true }, { fake }, { m, t -> logs.add(m to t) })
		assertTrue(src.collect(SurfaceDrawnList()))
		fake.fail = NoSuchMethodError("getRenderLists")
		repeat(200) { assertFalse(src.collect(SurfaceDrawnList())) }
		assertEquals(2, fake.calls, "never asked again after the failure")
		assertEquals(1, logs.size)
		assertTrue(logs[0].second is NoSuchMethodError)
		assertTrue(src.failed)
	}

	@Test
	fun loadFailuresAndUntestedVersionsFallBackAndLogOnce() {
		for (case in 0..3) {
			val logs = ArrayList<String>()
			val src = when (case) {
				0 -> SurfaceDrawnSources({ true }, { throw NoClassDefFoundError("renderer class missing") }, { m, _ -> logs.add(m) })
				1 -> SurfaceDrawnSources({ true }, { null }, { m, _ -> logs.add(m) })
				2 -> SurfaceDrawnSources({ true }, { Fake(emptyList(), reason = "untested renderer version 0.10.0") }, { m, _ -> logs.add(m) })
				else -> SurfaceDrawnSources({ true }, { Fake(emptyList(), fail = IllegalStateException("render-list accessor not applied")) }, { m, _ -> logs.add(m) })
			}
			repeat(50) { assertFalse(src.collect(SurfaceDrawnList()), "case $case") }
			assertEquals(1, logs.size, "case $case: $logs")
			assertTrue(logs[0].contains("prediction path"), logs[0])
			assertFalse(src.active)
		}
	}
}
