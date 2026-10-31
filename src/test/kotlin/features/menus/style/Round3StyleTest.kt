// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.style.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class Round3StyleTest {
	private val ms = 1_000_000L

	@Test fun forgeCornerTabsPokeOutsideTheRect() {
		val t = ForgeMetal.tabs(10, 20, 100, 60)
		assertEquals(4, t.size)
		assertTrue(t.any { it[0] < 10 && it[1] < 20 }); assertTrue(t.any { it[2] > 110 && it[3] > 80 })
		for (r in t) assertTrue(r[0] >= 10 - ForgeMetal.POKE && r[1] >= 20 - ForgeMetal.POKE && r[2] <= 110 + ForgeMetal.POKE && r[3] <= 80 + ForgeMetal.POKE)
	}

	@Test fun forgeSparksStayInsideFaintAndDeterministic() {
		val a = ForgeMetal.sparks(42L, 0, 0, 300, 200, 0xFF8A3D)
		assertEquals(a, ForgeMetal.sparks(42L, 0, 0, 300, 200, 0xFF8A3D))
		assertTrue(a.size in 3..40)
		for (s in a) {
			assertTrue(s.x - s.size >= 0 && s.y - s.size >= 0 && s.x + s.size <= 300 && s.y + s.size <= 200, "$s")
			assertTrue((s.argb ushr 24) <= ForgeMetal.SPARK_MAX_ALPHA)
		}
	}

	@Test fun skyglassGetsNoMetalForgeDoes() {
		val sky = RecordingCanvas(); ForgeMetal.plate(sky, StylePalette.SKYGLASS, 0, 0, 200, 100)
		assertTrue(sky.ops.isEmpty())
		for (mode in ForgeMode.entries) {
			val f = RecordingCanvas(); ForgeMetal.plate(f, StylePalette.of(StyleState(style = Style.FORGE, forgeMode = mode)), 0, 0, 200, 100)
			assertTrue(f.ops.any { it.startsWith("F -") }, "a tab reaches outside")
		}
	}

	@Test fun worldToMenuPlaysOpen() {
		ScreenTransitions.resetForTests()
		val now = 5_000 * ms
		assertEquals(now, ScreenTransitions.openStartNs(now))
	}

	@Test fun menuToMenuPlaysNothing() {
		ScreenTransitions.resetForTests()
		ScreenTransitions.removed(1_000 * ms)
		val now = 1_001 * ms
		assertTrue(ScreenTransitions.isSwap(now, 1_000 * ms))
		assertEquals(1.0, StyleAnim.openReveal(StyleState(), (now - ScreenTransitions.openStartNs(now)) / ms))
		assertFalse(ScreenTransitions.closeShows(screenOpen = true))
	}

	@Test fun menuToWorldPlaysClose() {
		assertTrue(ScreenTransitions.closeShows(screenOpen = false))
	}

	@Test fun serverCloseThenReopenWithinTheWindowIsASwap() {
		assertTrue(ScreenTransitions.isSwap(1_080 * ms, 1_000 * ms))
		assertFalse(ScreenTransitions.isSwap(1_300 * ms, 1_000 * ms))
		ScreenTransitions.resetForTests()
	}
}
