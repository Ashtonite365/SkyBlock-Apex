// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumRemoveGuard
import dev.ashtonite365.apex.features.menus.museum.MuseumStatus
import dev.ashtonite365.apex.features.menus.support.SlotText

class MuseumCategoryPageTest {

	private val combat = RealDumpFixtures.load("museum-combat-1")

	@Test
	fun everyGridItemListedFromRealPage() {
		val m = MuseumParser.parse(combat.title, combat.slots)!!
		assertEquals("Combat", m.category); assertEquals(1, m.page); assertEquals(9, m.pages)
		assertEquals(28, m.entries.size)
		val by = m.entries.associateBy { it.name }
		assertEquals(MuseumStatus.MISSING, by["White Belt"]!!.status)
		assertEquals(MuseumStatus.MISSING, by["Celeste Armor"]!!.status)
		assertEquals(MuseumStatus.DONATED, by["End Sword"]!!.status)
		assertEquals(MuseumStatus.DONATED, by["Wand of Healing"]!!.status)
		assertTrue(m.entries.none { it.status == MuseumStatus.UNKNOWN }, m.entries.filter { it.status == MuseumStatus.UNKNOWN }.joinToString { it.name })
		assertEquals(33 to 245, m.donated!! to m.total!!)
	}

	@Test
	fun lockedItemsIncludedAndNeverClicked() {
		val slots = combat.slots.filter { it.slot != 10 && it.slot != 11 && it.slot != 12 } + listOf(
			SlotText(10, "§c???", listOf("§7This item is locked!")),
			SlotText(11, " ", listOf("§7Locked")),
			SlotText(12, "§8Grey Item", listOf("§cNot unlocked yet")),
		)
		val m = MuseumParser.parse(combat.title, slots)!!
		val locked = m.entries.filter { it.status == MuseumStatus.LOCKED }
		assertEquals(listOf(10, 11, 12), locked.map { it.slot }.sorted())
		assertEquals(28, m.entries.size)
		for (l in locked) { assertFalse(l.removable); assertTrue(MuseumRemoveGuard.isProtected(m, l.slot)) }
		assertFalse(MuseumRemoveGuard.isProtected(m, 13))
	}
}
