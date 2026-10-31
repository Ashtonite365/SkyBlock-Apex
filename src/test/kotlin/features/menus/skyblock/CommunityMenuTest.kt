// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.CityProjectParser
import dev.ashtonite365.apex.features.menus.skyblock.CommunityParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CommunityMenuTest {
	private val d = RealDumpFixtures.load("community-shop")

	@Test
	fun realDump() {
		val m = CommunityParser.parse(d.title, d.slots)!!
		assertEquals(listOf(1, 2, 3, 4, 5, 7), m.tabs.map { it.slot })
		assertTrue(m.tabs.first { it.slot == 1 }.selected)
		assertEquals(listOf(31, 50), m.tiles.map { it.slot })
		assertEquals("City Project", m.tiles[0].kind)
		assertEquals(listOf("Gems: 75", "Bits: 13,227", "Fame Rank: Settler"), m.account)
		assertEquals(emptyMap<String, Int>(), m.nav, "slot 49 is the account item in this dump; no Close")
	}

	@Test
	fun hubRevampIsLinkedNotDuplicated() {

		assertNull(CityProjectParser.parse(d.title, d.slots))
		val p = RealDumpFixtures.load("city-project")
		assertNull(CommunityParser.parse(p.title, p.slots))
		assertNull(CommunityParser.parse(d.title, d.slots.filter { it.slot < 9 || it.lore.isEmpty() || it.cleanName.contains("Categories") }),
			"tabs but no useful item -> vanilla")
	}

	private fun tab(selected: Int) = listOf(1 to "City Projects", 2 to "Account & Profile Upgrades", 3 to "Booster Cookie", 4 to "Bits Shop",
		5 to "♨ FIRE Sales ♨", 7 to "Hypixel Ranks").map { (s, n) -> SlotText(s, n, listOf("x", "", if (s == selected) "Currently selected!" else "Click to view!")) }

	@Test
	fun otherTabsReadTolerantly() {
		val sale = SlotText(22, "Mystery Chest", listOf("Some text", "", "Cost: 1,000 Gems", "Stock: 3/50", "Ends in: 2d 4h", "", "Click to purchase!"))
		val gem = SlotText(32, "SkyBlock Gems", listOf("You have: 75 Gems"))
		val m = CommunityParser.parse("Community Shop", tab(5) + sale + gem + SlotText(49, "Close", emptyList()))!!
		assertEquals("♨ FIRE Sales ♨", m.selectedTab)
		assertEquals(listOf(22, 32), m.tiles.map { it.slot })
		assertEquals(listOf("Cost: 1,000 Gems", "Stock: 3/50", "Ends in: 2d 4h", "Click to purchase!"), m.tiles[0].lines)
		assertEquals(mapOf("Close" to 49), m.nav)

		val bits = CommunityParser.parse("Community Shop", tab(4) + SlotText(19, "God Potion", listOf("Cost", "1,500 Bits")) + SlotText(53, "Next Page", listOf("Page 2")))!!
		assertEquals(mapOf("Next Page" to 53), bits.nav); assertEquals(listOf(19), bits.tiles.map { it.slot })
	}

	@Test
	fun ranksMergeColumns() {
		val slots = tab(7) + SlotText(11, "VIP", listOf("Price: 699 Gems")) + SlotText(20, "VIP Perks", listOf("Perk A")) +
			SlotText(12, "VIP+", listOf("Price: 1,299 Gems")) + SlotText(21, "VIP+ Perks", listOf("Perk B"))
		val m = CommunityParser.parse("Community Shop", slots)!!
		assertEquals(listOf(listOf(11, 20), listOf(12, 21)), m.tiles.map { it.slots })
		assertEquals(listOf("Price: 699 Gems", "", "§fVIP Perks", "Perk A"), m.tiles[0].lore)
	}
}
