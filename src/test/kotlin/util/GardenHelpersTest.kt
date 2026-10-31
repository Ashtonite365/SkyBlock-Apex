/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenHelpers
import dev.ashtonite365.apex.features.garden.GardenProgress

class GardenHelpersTest {
	@Test
	fun visitors() {
		assertEquals("Jacob", GardenHelpers.parseVisitorArrival("Jacob has arrived on your Garden!"))
		assertEquals("Jacob", GardenHelpers.parseVisitorAccepted("OFFER ACCEPTED with Jacob (UNCOMMON)"))
		assertNull(GardenHelpers.parseVisitorArrival("hello"))
		val req = GardenHelpers.parseRequired(listOf("Some lore", "Items Required:", " Enchanted Hay Bale x3", " Wheat", "", "Rewards:"))
		assertEquals(mapOf("Enchanted Hay Bale" to 3, "Wheat" to 1), req)
		val v = GardenHelpers.Visitors(mutableMapOf("A" to mutableMapOf("Wheat" to 2), "B" to mutableMapOf("Wheat" to 3, "Carrot" to 1)))
		assertEquals(mapOf("Carrot" to 1, "Wheat" to 5), GardenHelpers.shoppingList(v))
	}

	@Test
	fun farmingAndComposter() {
		assertEquals("NETHER_STALK", GardenHelpers.cropForTool("THEORETICAL_HOE_WARTS_3")?.skyblockId)
		assertNull(GardenHelpers.cropForTool("ASPECT_OF_THE_END"))
		assertEquals(36000.0, GardenHelpers.coinsPerHour(100.0, 6.0))
		assertEquals(1200.0, GardenHelpers.parseShortNumber(" 1.2k"))
		assertEquals(1200.0 to 3_000_000.0, GardenHelpers.parseComposter(listOf("Organic Matter: 1.2k", "Fuel: 3M", "Time Left: 5m")))
	}

	@Test
	fun progress() {
		val table = listOf(30L, 50L, 80L)
		assertEquals(GardenProgress.Milestone(0, 10, 30), GardenProgress.milestoneFromTotal(table, 10))
		assertEquals(GardenProgress.Milestone(2, 20, 80), GardenProgress.milestoneFromTotal(table, 100))
		assertEquals(GardenProgress.Milestone(3, 5, null), GardenProgress.milestoneFromTotal(table, 165))
		assertEquals(14, GardenProgress.roman("XIV"))
		assertEquals(100L, GardenProgress.totalFromMenu(table, 2, listOf("Progress to Tier III: 25%", "  20/80")))
		val best = GardenProgress.bestCrop(mapOf("WHEAT" to 75L, "CARROT" to 10L)) { table }
		assertEquals(GardenProgress.Crop.WHEAT, best?.first)
		assertEquals(0.0, GardenProgress.ffNeeded(24000, GardenProgress.Crop.WHEAT), 1e-9)
		assertEquals(mapOf(GardenProgress.Crop.WHEAT to 500000L, GardenProgress.Crop.SUGAR_CANE to 1500000L),
			GardenProgress.parseThresholds("WHEAT=500k, sugar cane=1.5m, bogus=3"))
		assertEquals(5, GardenProgress.visitorCount(listOf("Visitors: (5)", "x")))
	}
}
