package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class BagPagesTest {
	private fun parse(n: String) = RealDumpFixtures.load(n).let { BagsParser.parse(it.title, it.slots.filter { s -> s.cleanName.isNotEmpty() })!! }
	private fun HubModel.chip(l: String) = chips.firstOrNull { it.first == l }?.second

	@Test fun quiverTypesFromSlots() {
		val q = parse("bags-quiver")
		assertEquals(36, q.allTiles.size)
		assertEquals("2", q.chip("Types"))
		assertEquals("34 slots", q.chip("Icy Arrow")); assertEquals("2 slots", q.chip("Flint Arrow"))
		assertTrue(q.allTiles.all { it.button == 0 && !it.delayed && !it.locked })
		assertTrue(q.nav.contains("Clear Quiver" to 41))
	}

	@Test fun accessoryPageOne() {
		val a = parse("bags-accessory-1")
		assertEquals("1/2", a.chip("Page"))
		assertEquals(RealDumpFixtures.load("bags-accessory-1").slots.count { it.slot < 45 && it.cleanName.isNotEmpty() }, a.allTiles.size)
		assertTrue(a.chip("Accessory Power (page)")!!.startsWith("+"))
		assertTrue(a.nav.any { it.first == "Next Page" })
		assertNull(a.locked)
	}

	@Test fun emptyAccessoryPageTwo() {
		val a = parse("bags-accessory-2")
		assertEquals("2/2", a.chip("Page"))
		assertTrue(a.allTiles.isEmpty()); assertTrue(a.sections.isEmpty())
		assertEquals("Empty page", a.emptyText); assertNull(a.locked)
		assertTrue(a.nav.any { it.first == "Previous Page" })
	}

	@Test fun lockedSlotsAreNotOffered() {
		val slots = listOf(
			SlotText(0, "Speed Ring", listOf("Speed: +3", "UNCOMMON ACCESSORY")),
			SlotText(1, "Locked Slot", listOf("Requires Redstone Collection IX.")),
			SlotText(48, "Go Back", listOf("To Your Bags")), SlotText(49, "Close", emptyList()))
		val m = BagsParser.parse("Accessory Bag (1/2)", slots)!!
		assertEquals(listOf(0), m.allTiles.map { it.slot }); assertEquals("1", m.chip("Locked"))
		val all = BagsParser.parse("Accessory Bag (2/2)", slots.drop(1))!!
		assertTrue(all.allTiles.isEmpty()); assertEquals("Locked", all.locked)
	}
}
