// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenLanes

class GardenLanesTest {
	@Test
	fun lane() {
		val l = GardenLanes.laneFrom(-100.0, 5.0, 90.0, 7.0)
		assertEquals(GardenLanes.Lane(true, -100.0, 90.0), l)
		assertEquals(40.0, GardenLanes.distanceToEnd(l, 50.0, 10.0))
		assertEquals(150.0, GardenLanes.distanceToEnd(l, 50.0, -10.0))
		assertEquals(null, GardenLanes.distanceToEnd(l, 95.0, 10.0))
		assertEquals(false, GardenLanes.laneFrom(0.0, 0.0, 1.0, 50.0).alongX)
	}

	@Test
	fun noBreakItems() {
		assertTrue(GardenLanes.isNoBreakItem("ROD_OF_THE_SEA"))
		assertTrue(GardenLanes.isNoBreakItem("SPRAYONATOR"))
		assertFalse(GardenLanes.isNoBreakItem("THEORETICAL_HOE_WHEAT_3"))
	}
}
