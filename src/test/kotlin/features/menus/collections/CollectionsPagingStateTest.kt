// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CollectionsPagingStateTest {
	private fun item(n: String) = RealDumpFixtures.load(n).let { CollectionsParser.parse(it.title, it.slots) } as CollPage.Item

	@Test fun loreBeatsPaneOnRealPages() {
		for (n in listOf("coal-collection", "gravel-collection", "glowstone-dust-collection")) {
			val p = item(n)
			assertTrue(p.tiers.none { it.state == TileState.UNKNOWN }, n)
			val red = CollectionsParser.withPaneStates(p) { "red_stained_glass_pane" }
			assertEquals(p.tiers.map { it.state }, red.tiers.map { it.state }, n)
		}
	}

	@Test fun paneFillsOnlyUnknown() {
		val p = item("glowstone-dust-collection")
		val blank = p.copy(tiers = p.tiers.map { if (it.tier == 7) it.copy(state = TileState.UNKNOWN) else it })
		val f = CollectionsParser.withPaneStates(blank) { s -> if (s == blank.tiers.last().slot) "lime_stained_glass_pane" else "redstone" }
		assertEquals(TileState.UNLOCKED, f.tiers.last().state)
		assertEquals(p.tiers.dropLast(1).map { it.state }, f.tiers.dropLast(1).map { it.state })
		assertEquals(TileState.UNKNOWN, CollectionsParser.withPaneStates(blank) { "redstone" }.tiers.last().state)
	}

	private fun cat(title: String, vararg extra: SlotText) = CollectionsParser.parse(title, listOf(
		SlotText(4, "Farming Collections", listOf("View your Farming Collections!")),
		SlotText(10, "Wheat V", listOf("View all your Wheat Collection", "Total collected: 1,000")),
		SlotText(11, "Carrot", listOf("View all your Carrot Collection", "Progress: 50%")),
		SlotText(49, "Close", emptyList())) + extra) as CollPage.Category

	@Test fun pagingFromTitle() {
		assertEquals(2 to 3, cat("(2/3) Farming Collections").page)
		assertEquals(2 to 3, cat("Farming Collections (2/3)").page)
		assertEquals("Farming", cat("Farming Collections (2/3)").category)
		assertNull(cat("Farming Collections").page)
	}

	@Test fun pagingFromArrows() {
		val next = SlotText(53, "Next Page", listOf("Page 2")); val prev = SlotText(45, "Previous Page", listOf("Page 2"))
		assertEquals(1 to 0, cat("Farming Collections", next).page)
		assertEquals(3 to 3, cat("Farming Collections", prev).page)
		val mid = cat("Farming Collections", SlotText(45, "Previous Page", listOf("Page 1")), SlotText(53, "Next Page", listOf("Page 3")))
		assertEquals(2 to 0, mid.page)
		assertEquals(45, CollectionsParser.prevButton(mid)!!.slot); assertEquals(53, CollectionsParser.nextButton(mid)!!.slot)
		assertEquals(1 to 0, cat("Farming Collections", SlotText(53, "Next Page", emptyList())).page)

		assertEquals(2 to 4, cat("(2/4) Farming Collections", next).page)
	}

	@Test fun hoppityNotTakenOver() {
		val d = RealDumpFixtures.load("hoppity-collection-4")
		assertNull(CollectionsParser.parse(d.title, d.slots))
	}
}
