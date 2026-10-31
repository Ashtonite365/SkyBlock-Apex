// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CollectionsTest {
	@Test fun realMainPage() {
		val d = RealDumpFixtures.load("collections-main")
		val p = CollectionsParser.parse(d.title, d.slots) as CollPage.Main
		assertEquals(listOf("Farming Collections", "Mining Collections", "Combat Collections", "Foraging Collections",
			"Fishing Collections", "Boss Collections", "Rift Collections"), p.categories.map { it.name })
		assertEquals(76.7, p.header!!.unlocked!!.pct)
		assertEquals(69.0, p.header!!.unlocked!!.current); assertEquals(90.0, p.header!!.unlocked!!.max)
		val boss = p.categories.first { it.name.startsWith("Boss") }
		assertEquals(7.0, boss.progress!!.current); assertEquals(8.0, boss.progress!!.max)
		assertEquals(0.0, p.categories.last().progress!!.fraction)
		val nav = p.nav.map { it.name }
		assertTrue(nav.containsAll(listOf("Go Back", "Close", "Crafted Minions", "Show Collection Rankings")), nav.toString())
	}

	@Test fun realCraftedMinions() {
		val d = RealDumpFixtures.load("crafted-minions-1")
		val p = CollectionsParser.parse(d.title, d.slots) as CollPage.Minions
		assertEquals(1 to 3, p.page)
		assertEquals(456 to 719, p.crafted); assertEquals(27 to 32, p.limit)
		val cobble = p.minions.first { it.name == "Cobblestone Minion" }
		assertEquals((1..9).toSet(), cobble.crafted); assertEquals(12, cobble.maxTier)
		assertTrue(p.minions.first { it.name == "Red Sand Minion" }.none)
		assertTrue(p.nav.any { it.name == "Next Page" })
		val last = CollectionsParser.parse(RealDumpFixtures.load("crafted-minions-3").title, RealDumpFixtures.load("crafted-minions-3").slots) as CollPage.Minions
		assertTrue(last.nav.any { it.name == "Previous Page" }); assertEquals(5, last.minions.size)
	}

	private fun s(slot: Int, name: String, vararg lore: String) = SlotText(slot, name, lore.toList())

	@Test fun tolerantCategoryPage() {
		val slots = listOf(
			s(4, "Farming Collections", "View your Farming Collections!", "", "Collections Unlocked: 85%", "      17/20"),
			s(19, "Wheat XI", "View all your Wheat Collection", "", "Total Collected: 1,234,567", "", "Click to view!"),
			s(20, "Carrot VIII", "", "Progress to Carrot IX: 45.5%", "           45.5k/100k", "", "Total Collected: 145,500"),
			s(21, "Cocoa Beans", "Find this item to add it to your", "collection and unlock collection", "rewards!"),
			s(48, "Go Back", "To Collections"), s(49, "Close"))
		val p = CollectionsParser.parse("Farming Collections", slots) as CollPage.Category
		assertEquals("Farming", p.category)
		assertEquals(listOf(TileState.MAXED, TileState.IN_PROGRESS, TileState.LOCKED), p.items.map { it.state })
		val carrot = p.items[1]
		assertEquals("Carrot", carrot.base); assertEquals(8, carrot.tier)
		assertEquals(45_500.0, carrot.progress!!.current); assertEquals(100_000.0, carrot.progress!!.max)
		assertEquals(listOf("In progress", "Maxed", "Locked"), CollectionsParser.groups(p.items).map { it.first })
		assertEquals(2, p.nav.size)
	}

	@Test fun tolerantItemPage() {
		val slots = listOf(
			s(4, "Wheat", "Total Collected: 60,000"),
			s(18, "Wheat I", "Progress: 100%", "   50/50", "", "Rewards:", "  Wheat Minion Recipes", "  +4 SkyBlock XP", "", "UNLOCKED"),
			s(19, "Wheat II", "Progress: 100%", "   100/100"),
			s(20, "Wheat III", "Progress: 12%", "   30k/250k", "", "Rewards:", "  Enchanted Bread Recipe"),
			s(48, "Go Back", "To Farming Collections"))
		val p = CollectionsParser.parse("Wheat Collection", slots) as CollPage.Item
		assertEquals("Wheat", p.item)
		assertEquals(listOf(1, 2, 3), p.tiers.map { it.tier })
		assertEquals(2, p.unlockedTiers)
		assertEquals("Wheat III", p.next!!.name)
		assertEquals(listOf("Wheat Minion Recipes", "+4 SkyBlock XP"), p.tiers[0].details)
		assertEquals(TileState.UNLOCKED, CollectionsParser.stateFromItem("lime_stained_glass_pane"))
		assertEquals(TileState.LOCKED, CollectionsParser.stateFromItem("red_stained_glass_pane"))
	}

	@Test fun titles() {
		for (t in listOf("Farming Collections", "Boss Collections", "Rift Collections", "(2/3) Crafted Minions"))
			assertTrue(CollectionsParser.PAGES.containsMatchIn(t), t)
		assertTrue(CollectionsParser.ITEM_TITLES.containsMatchIn("Wheat Collection")); assertFalse(CollectionsParser.PAGES.containsMatchIn("Wheat Collection"))
		assertFalse(CollectionsParser.PAGES.containsMatchIn("Collections"))
		assertTrue(CollectionsParser.MAIN.matches("Collections"))
		assertTrue(CollectionsParser.ITEM_TITLES.containsMatchIn("Enchanted Brown Mushroom Collect"))
		assertFalse(CollectionsParser.PAGES.containsMatchIn("Quartz Minion Recipes"))
		assertNull(CollectionsParser.parse("Collections", emptyList()))
	}

	@Test fun loreHelpers() {
		assertEquals(644.0 to 1000.0, LoreParse.fraction("                           644/1k"))
		assertEquals("Wheat" to 12, LoreParse.splitTier("Wheat XII"))
		assertEquals("Hard Stone" to null, LoreParse.splitTier("Hard Stone"))
		assertEquals(4, LoreParse.roman("IV")); assertNull(LoreParse.roman("Stone"))
		assertEquals(3, TileLayout.cols(350, 112))
		assertEquals(12 + 2 * 33 + 12 + 33, TileLayout.height(listOf(5, 1), 3, 30))
	}
}

class LevelingTitleExclusionTest {
	@Test fun taskPagesNotTakenOver() {
		for (t in listOf("Tasks ➜ Core", "Core ➜ Collections", "Core ➜ Boss Collections", "Starter ➜ Skills", "SkyBlock Leveling", "Guide ➜ Starter"))
			assertFalse(CollectionsParser.MAIN.matches(t) || CollectionsParser.PAGES.matches(t), t)
		assertTrue(CollectionsParser.MAIN.matches("Collections"))
		assertTrue(CollectionsParser.PAGES.matches("Mining Collections"))
	}
}
