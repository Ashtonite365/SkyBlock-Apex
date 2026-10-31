package dev.ashtonite365.apex.test.features.menus.guides

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.guides.DragonGuideMenu
import dev.ashtonite365.apex.features.menus.guides.DragonGuideParser
import dev.ashtonite365.apex.features.menus.guides.EnchantGuideMenu
import dev.ashtonite365.apex.features.menus.guides.ReforgeGuideMenu
import dev.ashtonite365.apex.features.menus.guides.ReforgeGuideParser
import dev.ashtonite365.apex.features.menus.guides.SkyBlockGuidesMenu
import dev.ashtonite365.apex.features.menus.guides.SkyBlockGuidesParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkyBlockGuidesMenusTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun load(n: String) = RealDumpFixtures.load(n)

	@Test fun titlesAndRouting() {
		MenuRegistry.register(SkyBlockGuidesMenu); MenuRegistry.register(DragonGuideMenu); MenuRegistry.register(ReforgeGuideMenu); MenuRegistry.register(EnchantGuideMenu)
		assertSame(SkyBlockGuidesMenu, MenuRegistry.match("SkyBlock Guides"))
		assertSame(DragonGuideMenu, MenuRegistry.match("Dragon Guide"))
		for (t in listOf("Reforge Guide", "(1/2) Basic Reforges", "(2/2) Basic Reforges", "(1/4) Advanced Reforges", "(4/4) Advanced Reforges"))
			assertSame(ReforgeGuideMenu, MenuRegistry.match(t), t)

		assertSame(EnchantGuideMenu, MenuRegistry.match("(1/6) Enchantment Guide"))

		assertNull(MenuRegistry.match("Astute"))
		assertSame(ReforgeGuideMenu, MenuRegistry.match("Astute", load("reforge-astute-1345-66").slots))
		assertSame(ReforgeGuideMenu, MenuRegistry.match("Amber Material", load("reforge-amber-1348-85").slots))
		for (t in listOf("Reforge Stones ➜ Dragon Claw", "(1/3) Oddities ➜ Reforge Stones", "SkyBlock XP Guide")) assertNull(MenuRegistry.match(t), t)
	}

	@Test fun skyblockGuides() {
		val d = load("skyblock-guides-2040-25")
		val m = SkyBlockGuidesParser.parse(d.title, d.slots)!!
		assertEquals("SkyBlock Guides", m.heading)
		assertEquals(listOf("Dragon Guide", "Enchantment Guide", "Handy Block Guide", "Reforge Guide", "SkyBlock XP Guide", "Bait Guide", "Dye Guide",
			"Honey Guide", "Rod Part Guide", "Sea Creature Guide", "Weather Information"), m.allTiles.map { it.name })
		assertEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22), m.allTiles.map { it.slot })
		assertTrue(m.allTiles.all { it.clickable && !it.delayed && it.button == 0 })
		assertEquals("About" to "A collection of all guides from across SkyBlock.", m.chips.single())
		assertEquals(listOf("Close" to 49), m.nav)
		assertTrue(m.allTiles.first { it.name == "Bait Guide" }.info.single().contains("Talk to Angler Angus at the Fishing Outpost in the Hub to unlock this!"))
		assertTrue(m.allTiles.first { it.name == "Sea Creature Guide" }.info.single().contains("Your Fishing: Level XIV"))
	}

	@Test fun dragonGuide() {
		val d = load("dragon-guide-1354-72")
		val m = DragonGuideParser.parse(d.title, d.slots)!!
		assertEquals(listOf("Summoning Eyes", "Dragon Spawning"), m.sections[0].tiles.map { it.name })
		val dragons = m.sections[1].tiles
		assertEquals(listOf("Young", "Old", "Strong", "Wise", "Unstable", "Protector", "Superior").map { "$it Dragons" }, dragons.map { it.name })
		assertEquals(listOf("Summoning Eyes Collected" to "13 (Top 3.23%)", "Dragons Summoned" to "0"), m.chips)
		val young = dragons[0].info.single()
		for (part in listOf("Fastest Dragons.", "Kills: 11 (Top 1.43%)", "Highest Rank: 6th", "Fastest Kill: 23s", "Most Damage: 430,184 (Top 31.03%)",
				"Amount Summoned: 0", "Rankings may take some time to refresh.")) assertTrue(young.contains(part), part)
		assertTrue(dragons[6].info.single().contains("Most Damage: 11,566 (Top 77.77%)"))
		assertEquals(listOf("Back (SkyBlock Guides)" to 48, "Close" to 49), m.nav)
		assertFalse(m.allTiles.any { it.clickable })
	}

	@Test fun reforgeGuideMain() {
		val d = load("reforge-guide-1345-62")
		val m = ReforgeGuideParser.parse(d.title, d.slots)!!
		assertEquals(listOf("Basic Reforges" to 21, "Advanced Reforges" to 23), m.allTiles.map { it.name to it.slot })
		assertTrue(m.allTiles.all { it.clickable })
		assertEquals("View all Reforges and their effects! · Also accessible via /reforges", m.chips.first().second)
	}

	@Test fun reforgeLists() {
		val b1 = ReforgeGuideParser.parse(load("basic-reforges-1-1345-63").title, load("basic-reforges-1-1345-63").slots)!!
		assertEquals(28, b1.allTiles.size)
		assertEquals("Applies to Melee Weapon or Fishing Rod.", b1.allTiles.first { it.name == "Epic" }.info.single())
		assertEquals(listOf("Back (Reforge Guide)" to 48, "Close" to 49, "Next Page" to 53, "Filter: None" to 51), b1.nav)
		assertTrue(b1.chips.contains("Page" to "1/2"))
		val b2 = load("basic-reforges-2-1345-64").let { ReforgeGuideParser.parse(it.title, it.slots)!! }
		assertTrue(b2.nav.contains("Previous Page" to 45)); assertFalse(b2.nav.any { it.first == "Next Page" })
		val a1 = load("advanced-reforges-1-1348-78").let { ReforgeGuideParser.parse(it.title, it.slots)!! }
		val amber = a1.allTiles.first { it.name == "Amber Material" }.info.single()
		assertTrue(amber.contains("Applies the Ambered reforge when combined with a Mining Tool.") && amber.contains("Find Goblins from mining 40% more often."))
		assertTrue(a1.nav.any { it.first == "Filter: None" })
		val a4 = load("advanced-reforges-4-1348-81").let { ReforgeGuideParser.parse(it.title, it.slots)!! }
		assertTrue(a4.allTiles.isNotEmpty()); assertTrue(a4.chips.contains("Page" to "4/4"))
	}

	@Test fun rarityScaling() {
		val a = load("reforge-astute-1345-66")
		assertFalse(ReforgeGuideParser.TITLE.matches(a.title))
		val m = ReforgeGuideParser.parse(a.title, a.slots)!!
		assertEquals(listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE").map { "$it Reforge Stats" }, m.sections[0].tiles.map { it.name })

		assertEquals("+1 \uE010 Health +1 \uE008 Defense +3 \uE003 Intelligence", m.sections[0].tiles[0].info.single())
		assertEquals(listOf("Reforge Bonus"), m.sections[1].tiles.map { it.name })
		assertEquals(listOf("Back (Basic Reforges)" to 48, "Close" to 49), m.nav)
		val amber = load("reforge-amber-1348-85").let { ReforgeGuideParser.parse(it.title, it.slots)!! }
		assertEquals("+200 \uE015 Mining Speed · Cost to Apply: 250,000 Coins · Can be applied in an Anvil!", amber.sections[0].tiles.last().info.single())
		assertEquals("Ambered Bonus", amber.sections[1].tiles.single().name)

		assertNull(ReforgeGuideParser.parse("Astute", load("dragon-guide-1354-72").slots))
		assertFalse(ReforgeGuideParser.isDetail(load("basic-reforges-1-1345-63").slots))
	}
}
