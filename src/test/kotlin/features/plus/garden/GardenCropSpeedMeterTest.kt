// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenCropSpeedMeter
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop

class GardenCropSpeedMeterTest {
	@Test
	fun compute() {
		val r = GardenCropSpeedMeter.compute(3_000, 100, Crop.CARROT)!!
		assertEquals(30.0, r.cropsPerBlock, 1e-9)
		assertEquals(1000.0, r.fortune, 1e-9)
		assertNull(GardenCropSpeedMeter.compute(10, 0, Crop.WHEAT))
	}
}
