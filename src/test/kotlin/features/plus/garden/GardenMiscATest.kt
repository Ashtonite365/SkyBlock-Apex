// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenMiscA as A

class GardenMiscATest {
	@Test
	fun numbers() {
		assertEquals("12", A.slotNumber("Crop Milestones", "Wheat XII", emptyList()))
		assertEquals("6", A.slotNumber("Crop Upgrades", "Wheat", listOf("Current Tier: 6/9")))
		assertEquals("25", A.slotNumber("Composter Upgrades", "Speed XXV", emptyList()))
		assertEquals(null, A.slotNumber("Other", "Wheat XII", emptyList()))
	}

	@Test
	fun uptimeAndCropsIn() {
		assertEquals(5000L, A.addGap(5000))
		assertEquals(0L, A.addGap(40_000))
		assertEquals(1500L, A.cropsIn(10, 150.0))
	}
}
