// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenContests
import dev.ashtonite365.apex.features.garden.GardenContests.Bracket

class GardenContestsTest {
	@Test
	fun brackets() {
		val lore = listOf("Carrot Contest", " GOLD (Top 5%): 1,234,567", " BRONZE (Top 60%): 100,000", "x")
		assertEquals(mapOf(Bracket.GOLD to 1234567L, Bracket.BRONZE to 100000L), GardenContests.parseBrackets(lore))
	}

	@Test
	fun active() {
		val tab = listOf("Garden", "Jacob's Contest: 15m left", " ☘ Cocoa Beans 15m14s", " ○ Wheat", " ○ Carrot")
		assertEquals("15m" to listOf("Cocoa Beans", "Wheat", "Carrot"), GardenContests.parseActive(tab))
		assertEquals(null, GardenContests.parseActive(listOf("Garden")))
	}

	@Test
	fun summary() {
		assertEquals(true, GardenContests.summary(2400, 120_000).contains("20.00"))
	}
}
