// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.IslandBrowserParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class IslandBrowserMenuTest {
	private val d = RealDumpFixtures.load("island-browser")

	@Test
	fun realDump() {
		val m = IslandBrowserParser.parse(d.title, d.slots)!!
		assertEquals(28, m.tiles.size)
		val t = m.tiles.first { it.slot == 11 }
		assertEquals(listOf("Portal Hub", "Themed", "Parkour"), t.tags)
		assertEquals(listOf("[MVP++] Sheepert_", "[VIP] prtlhub", "[ዞ] TimeDeo"), t.players)
		assertEquals(7, t.popularity); assertEquals(5, t.votes)
		assertEquals(1, m.page); assertEquals(6, m.pages); assertEquals("None", m.tags)
		assertEquals(mapOf("Next Page" to 53, "Island Category" to 50, "Close" to 49), m.nav)
	}

	@Test
	fun localSearch() {
		val m = IslandBrowserParser.parse(d.title, d.slots)!!
		assertEquals(listOf(29), IslandBrowserParser.filter(m.tiles, "yadi").map { it.slot })
		assertEquals(listOf(38), IslandBrowserParser.filter(m.tiles, "dropper").map { it.slot })
		assertEquals(m.tiles, IslandBrowserParser.filter(m.tiles, "  "))
	}

	@Test
	fun mismatch() {
		assertNull(IslandBrowserParser.parse("Fast Travel", d.slots))
		assertNull(IslandBrowserParser.parse(d.title, d.slots.filter { it.slot >= 45 }))
	}
}
