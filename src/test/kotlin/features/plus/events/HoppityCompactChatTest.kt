// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.HoppityCompactChat

class HoppityCompactChatTest {
	@Test
	fun compactNewAndDupe() {
		val c = HoppityCompactChat.Compactor()
		assertFalse(c.feed("hello").first)
		assertTrue(c.feed("HOPPITY'S HUNT You found a Chocolate Lunch Egg on a ledge next to the stairs up!").first)
		assertTrue(c.feed("HOPPITY'S HUNT You found Solomon (LEGENDARY)!").first)
		val (hide, out) = c.feed("NEW RABBIT! +2 Chocolate and +0.003x Chocolate per second!")
		assertTrue(hide)
		assertEquals("§dChocolate Lunch Egg! §d§lNEW §6§lLEGENDARY §fSolomon §7(+2 Chocolate and +0.003x Chocolate per second!§7)", out!!.first)
		assertEquals(3, out.second.size)

		assertTrue(c.feed("HOPPITY'S HUNT You found a Hitman Egg!").first)
		c.feed("HOPPITY'S HUNT You found Arnie (COMMON)!")
		val dupe = c.feed("DUPLICATE RABBIT! +6,759,912 Chocolate").second!!.first
		assertTrue(dupe.startsWith("§dHitman Egg! §7Duplicate §6COMMON §fArnie"), dupe)

		c.feed("You bought Bubbles!")
		assertFalse(c.feed("random").first)
	}

	@Test
	fun summary() {
		val s = HoppityCompactChat.Summary()
		val c = HoppityCompactChat.Compactor { HoppityCompactChat.record(s, it) }
		c.feed("HOPPITY'S HUNT You found a Chocolate Lunch Egg on a ledge!"); c.feed("HOPPITY'S HUNT You found Arnie (COMMON)!"); c.feed("DUPLICATE RABBIT! +1,000 Chocolate")
		assertEquals(1, s.eggs["Chocolate Lunch Egg"]); assertEquals(1, s.dupeRabbits["COMMON"]); assertEquals(1000L, s.dupeChocolate)
		assertTrue(HoppityCompactChat.summaryLines(s).last().contains("1,000"))
	}
}
