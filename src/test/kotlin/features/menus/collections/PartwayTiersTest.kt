// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PartwayTiersTest {
	private fun item(n: String) = RealDumpFixtures.load(n).let { CollectionsParser.parse(it.title, it.slots) } as CollPage.Item

	@Test fun gravelOnFinalTier() {
		val p = item("gravel-collection")
		assertEquals("Gravel", p.item)
		assertEquals(9, p.maxTier); assertEquals(8, p.unlockedTiers); assertFalse(p.maxed)
		val next = p.next!!
		assertEquals(9, next.tier); assertEquals(TileState.IN_PROGRESS, next.state)
		assertEquals(33.2, next.progress!!.pct); assertEquals(16_623.0, next.progress!!.current); assertEquals(50_000.0, next.progress!!.max)
		assertEquals(listOf("+5,000 Mining Experience", "+4 SkyBlock XP"), next.details)
		assertTrue(p.tiers.none { it.state == TileState.LOCKED })
		assertEquals(List(8) { TileState.UNLOCKED }, p.tiers.take(8).map { it.state })
	}

	@Test fun glowstoneThreeLeft() {
		val p = item("glowstone-dust-collection")
		assertEquals("Glowstone Dust", p.item)
		assertEquals((1..7).toList(), p.tiers.map { it.tier })
		assertEquals(7, p.maxTier); assertEquals(4, p.unlockedTiers)
		val next = p.next!!
		assertEquals(5, next.tier); assertEquals(72.4, next.progress!!.pct)
		assertEquals(3_622.0, next.progress!!.current); assertEquals(5_000.0, next.progress!!.max)

		assertEquals(listOf(TileState.UNLOCKED, TileState.UNLOCKED, TileState.UNLOCKED, TileState.UNLOCKED,
			TileState.IN_PROGRESS, TileState.LOCKED, TileState.LOCKED), p.tiers.map { it.state })
		assertEquals(10_000.0, p.tiers[5].progress!!.max); assertEquals(25_000.0, p.tiers[6].progress!!.max)
		assertTrue(p.header!!.extra.any { it == "Total Collected: 3,622" })
	}

	@Test fun paneColourOnlyForPanes() {
		assertNull(CollectionsParser.stateFromItem("redstone"))
		assertNull(CollectionsParser.stateFromItem("glowstone_dust"))
		assertEquals(TileState.IN_PROGRESS, CollectionsParser.stateFromItem("yellow_stained_glass_pane"))
	}
}
