package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.wardrobe.LoadoutMenu
import dev.ashtonite365.apex.features.menus.wardrobe.LoadoutParser
import dev.ashtonite365.apex.features.menus.wardrobe.SetState
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeMenu
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeParser
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class LoadoutWardrobeTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun loadoutsPage1() {
		val d = RealDumpFixtures.load("loadouts-p1")
		val m = LoadoutParser.parse(d.title, d.slots)!!
		assertEquals(12, m.loadouts.size)
		assertEquals(4, m.loadouts.count { it.empty }); assertEquals(8, m.loadouts.count { it.locked })
		assertEquals("Loadout 5", m.loadouts.first { it.slot == 24 }.name)
		assertEquals(44, m.nav.next); assertNull(m.nav.prev)
		assertTrue(m.current.any { it.cleanName == "Power Stone" }); assertTrue(m.current.none { it.slot in LoadoutKeybinds.Logic.loadoutSlots })

		assertEquals(14, LoadoutParser.keySlot(m, 0)); assertNull(LoadoutParser.keySlot(m, 12))
	}

	@Test fun loadoutsPage3() {
		val d = RealDumpFixtures.load("loadouts-p3")
		val m = LoadoutParser.parse(d.title, d.slots)!!
		assertEquals(17, m.nav.prev); assertNull(m.nav.next)
		M2.reg(LoadoutMenu, true)
		assertTrue(M2.attaches("(3/3) Loadouts")); assertNull(MenuRegistry.match("Loadout Editor"))
		M2.reg(LoadoutMenu, false); assertTrue(M2.vanilla("(1/3) Loadouts"))
	}

	@Test fun wardrobeSynthetic() {
		val slots = listOf(SlotText(0, "Helmet A"), SlotText(9, "Chest A"), SlotText(1, "Slot 2: Empty"),
			SlotText(36, "Slot 1: Equipped"), SlotText(37, "Slot 2: Ready"), SlotText(38, "Slot 3: Locked"), SlotText(53, "Next Page"))
		val m = WardrobeParser.parse("Wardrobe (1/2)", slots)!!
		assertEquals(listOf(SetState.EQUIPPED, SetState.READY, SetState.LOCKED), m.sets.map { it.state })
		assertEquals(listOf("Helmet A", "Chest A"), m.sets[0].pieces)
		assertEquals(53, m.nav.next)
		assertEquals(37, WardrobeParser.keySlot(m, 1, false)); assertNull(WardrobeParser.keySlot(m, 0, false)); assertEquals(36, WardrobeParser.keySlot(m, 0, true))
		assertNull(WardrobeParser.keySlot(m, 2, true))
		M2.reg(WardrobeMenu, true)
		assertTrue(M2.attaches("Wardrobe (1/2)")); assertTrue(M2.attaches("(1/3) Armor Sets")); assertNull(MenuRegistry.match("Wardrobe Upgrades"))
	}
}
