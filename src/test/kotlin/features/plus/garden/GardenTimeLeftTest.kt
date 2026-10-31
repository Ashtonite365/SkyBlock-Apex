// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenProgress

class GardenTimeLeftTest {
	@Test
	fun timeLeft() {
		assertEquals(null, GardenProgress.timeLeft(100, null))
		assertEquals("2m 0s", GardenProgress.timeLeft(2000, 1000.0))
		assertEquals("1h 30m", GardenProgress.timeLeft(90_000, 1000.0))
		assertEquals("30s", GardenProgress.timeLeft(500, 1000.0))
	}
}

class GardenWarpsTest {
	@Test
	fun commands() {
		assertEquals("warp garden", dev.ashtonite365.apex.features.plus.garden.GardenWarps.commandFor(true, false, false))
		assertEquals("tptoplot barn", dev.ashtonite365.apex.features.plus.garden.GardenWarps.commandFor(false, false, true))
		assertEquals(null, dev.ashtonite365.apex.features.plus.garden.GardenWarps.commandFor(false, false, false))
	}
}

class GardenSensitivityTest {
	@Test
	fun reduce() {
		assertEquals(0.125, dev.ashtonite365.apex.features.garden.GardenSensitivity.reduced(0.5, 4))
	}
}
