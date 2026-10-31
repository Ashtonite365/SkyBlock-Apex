// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.garden.SprayTracker

class SprayTrackerTest {
	@Test
	fun parse() {
		assertEquals("12" to "Compost", SprayTracker.parse("SPRAYONATOR! You sprayed Plot - 12 with Compost!"))
		assertEquals("Wheat Farm" to "Honey Jar", SprayTracker.parse("SPRAYONATOR! You sprayed Plot - Wheat Farm with Honey Jar!"))
		assertEquals("7" to "Plant Matter", SprayTracker.parse("SPRAYONATOR! You sprayed Plot - 7 with 3 Plant Matter!"))
		assertNull(SprayTracker.parse("You sprayed nothing"))
	}

	@Test
	fun misc() {
		assertEquals(45, SprayTracker.durationMinutes("JUICY_SPRAYONATOR"))
		assertEquals(30, SprayTracker.durationMinutes(null))
		assertEquals("2:05", SprayTracker.formatRemaining(125_000))
	}
}
