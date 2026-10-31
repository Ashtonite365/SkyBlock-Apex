// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.support

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.CommunityParser
import dev.ashtonite365.apex.features.menus.skyblock.CommunityTile
import dev.ashtonite365.apex.features.menus.support.MenuSearch

class MenuSearchTest {
	private val tiles = listOf("§6God Potion", "§aKat Flower", "§5Heat Core", "§fJumbo Backpack Upgrade", "§9Kismet Feather").mapIndexed { i, n ->
		CommunityTile(10 + i, n, null, listOf("Cost", "1,500 Bits"))
	}
	private fun names(q: String) = CommunityParser.filterBits(tiles, q).map { MenuSearch.normal(it.name) }

	@Test fun bitsShopFilterByName() {
		assertEquals(5, names("").size); assertEquals(5, names("   ").size)
		assertEquals(listOf("god potion"), names("god"))
		assertEquals(listOf("god potion"), names("GOD POT"))
		assertEquals(listOf("kat flower", "kismet feather"), names("ER"))
		assertEquals(listOf("jumbo backpack upgrade"), names("§lbackpack"))
		assertTrue(names("bits").isEmpty())
		assertTrue(CommunityParser.isBitsShop("Bits Shop")); assertFalse(CommunityParser.isBitsShop("City Projects")); assertFalse(CommunityParser.isBitsShop(null))
	}

	@Test fun typingRules() {
		val s = MenuSearch(maxLength = 5)
		"Heat Core".forEach { s.type(it.code) }
		assertEquals("Heat ", s.query)
		s.clear(); s.type(8); s.type(13); assertEquals("", s.query)

		assertFalse(s.focused)
	}
}
