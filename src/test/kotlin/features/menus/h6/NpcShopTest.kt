package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.NpcShopParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class NpcShopTest {
	private fun load(n: String) = RealDumpFixtures.load(n).let { it.title to it.slots.filter { s -> s.cleanName.isNotEmpty() } }
	private fun parse(n: String) = load(n).let { (t, s) -> NpcShopParser.parse(t, s) }

	@Test fun costParser() {
		val c = NpcShopParser.cost(listOf("COMMON", "", "Cost", "5,000 Coins", "Enchanted Feather x8", "Griffin Feather", "", "Click to trade!"))!!
		assertEquals(5000.0, c.coins)
		assertEquals(listOf("Enchanted Feather" to 8, "Griffin Feather" to 1), c.items)
		assertNull(NpcShopParser.cost(listOf("COMMON", "Click to trade!")))
		assertEquals(640L, NpcShopParser.stock(listOf("Cost", "4 Coins", "", "Stock", "640 remaining", "")))
	}

	@Test fun beaCoinsNoStackOption() {
		val m = parse("shop-bea")!!
		val names = m.allTiles.map { it.name.replace(Regex("§."), "") }
		assertEquals(listOf("[Lvl 100] Bee", "Small Honey Dipper", "Medium Honey Dipper", "Large Honey Dipper", "Giant Honey Dipper"), names)
		val giant = m.allTiles.last()
		assertEquals("§6" + dev.ashtonite365.apex.features.menus.support.MenuText.coins(500000.0) + " coins", giant.info.first())
		assertTrue(giant.delayed); assertEquals(0, giant.button); assertNull(giant.shiftButton)
		assertFalse(m.tabs)
	}

	@Test fun beaCostText() {
		val (_, s) = load("shop-bea")
		val o = NpcShopParser.detect(s)!!.offers.first { it.name == "Giant Honey Dipper" }
		assertEquals(500000.0, o.cost.coins); assertTrue(o.cost.items.isEmpty()); assertFalse(o.stackable); assertEquals("LEGENDARY", o.rarity)
	}

	@Test fun builderHubSectionsBecomeTabsInSubShops() {
		val hub = parse("shop-builder")!!
		assertEquals(listOf("Woodworking", "Rocks & Bricks", "Green Thumb", "Variety"), hub.allTiles.map { it.name })
		assertEquals(listOf("Woodworking", "Rocks & Bricks", "Green Thumb", "Variety"), NpcShopParser.hubSections["Builder"])
		for ((f, title) in listOf("shop-builder-woodworking" to "Woodworking", "shop-builder-variety" to "Variety")) {
			val m = parse(f)!!
			assertTrue(m.tabs, f)
			assertEquals(listOf("Woodworking", "Rocks & Bricks", "Green Thumb", "Variety"), m.sections.map { it.title })
			assertEquals(title, m.sections[m.selectedTab].title)
			assertTrue(m.sections[m.selectedTab].tiles.isNotEmpty())
			assertEquals("Builder", m.chips.first { it.first == "Shop" }.second)
			assertTrue(m.nav.contains("Back (Builder)" to 48))
		}
	}

	@Test fun madRedstoneStackAndStock() {
		val (_, s) = load("shop-builder-mad-redstone")
		val shop = NpcShopParser.detect(s)!!
		assertEquals(13, shop.offers.size)
		val dust = shop.offers.first { it.name == "Redstone Dust" }
		assertEquals(4.0, dust.cost.coins); assertEquals(640L, dust.stock); assertTrue(dust.stackable)
		val t = parse("shop-builder-mad-redstone")!!.allTiles.first { it.name.endsWith("Redstone Dust") }
		assertEquals(1, t.shiftButton); assertEquals(0, t.button); assertTrue(t.delayed)
		assertEquals(49, shop.sellSlot); assertNull(shop.buyback)
	}

	@Test fun woolWeaverPages() {
		val v = NpcShopParser.detect(load("shop-wool-weaver-vibrant").second)!!
		assertEquals(1, v.page); assertEquals(53, v.nextPage)
		assertTrue(v.offers.size > 20)
		assertTrue(parse("shop-wool-weaver-vibrant")!!.nav.contains("Next (Page 2)" to 53))
		assertNotNull(parse("shop-wool-weaver-cool"))
	}

	@Test fun nonShopsAreNotDetected() {
		for (n in listOf("your-bags", "stats-equipment", "calendar-events", "quiver", "sack-of-sacks")) {
			assertNull(NpcShopParser.detect(load(n).second), n)
		}

		assertNull(NpcShopParser.detect(load("booster-cookie").second))
	}

	@Test fun ameliaLockedOffers() {
		val m = parse("shop-amelia")!!
		val npc = m.allTiles.first { it.name.endsWith("Island NPC") }
		assertTrue(npc.locked); assertEquals("§cRequires Social Skill 5.", npc.info[1])
		val cp = m.allTiles.first { it.name.endsWith("Parkour Checkpoint") }
		assertFalse(cp.locked); assertEquals(1, cp.shiftButton)
	}

	@Test fun syntheticGeorgeLikeShopWithBuyback() {
		val slots = listOf(
			SlotText(11, "[Lvl 1] Griffin", listOf("LEGENDARY", "", "Cost", "Griffin Feather x4", "250,000 Coins", "", "Click to trade!")),
			SlotText(13, "Pet Item: Exp Share", listOf("EPIC", "", "Cost", "1.5M Coins", "", "Click to trade!", "Right-click for more trading options!")),
			SlotText(31, "Wolf Pet", listOf("COMMON", "", "Sold for 10 Coins", "", "Click to buyback!")),
		)
		val shop = NpcShopParser.detect(slots)!!
		assertEquals(31, shop.sellSlot); assertNotNull(shop.buyback)
		assertEquals(listOf("Griffin Feather" to 4), shop.offers[0].cost.items)
		assertEquals(250000.0, shop.offers[0].cost.coins)
		assertEquals(1_500_000.0, shop.offers[1].cost.coins)
		val m = NpcShopParser.parse("George", slots)!!
		assertTrue(m.sections.any { it.title == "Buyback" && it.tiles.single().delayed })
		assertEquals(m, NpcShopParser.parse("Anything At All", slots)!!.copy(heading = "George"), "detection must not depend on the title")
	}
	@Test fun noTabChainOption() {

		assertTrue(dev.ashtonite365.apex.features.menus.h6.NpcShopSettings.allOptions["tab-chain"] == null)
		assertTrue(dev.ashtonite365.apex.features.menus.MenuSettings.Global.allOptions["automation"] == null)
	}

	@Test fun tabChainStepsAreDelayedLeftClicks() {
		val c = dev.ashtonite365.apex.features.menus.h6.NpcShopChain
		for (st in listOf(c.goBack(48), c.section(12))) { assertEquals(0, st.button); assertTrue(st.delayed) }
		val d = dev.ashtonite365.apex.util.ApexClickDelay
		try {
			for (v in listOf(0, 100, 150, 200, 999)) { d.random = { v }; assertTrue(d.nextMs() in 100L..200L, "$v") }
			assertEquals(100..200, d.RANGE_MS)
		} finally { d.resetForTests() }
	}
}
