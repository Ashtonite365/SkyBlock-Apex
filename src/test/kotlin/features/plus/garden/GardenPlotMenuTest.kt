// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenPlotMenu
import dev.ashtonite365.apex.features.garden.GardenPlotMenu.Status

class GardenPlotMenuTest {
	@Test
	fun slots() {
		assertEquals(22, GardenPlotMenu.slotOf(0))
		assertEquals(2, GardenPlotMenu.slotOf(21))
		assertEquals(42, GardenPlotMenu.slotOf(24))
		assertEquals(4, GardenPlotMenu.plotAtSlot(31))
		assertNull(GardenPlotMenu.plotAtSlot(0))
		assertNull(GardenPlotMenu.plotAtSlot(53))
	}

	@Test
	fun status() {
		val pests = GardenPlotMenu.readPlot("Plot - 4", listOf("ൠ This plot has 5 ൠ Pests!"))
		assertEquals(5, pests.pests)
		assertEquals("4", pests.name)
		assertEquals(Status.PESTS to 5, GardenPlotMenu.status(pests, 4, null, 12))
		val locked = GardenPlotMenu.readPlot("Plot - 9", listOf("Cost:", " Compost x20"))
		assertEquals(Status.LOCKED to null, GardenPlotMenu.status(locked, 9, null, null))
		assertEquals(Status.SPRAYS to 12, GardenPlotMenu.status(GardenPlotMenu.readPlot("Plot - 1", emptyList()), 1, 1, 12))
		assertEquals(Status.CURRENT to null, GardenPlotMenu.status(GardenPlotMenu.readPlot("Plot - 1", emptyList()), 1, 1, null))
		assertEquals(1 to ("Compost" to 20L), GardenPlotMenu.costLine(listOf("Cost:", " Compost x20")))
		assertNull(GardenPlotMenu.costLine(listOf("Click to teleport!")))
	}
}
