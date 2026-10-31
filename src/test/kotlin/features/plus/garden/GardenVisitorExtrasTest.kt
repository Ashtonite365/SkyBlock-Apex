// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenVisitorExtras
import dev.ashtonite365.apex.features.garden.GardenVisitorExtras.Stats
import dev.ashtonite365.apex.features.garden.GardenVisitorExtras.Status

class GardenVisitorExtrasTest {
	@Test
	fun colours() {
		val r = GardenVisitorExtras.visitorRarities()
		assertEquals("uncommon", r["Alchemist"])
		assertEquals("§aAlchemist", GardenVisitorExtras.colouredName("Alchemist", r))
		assertEquals("§fNobody", GardenVisitorExtras.colouredName("Nobody", r))
	}

	@Test
	fun compact() {
		assertEquals("§c32 Copper", GardenVisitorExtras.compactReward("+32 Copper"))
		assertEquals("§311k Farming XP", GardenVisitorExtras.compactReward("+11k Farming XP"))
		assertEquals("§f2 Gold Essence", GardenVisitorExtras.compactReward("+2x Gold Essence"))
		assertEquals("§f3 Fine Flour", GardenVisitorExtras.compactReward("Fine Flour x3"))
		assertEquals("§fDead Bush", GardenVisitorExtras.compactReward("Dead Bush"))
	}

	@Test
	fun stats() {
		val s = Stats()
		assertTrue(GardenVisitorExtras.record(s, "OFFER ACCEPTED with Duke (UNCOMMON)"))
		assertTrue(GardenVisitorExtras.record(s, "    +20 Copper"))
		assertTrue(GardenVisitorExtras.record(s, "    +18.2k Farming XP"))
		assertTrue(GardenVisitorExtras.record(s, "    +754 Gemstone Powder"))
		assertTrue(GardenVisitorExtras.record(s, "    Green Bandana"))
		assertFalse(GardenVisitorExtras.record(s, "You sold something"))
		assertEquals(1, s.accepted); assertEquals(20L, s.copper); assertEquals(18200L, s.farmingXp); assertEquals(754L, s.gemstonePowder)
		assertEquals(1, s.rarities["UNCOMMON"]); assertEquals(1, s.rewards["Green Bandana"])
	}

	@Test
	fun status() {
		assertEquals(Status.NEW, GardenVisitorExtras.status(null, emptyMap()))
		assertEquals(Status.WAITING, GardenVisitorExtras.status(mapOf("Wheat" to 10), mapOf("Wheat" to 3)))
		assertEquals(Status.READY, GardenVisitorExtras.status(mapOf("Wheat" to 10), mapOf("Wheat" to 64)))
	}
}
