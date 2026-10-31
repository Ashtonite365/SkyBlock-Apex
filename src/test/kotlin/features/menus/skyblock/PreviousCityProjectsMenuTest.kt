// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.CityProjectParser
import dev.ashtonite365.apex.features.menus.skyblock.CityText
import dev.ashtonite365.apex.features.menus.skyblock.PreviousCityProjectsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PreviousCityProjectsMenuTest {
	private val d = RealDumpFixtures.load("previous-city-projects")

	@Test
	fun realDump() {
		val m = PreviousCityProjectsParser.parse(d.title, d.slots)!!
		assertEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21), m.projects.map { it.slot })
		val farm = m.projects[0]
		assertEquals("Farm Merchant's Dwelling", farm.name)
		assertEquals("Released November 2020", farm.released)
		assertEquals(listOf("4: 1 day headstart", "8: 2 days headstart", "16: 5% discount at farm merchant", "24: 10% discount at farm merchant"), farm.bonuses)
		val artist = m.projects.first { it.slot == 20 }
		assertEquals("You made 4 contributions to this project!", artist.contributed)
		assertEquals(emptyList<String>(), artist.bonuses)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
	}

	@Test
	fun screensDoNotStealEachOther() {
		assertNull(CityProjectParser.parse(d.title, d.slots))
		val p = RealDumpFixtures.load("city-project")
		assertNull(PreviousCityProjectsParser.parse(p.title, p.slots))
	}

	@Test
	fun fitLinesDropsDescriptionsFirstNeverCosts() {
		val l = listOf(CityText.Line("g", false), CityText.Line("d1", true), CityText.Line("d2", true), CityText.Line("Cost:", false), CityText.Line("x", false))
		assertEquals(listOf("g", "d1", "Cost:", "x"), CityText.fitLines(l, 4).map { it.text })
		assertEquals(listOf("g", "Cost:", "x"), CityText.fitLines(l, 2).map { it.text }, "required lines are never dropped")
	}

	@Test
	fun wrapNeverTruncates() {
		val w = CityText.wrap("§f Enchanted Cobblestone x8", 60) { it.replace(Regex("§."), "").length * 6 }
		assertEquals("EnchantedCobblestonex8", w.joinToString("") { it.replace(Regex("§."), "") }.replace(Regex("\\s+"), ""))
	}
}
