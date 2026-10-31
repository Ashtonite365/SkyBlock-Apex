// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenAnitaFortune
import dev.ashtonite365.apex.features.garden.GardenAnitaFortune.Price

class GardenAnitaFortuneTest {
	@Test
	fun bundledCosts() {
		val p = GardenAnitaFortune.levelPrices()
		assertTrue(p.size >= 10, "bundled AnitaUpgradeCosts.json")
		assertEquals(Price(1, 50), p[2])
	}

	@Test
	fun cost() {
		val prices = mapOf(1 to Price(1, 0), 2 to Price(1, 50), 3 to Price(1, 50), 4 to Price(2, 100))
		val r = GardenAnitaFortune.costToMax(listOf("You have: +4☘ Farming Fortune", "Cost", "Jacob's Ticket x25", "Click to trade!"), prices)!!
		assertEquals(1, r.tier); assertEquals(4, r.medals); assertEquals(100, r.tickets)
	}
}
