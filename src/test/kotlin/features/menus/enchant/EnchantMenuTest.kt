// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.enchant

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.enchant.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class EnchantMenuTest {
	@Test fun realEmptyTable() {
		val d = RealDumpFixtures.load("enchant-empty")
		val empty = (0 until 54).toSet() - d.slots.map { it.slot }.toSet()
		val m = EnchantParser.parse(d.title, d.slots, 54, empty, emptySet())!!
		assertEquals(19, m.itemSlot)
		assertTrue(m.options.isEmpty())
		assertEquals(49, m.closeSlot); assertEquals(50, m.guideSlot); assertEquals(51, m.sortSlot)
		assertEquals("Default", m.sort)
		assertEquals(listOf("Default", "Missing Enchantments First", "A to Z", "Z to A"), m.sortChoices)
		assertEquals(2, m.hints.size)
		assertTrue(m.others.isEmpty())
	}

	@Test fun tolerantOptions() {
		val slots = listOf(
			SlotText(12, "Sharpness V", listOf("Increases melee damage dealt by 25%.", "", "Cost: 30 Exp Levels", "", "Click to enchant!")),
			SlotText(13, "Smite VI", listOf("Already applied!", "", "Click to remove!")),
			SlotText(19, "Aspect of the End", listOf("LEGENDARY SWORD")),
			SlotText(49, "Close"))
		val m = EnchantParser.parse("Enchant Item", slots, 54, emptySet(), setOf(12, 13))!!
		assertEquals(19, m.itemSlot)
		val sharp = m.options.first { it.slot == 12 }
		assertEquals("Sharpness", sharp.base); assertEquals(5, sharp.level); assertEquals(30, sharp.cost); assertFalse(sharp.applied)
		assertTrue(m.options.first { it.slot == 13 }.applied)
		assertNull(EnchantParser.parse("Craft Item", slots, 54, emptySet(), emptySet()))
	}
}

class EnchantRealItemTest {
	@Test fun itemOnTable() {
		val d = RealDumpFixtures.load("enchant-item")
		val m = EnchantParser.parse(d.title, d.slots, 54, emptySet(), emptySet())!!
		assertEquals(19, m.itemSlot)
		assertEquals(15, m.options.size)
		val sharp = m.options.first { it.name == "Sharpness" }
		assertEquals(12, sharp.slot); assertFalse(sharp.applied); assertNull(sharp.level)
		assertEquals(35, m.nextSlot); assertNull(m.prevSlot)
		assertEquals(49, m.closeSlot); assertEquals(51, m.sortSlot); assertEquals("Default", m.sort)
		assertEquals(1, m.hints.size)
		assertTrue(m.others.isEmpty(), m.others.toString())
	}

	@Test fun appliedStatus() {
		val m = EnchantParser.parse("Enchant Item", listOf(
			SlotText(12, "Sharpness", listOf("Increases melee damage dealt by 25%.", "", "  Sharpness V", "", "Click to view!")),
			SlotText(13, "Smite", listOf("", "  Smite ✖", "", "Click to view!")),
			SlotText(19, "Sword", listOf("COMMON SWORD"))), 54, emptySet(), emptySet())!!
		assertEquals(5, m.options.first { it.slot == 12 }.level); assertTrue(m.options.first { it.slot == 12 }.applied)
		assertFalse(m.options.first { it.slot == 13 }.applied)
	}
}
