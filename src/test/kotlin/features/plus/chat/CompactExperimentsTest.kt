// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.chat.CompactExperiments

class CompactExperimentsTest {
	@Test
	fun patterns() {
		assertTrue(CompactExperiments.isExperimentInventory("Superpairs (Metaphysical)"))
		assertTrue(CompactExperiments.isExperimentInventory("Superpairs Rewards"))
		assertTrue(CompactExperiments.isClaim("You claimed the Ultrasequencer rewards!"))
		assertEquals("132k Enchanting Exp", CompactExperiments.reward(" +132k Enchanting Exp"))
	}

	@Test
	fun jacob() {
		val j = CompactExperiments.JacobCompactor()
		assertEquals(false, j.feed("hi").first)
		assertTrue(j.feed("  FARMING CONTEST REWARDS CLAIMED").first)
		j.feed("   » Sunflower Contest on Autumn 31st, Year 472"); j.feed("  REWARDS"); j.feed("    Jacob's Ticket x271")
		val (hide, out) = j.feed("▬".repeat(40))
		assertTrue(hide)
		assertEquals(3, out!!.second.size)
		assertTrue(out.first.contains("1 contest,"))
	}

	@Test
	fun stash() {
		val c = CompactExperiments.StashCompactor()
		assertTrue(c.feed("                 You have 226 materials stashed away!").first)
		assertTrue(c.feed("               (This totals 3 types of materials stashed!)").first)
		val (hide, out) = c.feed("                >>> CLICK HERE to pick them up! <<<")
		assertTrue(hide)
		assertEquals("§eYou have §a226 §ematerials stashed §7(3 types). §6Click to pick up!", out!!.first)
		assertTrue(out.second)
		assertEquals(false, c.feed(">>> CLICK HERE to pick them up! <<<").first)
	}
}
