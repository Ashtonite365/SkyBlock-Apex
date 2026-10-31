package dev.ashtonite365.apex.test.features.menus.salvage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.salvage.MalikMenu
import dev.ashtonite365.apex.features.menus.salvage.MalikParser
import dev.ashtonite365.apex.features.menus.salvage.SalvageMenu
import dev.ashtonite365.apex.features.menus.salvage.SalvageParser
import dev.ashtonite365.apex.features.menus.support.SlotDumpGate
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SalvageMenusTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val real = RealDumpFixtures.load("salvage-items-1301-19")

	private fun withAssist(state: String, slot: Int = 41, salvageAt: Int = 40) = real.slots.filter { it.slot != slot }.map {
		if (it.cleanName == "Salvage Items") it.copy(slot = salvageAt) else it } +
		SlotText(slot, "Salvage Assist", listOf("Automatically salvage items", "you add.", "", "Currently: $state", "", "Click to toggle!"))

	@Test fun realPage() {
		assertEquals(real.slots, RealDumpFixtures.load("salvage-items-1301-26").slots)
		val m = SalvageParser.parse(real.title, real.slots)!!
		assertEquals(listOf("Salvage Items" to 40, "Add Uncommon Items" to 50, "Add Rare Items" to 51, "Add Epic Items" to 52),
			m.sections.first().tiles.map { it.name to it.slot })
		val other = m.sections.first { it.title == "Other" }.tiles
		assertEquals(listOf("Essence Guide" to 47), other.map { it.name to it.slot })
		assertTrue(other.single().clickable)
		assertTrue(m.nav.any { it.second == 48 } && m.nav.any { it.second == 49 })
		assertNull(SalvageParser.controls(real.slots).assist)
	}

	@Test fun liveButtonDiscovery() {
		val c = SalvageParser.controls(real.slots)
		assertEquals(40, c.salvage!!.slot); assertEquals(listOf(50, 51, 52), c.adders.map { it.slot })
		val moved = SalvageParser.controls(withAssist("ON", slot = 44, salvageAt = 42))
		assertEquals(42, moved.salvage!!.slot); assertEquals(44, moved.assist!!.slot)
		assertNull(SalvageParser.parse(real.title, real.slots.filter { it.cleanName != "Salvage Items" }))
	}

	@Test fun assistTileReflectsLiveLore() {
		val a = SalvageParser.parse(real.title, withAssist("ON"))!!.sections.first().tiles
		val b = SalvageParser.parse(real.title, withAssist("OFF"))!!.sections.first().tiles
		assertEquals(listOf("Salvage Items", "Salvage Assist"), a.take(2).map { it.name })
		assertEquals(listOf("§aON"), a[1].info); assertTrue(a[1].highlight)
		assertEquals(listOf("§cOFF"), b[1].info); assertFalse(b[1].highlight)
		assertEquals(listOf("§aON"), SalvageParser.parse(real.title, withAssist("Enabled"))!!.sections.first().tiles[1].info)
	}

	@Test fun clicksForwardToRealSlots() {
		val d = withAssist("ON", slot = 44, salvageAt = 42)
		val tiles = SalvageParser.parse(real.title, d)!!.sections.first().tiles
		assertEquals(listOf(42, 44, 50, 51, 52), tiles.map { it.slot })
		for (t in tiles) { assertEquals(0, t.button); assertFalse(t.delayed); assertTrue(t.clickable) }
	}

	@Test fun blacksmithAndRegistry() {
		val d = RealDumpFixtures.load("dungeon-blacksmith-1301-18")
		val m = MalikParser.parse(d.title, d.slots)!!
		assertEquals(listOf("Salvage Items" to 20, "Essence Crafting" to 22, "Essence Shop" to 24), m.allTiles.map { it.name to it.slot })
		assertEquals(listOf("Close" to 49), m.nav)
		MenuRegistry.register(SalvageMenu); MenuRegistry.register(MalikMenu)
		assertEquals("salvage", MenuRegistry.match("Salvage Items")?.id); assertEquals("malik", MenuRegistry.match("Dungeon Blacksmith")?.id)
		assertNull(MenuRegistry.match("Malik"))
		assertTrue(SlotDumpGate.everyUpdate("Dungeon Blacksmith") && SlotDumpGate.everyUpdate("Salvage Items"))
	}
}
