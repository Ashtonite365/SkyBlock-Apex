// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenShops

class GardenShopsTest {
	@Test
	fun medals() {
		assertEquals(10, GardenShops.bronzeCost(listOf("Item", "", "Cost", "Gold medal", "2x Bronze medal", "", "Click to trade!")))
		assertEquals(6, GardenShops.bronzeCost(listOf("Cost", "Silver medal x3")))
		assertEquals(null, GardenShops.bronzeCost(listOf("Cost", "1,000 Copper")))
	}

	@Test
	fun copper() {
		assertEquals(1500, GardenShops.copperCost(listOf("Cost", "1,500 Copper", "")))
		assertEquals(null, GardenShops.copperCost(listOf("1,500 Copper")))
	}
}
