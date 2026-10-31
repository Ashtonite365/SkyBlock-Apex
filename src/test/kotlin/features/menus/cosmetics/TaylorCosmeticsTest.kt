package dev.ashtonite365.apex.test.features.menus.cosmetics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.cosmetics.TaylorCosmetics
import dev.ashtonite365.apex.features.menus.cosmetics.TaylorCosmeticsMenu
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class TaylorCosmeticsTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val main = RealDumpFixtures.load("taylor-cosmetics-2049-23")
	private val bundles = RealDumpFixtures.load("seasonal-bundles-2049-24")
	private val collection = RealDumpFixtures.load("taylors-collection-2049-26")

	@Test fun titles() {
		assertEquals(listOf("Taylor's Cosmetics", "Seasonal Bundles", "Taylor's Collection"), listOf(main.title, bundles.title, collection.title))
		MenuRegistry.register(TaylorCosmeticsMenu)
		for (t in listOf(main.title, bundles.title, collection.title)) assertSame(TaylorCosmeticsMenu, MenuRegistry.match(t))
		for (t in listOf("Taylor's Cosmetics ➜ X", "Bundles", "Community Shop", "Seasonal Bundle")) assertNull(MenuRegistry.match(t), t)
		assertFalse(MenuRegistry.denied(main.title))
	}

	@Test fun mainPage() {
		assertEquals(RealDumpFixtures.load("taylor-cosmetics-2048-18").slots, main.slots)
		val m = TaylorCosmetics.parse(main.title, main.slots)!!
		val tiles = m.allTiles
		assertEquals(listOf("Seasonal Bundles" to 20, "Taylor's Collection" to 24), tiles.map { it.name to it.slot })
		assertEquals(listOf("Current bundle: Autumn Bundle · Ends in: 2 Months"), tiles[0].info)
		assertTrue(tiles.all { it.clickable && !it.delayed })
		assertEquals(listOf("SkyBlock Gems" to "75"), m.chips)
		assertEquals(listOf("Close" to 49, "SkyBlock Gems" to 50), m.nav)
	}

	@Test fun seasonalBundles() {
		val m = TaylorCosmetics.parse(bundles.title, bundles.slots)!!
		val items = bundles.slots.mapNotNull(TaylorCosmetics::item)
		assertEquals(23, items.size)
		assertEquals(250, items.minOf { it.gems!! }); assertEquals(1800, items.maxOf { it.gems!! })
		assertEquals(listOf("Ultimate (2) · 1,800 Gems", "Mythic (8) · 375-1,350 Gems", "Legendary (7) · 950 Gems", "Epic (4) · 375-650 Gems", "Common (2) · 250 Gems"),
			m.sections.map { it.title })
		val orb = items.first { it.slot == 10 }
		assertEquals("Pumpkin Power Orb Skin", orb.name); assertEquals("MYTHIC", orb.rarity); assertEquals("MYTHIC COSMETIC", orb.rarityLine)
		assertEquals(1350, orb.gems); assertEquals("1,350 Gems", orb.priceText); assertNull(orb.owned)
		assertEquals("MYTHIC DYE", items.first { it.name == "Sludge Dye" }.rarityLine)
		assertEquals("◆ Flowing Fall Rune III", items.first { it.slot == 11 }.name)
		assertTrue(items.none { it.owned != null })
		assertEquals(listOf("Back (Taylor's Cosmetics)" to 48, "Close" to 49, "SkyBlock Gems" to 50), m.nav)
		assertTrue(m.allTiles.all { it.clickable && !it.delayed && it.button == 0 })
		assertEquals(items.map { it.slot }.toSet(), m.allTiles.map { it.slot }.toSet())
	}

	@Test fun taylorsCollection() {
		val m = TaylorCosmetics.parse(collection.title, collection.slots)!!
		assertEquals(listOf("Ultimate (2) · 1,800 Gems", "Mythic (9) · 1,350 Gems", "Epic (9) · 650 Gems"), m.sections.map { it.title })
		assertEquals(listOf("Super Plushie Golden Dragon Skin", "Super Plushie Ender Dragon Skin"), m.sections[0].tiles.map { it.name })
		assertEquals(20, m.allTiles.size)
		assertEquals(listOf("§4ULTIMATE COSMETIC§7 · §b1,800 Gems§7"), m.sections[0].tiles[0].info)
		assertEquals(listOf("SkyBlock Gems" to "75"), m.chips)
	}

	@Test fun ownedLineShownAsLoreSays() {
		val s = collection.slots.first { it.slot == 12 }
		val owned = s.copy(lore = s.lore + listOf("", "You already own this skin!"))
		assertEquals("You already own this skin!", TaylorCosmetics.item(owned)!!.owned)
		val barn = bundles.slots.first { it.cleanName == "Harvest Barn Skin" }
		assertNull(TaylorCosmetics.item(barn)!!.owned)
		val m = TaylorCosmetics.parse(collection.title, collection.slots.map { if (it.slot == 12) owned else it })!!
		assertTrue(m.allTiles.first { it.slot == 12 }.highlight)
	}

	@Test fun notAShopPage() {
		assertNull(TaylorCosmetics.parse(bundles.title, bundles.slots.filter { TaylorCosmetics.item(it) == null }))
		assertNull(TaylorCosmetics.parse("Community Shop", bundles.slots))
		assertNull(TaylorCosmetics.item(SlotText(1, "Close", emptyList())))
	}
}
