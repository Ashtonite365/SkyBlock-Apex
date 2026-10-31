package dev.ashtonite365.apex.test.features.menus.hub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.hub.HubSelectorParser
import dev.ashtonite365.apex.features.menus.hub.SelectorTileLayout
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SelectorTileLayoutTest {
	private val width: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }

	private val tw = (440 - 16 - 3 * 4) / 5

	private fun assertNoOverlap(l: SelectorTileLayout.Tile) {
		val ys = l.titleY + l.subY
		ys.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 9, "lines overlap $a/$b") }
		assertTrue((ys.maxOrNull() ?: 0) + 9 <= l.barY, "text runs into the bar: $l")
		assertTrue(l.barY + SelectorTileLayout.BAR_H <= l.height, "bar outside the tile")
	}

	@Test fun realDumpTilesDoNotOverlap() {
		val d = RealDumpFixtures.load("hub-selector")
		val m = HubSelectorParser.parse(d.title, d.slots)!!
		assertTrue(m.hubs.size > 10)
		for (h in m.hubs) {
			val l = SelectorTileLayout.tile("§f#${h.number}", h.server?.let { "§7$it" }, tw, width)
			assertNoOverlap(l)
			assertEquals(listOf(h.server), l.sub.map { it.removePrefix("§7") }, "server id is shown whole, on its own line")
			assertEquals("${h.players}/${h.capacity}", SelectorTileLayout.barLabel(h.players, h.capacity, tw - 6, width))
		}
	}

	@Test fun longTextWrapsInsteadOfTruncating() {
		val l = SelectorTileLayout.tile("§f#12", "§7mega-very-long-server-id-123", 70, width)
		assertTrue(l.sub.size > 1)
		assertEquals("mega-very-long-server-id-123", l.sub.joinToString("") { it.replace(Regex("§."), "") })
		assertTrue(l.sub.none { it.contains("...") || it.contains("…") })
		assertNoOverlap(l)
	}

	@Test fun scrollFloorAndClippedHitRects() {
		val v = ScrollView()
		v.layout(MenuBounds(0, 0, 100, 200), 100)
		assertEquals(0, v.max); assertTrue(!v.scroll(-1.0))
		v.layout(MenuBounds(0, 0, 100, 200), 250)
		assertEquals(50, v.max)
		repeat(20) { v.scroll(-1.0) }
		assertEquals(50, v.offset)
		val view = MenuBounds(0, 0, 100, 100)
		assertEquals(MenuBounds(0, 10, 50, 20), ScrollClip.clip(MenuBounds(0, 10, 50, 20), view))
		assertEquals(MenuBounds(0, 90, 50, 10), ScrollClip.clip(MenuBounds(0, 90, 50, 20), view))
		assertEquals(MenuBounds(0, 0, 50, 5), ScrollClip.clip(MenuBounds(0, -15, 50, 20), view))
		assertNull(ScrollClip.clip(MenuBounds(0, 100, 50, 20), view))
		assertNull(ScrollClip.clip(MenuBounds(0, -20, 50, 20), view))
		assertTrue(!ScrollClip.hit(10, 105, MenuBounds(0, 90, 50, 20), view))
	}
}
