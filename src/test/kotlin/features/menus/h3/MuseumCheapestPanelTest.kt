// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.museum.CheapestList

class MuseumCheapestPanelTest {
	@Test
	fun cheapestListScrollReachesLastRow() {
		val total = 394; val vis = CheapestList.visible(200)
		assertEquals(14, vis)
		assertEquals(total - vis, CheapestList.maxScroll(total, vis))
		assertEquals(total - vis, CheapestList.clamp(10_000, total, vis))
		assertEquals(0, CheapestList.clamp(-5, total, vis))
		val last = CheapestList.shown(10_000, total, vis)
		assertEquals(total - 1, last.last); assertEquals(vis, last.count())
		assertEquals(0 until 3, CheapestList.shown(0, 3, vis))
		var s = 0; repeat(200) { s = CheapestList.clamp(s + 3, total, vis) }
		assertTrue(total - 1 in CheapestList.shown(s, total, vis))
	}

	@Test
	fun buyOpensAhSearchOnly() {
		val row = dev.ashtonite365.apex.features.museum.MuseumHelper.Missing("§9Spider Sword", listOf(dev.ashtonite365.apex.util.ItemId("SPIDER_SWORD")), "Combat", 0, { 100.0 }, { null })
		assertEquals("ahs Spider Sword", dev.ashtonite365.apex.features.museum.MuseumHelper.buyCommand(row))
	}
}
