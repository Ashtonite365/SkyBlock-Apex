// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenPests
import dev.ashtonite365.apex.util.plus.ProfitTracker
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class GardenPestsTest {
	@Test
	fun infested() {
		assertEquals(listOf(4, 12, 13), GardenPests.parseInfested(listOf("Area: Garden", " Plots: 4, 12, 13")))
		assertEquals(emptyList<Int>(), GardenPests.parseInfested(listOf("Area: Garden")))
	}

	@Test
	fun drops() {
		assertEquals("Mutant Nether Wart" to 9L, GardenPests.parseDrop("RARE DROP! Mutant Nether Wart x9 (+134)"))
		assertEquals("Slug" to 1L, GardenPests.parseDrop("PET DROP! Slug (+78)"))
		assertEquals("Not Just a Pest Vinyl" to 1L, GardenPests.parseDrop("RARE DROP! Not Just a Pest Vinyl (Cocoaleech)"))
		assertNull(GardenPests.parseDrop("You picked up a thing"))
		assertEquals("2m 5s", GardenPests.formatAgo(125_000))
	}

	private val rawRat = "§eYou received §a6x Enchanted Pumpkin §efor killing a §2Rat§e!"
	private val rawCricket = "§eYou received §a20x Enchanted Carrot §efor killing a §2Cricket§e!"
	private fun strip(s: String) = s.replace(Regex("§."), "")

	@Test
	fun kills() {
		assertEquals(Triple("Enchanted Pumpkin", 6L, "Rat"), GardenPests.parseKill("You received 6x Enchanted Pumpkin for killing a Rat!"))
		assertEquals(Triple("Enchanted Carrot", 20L, "Cricket"), GardenPests.parseKill("You received 20x Enchanted Carrot for killing a Cricket!"))
		assertEquals(Triple("Enchanted Pumpkin", 6L, "Rat"), GardenPests.parseKill(strip(rawRat)))
		assertEquals(Triple("Enchanted Carrot", 20L, "Cricket"), GardenPests.parseKill(strip(rawCricket)))
		assertEquals(Triple("Enchanted Melon", 1234L, "Earthworm"), GardenPests.parseKill("You received 1,234x Enchanted Melon for killing an Earthworm!"))
		assertNull(GardenPests.parseKill("RARE DROP! Mutant Nether Wart x9 (+134)"))
		assertNull(GardenPests.parseDrop("You received 6x Enchanted Pumpkin for killing a Rat!"))
	}

	@Test
	fun sellPriceAndTotals() {
		assertEquals(160.0, GardenPests.sellPrice(160.0, 100.0))
		assertEquals(100.0, GardenPests.sellPrice(null, 100.0))
		assertEquals(100.0, GardenPests.sellPrice(0.0, 100.0))
		assertNull(GardenPests.sellPrice(null, null))
		val saved = ProfitTracker.Data()
		val prices = mapOf("ENCHANTED_PUMPKIN" to 160.0, "ENCHANTED_CARROT" to 100.0)
		val t = ProfitTracker("Pest Profit", { saved }, { prices[it] })
		assertFalse(GardenPests.hasCounted(t.current))
		for (line in listOf(rawRat, rawCricket)) GardenPests.parseKill(strip(line))!!.let { (n, a, _) ->
			t.addItem(n.uppercase().replace(' ', '_'), a); t.addEvent() }
		assertTrue(GardenPests.hasCounted(t.current))
		assertEquals(2L, t.session.events)
		assertEquals(6 * 160.0 + 20 * 100.0, t.profit(t.session))

		val again = ProfitTracker("Pest Profit", { saved }, { prices[it] })
		assertEquals(2L, again.total.events)
		assertEquals(mapOf("ENCHANTED_PUMPKIN" to 6L, "ENCHANTED_CARROT" to 20L), again.total.items)
		again.resetSession(); again.resetTotal()
		assertFalse(GardenPests.hasCounted(again.total))
	}
}
