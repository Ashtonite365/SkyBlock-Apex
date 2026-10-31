// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.MenuDebug
import dev.ashtonite365.apex.features.menus.support.SlotDumpGate
import dev.ashtonite365.apex.features.menus.support.SlotText

class SlotDumpGateTest {
	private val S = SlotDumpGate.STABLE_MS

	@Test
	fun waitsForContentsThenFiresOnce() {
		val g = SlotDumpGate()
		assertFalse(g.offer(5, 1, 0, 0))
		assertFalse(g.offer(5, 1, 0, S + 1))
		assertFalse(g.offer(5, 2, 40, S + 2))
		assertFalse(g.offer(5, 2, 40, S + 50))
		assertTrue(g.offer(5, 2, 40, 2 * S + 3))
		assertFalse(g.offer(5, 2, 40, 3 * S))
		assertFalse(g.offer(5, 3, 41, 9 * S))
	}

	@Test
	fun everyWindowGetsItsOwnDump() {
		val g = SlotDumpGate()

		for ((i, id) in listOf(5, 6, 7).withIndex()) {
			val t = i * 10_000L
			assertFalse(g.offer(id, 10 + id, 30, t))
			assertTrue(g.offer(id, 10 + id, 30, t + S), "window $id")
		}
		assertFalse(g.offer(5, 15, 30, 99_999))
	}

	@Test
	fun dumpFormatUnchangedForUnclassifiedTitles() {
		for (title in listOf("SkyBlock Menu", "Your Stats", "Combat Stats", "Statistics")) {
			val d = MenuDebug.dump(title, 7, listOf(SlotText(13, "§aYour Stats", listOf("§7Health"))))
			assertEquals("[ApexSlotDump] id=7 title=$title\n[ApexSlotDump]   slot=13 name=&aYour Stats lore=[&7Health]", d)
		}
	}

	@Test
	fun contentUpdatesDedupedAndTagged() {
		val g = SlotDumpGate()
		val p1 = g.contentKey("Your Skills", listOf(SlotText(10, "§aFarming", listOf("§7Lv 50"))))
		val p2 = g.contentKey("Your Skills", listOf(SlotText(10, "§aCarpentry", listOf("§7Lv 40"))))
		val p3 = g.contentKey("Your Skills (2/2)", listOf(SlotText(10, "§aCarpentry", listOf("§7Lv 40"))))
		g.opened(9)
		assertEquals(null, g.contentUpdate(9, p1, 30, true))
		g.dumped(9, p1)
		assertEquals(null, g.contentUpdate(9, p1, 30, true))
		assertEquals("update#1", g.contentUpdate(9, p2, 30, true))
		assertEquals(null, g.contentUpdate(9, p2, 30, true))
		assertEquals(null, g.contentUpdate(9, p1, 30, true))
		assertEquals("update#2", g.contentUpdate(9, p3, 30, true))
		assertEquals(null, g.contentUpdate(9, 12345, 0, true))
		g.opened(9)
		g.dumped(9, p1)
		assertEquals("update#1", g.contentUpdate(9, p2, 30, true))
	}

	@Test
	fun contentUpdateGateOffNeverDumps() {
		val g = SlotDumpGate()
		g.opened(4); g.dumped(4, 1)
		assertEquals(null, g.contentUpdate(4, 2, 30, false))
		assertEquals(null, g.contentUpdate(4, 3, 30, false))
		assertEquals("update#1", g.contentUpdate(4, 2, 30, true))
	}
}
