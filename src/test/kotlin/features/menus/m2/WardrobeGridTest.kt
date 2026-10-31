package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeParser
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeParser.CellKind
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class WardrobeGridTest {
	@Test fun hypixelGridPage1() {
		val d = RealDumpFixtures.load("wardrobe-p1")
		val m = WardrobeParser.parse(d.title, d.slots)!!
		val cells = WardrobeParser.cells(m.slots)
		val pieces = cells.filter { it.kind == CellKind.PIECE }
		assertEquals((0..35).toList(), pieces.map { it.slot })
		assertTrue(pieces.all { it.row == it.slot / 9 && it.col == it.slot % 9 && it.row in 0..3 })
		val buttons = cells.filter { it.kind == CellKind.SET_BUTTON }
		assertEquals((36..44).toList(), buttons.map { it.slot }); assertTrue(buttons.all { it.row == 4 && it.col == it.slot - 36 })

		val by = d.slots.associateBy { it.slot }
		assertTrue(by.getValue(0).cleanName.startsWith("Ancient Tarantula Helmet")); assertEquals("Slot 4 Helmet", by.getValue(3).cleanName)
		assertTrue(pieces.any { it.slot == 3 } && pieces.any { it.slot == 30 })
		assertTrue(cells.none { it.slot >= 45 })
		assertEquals(53, m.nav.next); assertEquals(49, m.nav.close)
	}

	@Test fun everyPageSameShape() {
		for (fx in listOf("wardrobe-p1", "wardrobe-p2", "wardrobe-p3")) {
			val d = RealDumpFixtures.load(fx)
			val m = WardrobeParser.parse(d.title, d.slots)!!
			val cells = WardrobeParser.cells(m.slots)
			assertEquals(36, cells.count { it.kind == CellKind.PIECE }, fx)
			assertEquals(m.sets.map { it.buttonSlot }.toSet(), cells.filter { it.kind == CellKind.SET_BUTTON }.map { it.slot }.toSet(), fx)
		}
	}
}
