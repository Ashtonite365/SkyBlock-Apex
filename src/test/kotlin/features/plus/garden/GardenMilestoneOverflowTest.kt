// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenProgress
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop

class GardenMilestoneOverflowTest {
	private val table = listOf(10L, 20L, 30L)

	@Test
	fun overflow() {
		assertEquals(GardenProgress.Milestone(3, 15, null), GardenProgress.milestoneFromTotal(table, 75))
		assertEquals(GardenProgress.Milestone(3, 15, 30), GardenProgress.milestoneFromTotal(table, 75, true))
		assertEquals(GardenProgress.Milestone(5, 5, 30), GardenProgress.milestoneFromTotal(table, 125, true))
		assertEquals(GardenProgress.Milestone(1, 5, 20), GardenProgress.milestoneFromTotal(table, 15, true))
	}

	@Test
	fun goals() {
		assertEquals(mapOf(Crop.WHEAT to 50, Crop.NETHER_WART to 46), GardenProgress.parseGoals("wheat=50, Nether Wart = 46, bad=x"))
		assertEquals(35L, GardenProgress.cropsToTier(table, 55, 4))
		assertNull(GardenProgress.cropsToTier(table, 200, 4))
	}
}
