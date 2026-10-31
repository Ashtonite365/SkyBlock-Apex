// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenCropSpeed
import dev.ashtonite365.apex.features.garden.GardenProgress

class GardenCropSpeedTest {
	@Test
	fun tools() {
		assertEquals(93, GardenCropSpeed.optimalSpeeds[GardenProgress.Crop.forTool("THEORETICAL_HOE_WARTS_3")])
		assertEquals(464, GardenCropSpeed.optimalSpeeds[GardenProgress.Crop.CACTUS])
		assertEquals(GardenProgress.Crop.entries.size, GardenCropSpeed.optimalSpeeds.size)
	}

	@Test
	fun bps() {
		assertEquals(1.8, GardenCropSpeed.bps(listOf(0L, 1000, 6000, 7000, 8000, 9000, 10000, 10500, 10900, 10999, 11000), 11000), 0.01)
	}
}
