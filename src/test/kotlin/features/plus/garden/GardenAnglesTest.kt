// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenAngles
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop

class GardenAnglesTest {
	@Test
	fun angles() {
		assertEquals("2.80", GardenAngles.format(2.8f, 2))
		assertEquals("3", GardenAngles.format(2.8f, 0))
		assertEquals(-20f, GardenAngles.yawDiff(170f, -170f), 1e-4f)
		assertEquals(Crop.entries.size, GardenAngles.optimal.size)
		assertEquals(-135f to 0f, GardenAngles.optimal[Crop.SUGAR_CANE])
	}
}
