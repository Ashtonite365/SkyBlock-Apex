// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.garden.GardenPlots

class GardenPlotsTest {
	@Test
	fun grid() {
		assertEquals(0, GardenPlots.plotIdAt(0.0, 0.0))
		assertEquals(1, GardenPlots.plotIdAt(0.0, -100.0))
		assertEquals(3, GardenPlots.plotIdAt(100.0, 0.0))
		assertEquals(21, GardenPlots.plotIdAt(-239.0, -239.0))
		assertNull(GardenPlots.plotIdAt(300.0, 0.0))
		assertEquals(-48 to -144, GardenPlots.plotCorner(1))
		assertEquals(144 to 144, GardenPlots.plotCorner(24))
	}
}
