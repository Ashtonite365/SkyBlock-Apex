// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.MayorParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class MayorMenuTest {
	@Test
	fun mayor() {
		val d = RealDumpFixtures.load("mayor")
		val m = MayorParser.parse(d.title, d.slots)!!
		assertEquals("Mayor", m.role); assertEquals("Diaz", m.person); assertEquals(11, m.personSlot)
		assertEquals(listOf("Stock Exchange", "Volume Trading"), m.perks.map { it.name })
		assertTrue(m.perks[0].description.first().startsWith("Participate in the Stonks Auction"))
		assertTrue(m.footer.first().startsWith("The listed perks"))
		assertEquals("Year 520", m.year)
		assertEquals(listOf("Diaz", "Finnegan", "Aatrox", "Marina", "Diana"), m.votes.map { it.candidate })
		assertEquals(51.8, m.votes[0].percent); assertEquals("727,350", m.votes[0].votes)
		assertEquals(15, m.resultsSlot); assertEquals(31, m.nav["Close"])
	}

	@Test
	fun minister() {
		val d = RealDumpFixtures.load("minister")
		val m = MayorParser.parse(d.title, d.slots)!!
		assertEquals("Minister", m.role); assertEquals("Finnegan", m.person)
		assertEquals(listOf("Blooming Business"), m.perks.map { it.name })
		assertTrue(m.footer.first().startsWith("The Minister is"))
		assertEquals(5, m.votes.size)
	}

	@Test
	fun dynamicNamesAndMismatch() {
		val d = RealDumpFixtures.load("mayor")

		val renamed = d.slots.map { if (it.slot == 11) it.copy(name = "Mayor Someone") else it }
		assertEquals("Someone", MayorParser.parse("Mayor Someone", renamed)!!.person)
		assertNull(MayorParser.parse("Mayor Someone", d.slots), "title item missing")
		assertNull(MayorParser.parse("Calendar and Events", d.slots))
		assertNull(MayorParser.parse(d.title, d.slots.filter { it.slot != 11 }))
	}
}
