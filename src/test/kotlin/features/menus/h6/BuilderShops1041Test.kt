package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.NpcShopParser
import dev.ashtonite365.apex.features.menus.pets.PetCareParser
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class BuilderShops1041Test {
	private fun load(n: String) = RealDumpFixtures.load(n).let { it.title to it.slots.filter { s -> s.cleanName.isNotEmpty() } }
	private fun parse(n: String) = load(n).let { (t, s) -> NpcShopParser.parse(t, s) }

	private fun costSlots(n: String) = load(n).second.filter { s -> s.cleanLore.any { it == "Cost" } }.map { it.slot }

	@Test fun builderHubTabsDiscoveredFromScreen() {
		NpcShopParser.hubSections.clear()
		val hub = parse("shop-builder-1041")!!
		val (_, slots) = load("shop-builder-1041")
		val expected = slots.filter { s -> s.cleanLore.lastOrNull() == "Click to view!" }.map { it.cleanName }
		assertEquals(expected, hub.allTiles.map { it.name })
		assertEquals(4, expected.size)
		assertEquals(expected, NpcShopParser.hubSections["Builder"])
	}

	@Test fun everyBuilderSectionIsATab() {
		NpcShopParser.hubSections.clear(); parse("shop-builder-1041")
		val tabs = NpcShopParser.hubSections.getValue("Builder")
		for (f in listOf("shop-builder-woodworking-1041", "shop-builder-rocks-1041", "shop-builder-green-thumb-1041", "shop-builder-variety-1041")) {
			val m = parse(f); assertNotNull(m, f); m!!
			assertTrue(m.tabs, f)
			assertEquals(tabs, m.sections.map { it.title }, f)
			assertEquals(MenuText.clean(load(f).first), m.sections[m.selectedTab].title, f)
			assertEquals(costSlots(f), m.sections[m.selectedTab].tiles.map { it.slot }.filter { it in costSlots(f) }, f)
			assertTrue(m.nav.any { it.first == "Back (Builder)" }, f)
		}
	}

	@Test fun standaloneShops() {
		for (f in listOf("shop-mad-redstone-1041", "shop-wool-vibrant-1041", "shop-wool-cool-1041", "shop-bea-1042")) {
			val (_, s) = load(f)
			val shop = NpcShopParser.detect(s); assertNotNull(shop, f); shop!!
			assertEquals(costSlots(f), shop.offers.map { it.slot }, f)
			for (o in shop.offers) assertTrue(o.cost.coins != null || o.cost.items.isNotEmpty(), "$f ${o.name}")
			val m = parse(f)!!
			assertFalse(m.tabs, f)
		}
	}

	@Test fun woolCoolSecondPage() {
		val (_, s) = load("shop-wool-cool-1041")
		val shop = NpcShopParser.detect(s)!!
		assertEquals(2, shop.page)
		assertTrue(shop.offers.all { it.stackable })
	}

	@Test fun petSitterIsExistingPetCareMenu() {
		val d = RealDumpFixtures.load("pet-sitter-1042")
		assertEquals(null, NpcShopParser.detect(d.slots.filter { it.cleanName.isNotEmpty() }))
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.SITTER, PetCareParser.chestSize(81))!!
		assertEquals("Pet Sitter", p.header?.cleanName); assertEquals(40, p.nav.close)
	}
}
