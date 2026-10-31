package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.accessories.AccessorySidebarLayout
import dev.ashtonite365.apex.features.accessories.AccessorySidebarLayout.Box
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubTileText
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class AccessoryBagPanelTest {
	private val dump = RealDumpFixtures.load("bags-accessory-1439")
	private val model = BagsParser.parse(dump.title, dump.slots.filter { it.cleanName.isNotEmpty() })!!

	@Test fun accessoryBagShowsTheInventoryPanel() {
		assertEquals("Accessory Bag (1/2)", model.heading)
		for (t in listOf("Accessory Bag", "Accessory Bag (1/2)", "Accessory Bag (2/2)")) assertTrue(BagsParser.acceptsItems(t), t)
		assertFalse(BagsParser.acceptsItems("Your Bags"))
		assertEquals(dump.slots.count { it.slot < 45 && it.cleanName.isNotEmpty() }, model.allTiles.size)
	}

	@Test fun shiftClickOnAnyInventoryCellGoesToTheRealSlotNatively() {
		val window = 54 + 36
		val origin = 99 to 237
		val g = InventoryPanel.Gesture()
		for (row in 0..3) for (col in 0..8) {
			val (sx, sy) = InventoryPanel.itemPos(row, col, origin.first, origin.second)
			val cell = InventoryPanel.cellAt(origin, window, sx + 8.0, sy + 8.0)
			assertEquals(54 + row * 9 + col, cell)

			assertTrue(g.press(cell != null)); assertTrue(g.release())
		}

		assertEquals(86, InventoryPanel.slotIndex(3, 5, 54)); assertEquals(87, InventoryPanel.slotIndex(3, 6, 54))
		assertEquals(60, InventoryPanel.slotIndex(0, 6, 54))

		for (t in model.allTiles) assertFalse(InventoryPanel.isPanelSlot(t.slot, window))
	}

	private fun assertDockedBeside(anchor: Box, sw: Int, sh: Int, contentH: Int, itemList: Box?) {
		val b = AccessorySidebarLayout.dock(anchor, sw, sh, 210, contentH)
		assertNotNull(b, "fits $sw x $sh")
		b!!
		assertFalse(b.overlaps(anchor), "sidebar $b overlaps bag window $anchor (grid + inventory panel)")
		itemList?.let { assertFalse(b.overlaps(it), "sidebar $b overlaps item list $it") }
		assertTrue(b.x >= 0 && b.right <= sw && b.y >= 0 && b.bottom <= sh)
		assertTrue(b.w >= AccessorySidebarLayout.MIN_W)
	}

	@Test fun sidebarDocksBesideTheBagNeverOverIt() {

		val sw = 1024; val sh = 575
		val bag = Box((sw - 360) / 2, (sh - 330) / 2, 360, 330)
		val itemList = Box(820, 0, 204, sh)
		assertDockedBeside(bag, sw, sh, 120, itemList)
		assertTrue(AccessorySidebarLayout.dock(bag, sw, sh, 210, 120)!!.right <= bag.x)

		assertDockedBeside(Box((480 - 360) / 2 + 40, 5, 360, 260), 480, 270, 400, null)
		assertDockedBeside(Box(70, 5, 360, 260), 520, 270, 100, null)

		val tall = AccessorySidebarLayout.dock(bag, sw, sh, 210, 2000)!!
		assertTrue(tall.h <= sh - 4)
		assertEquals(2000 - tall.h, AccessorySidebarLayout.clampScroll(99999, 2000, tall.h))
		assertEquals(0, AccessorySidebarLayout.clampScroll(-5, 2000, tall.h))

		assertNull(AccessorySidebarLayout.dock(Box(10, 5, 340, 260), 360, 270, 210, 100))
	}

	@Test fun tileNamesWrapToFullText() {
		val measure = { s: String -> s.replace(Regex("§."), "").length * 6 }
		val (name, info) = HubTileText.wrap("§6Personal Compactor 7000", "Accessory Power: +16", 78, measure)
		assertEquals("Personal Compactor 7000", name.joinToString(" ") { it.replace(Regex("§."), "") })
		assertTrue(name.all { measure(it) <= 78 } && info.all { measure(it) <= 78 })
		assertFalse((name + info).any { it.contains("…") })
		assertEquals(30, HubTileText.height(1, 1))
		assertTrue(HubTileText.height(name.size, info.size) > 30)
		for (t in model.allTiles) {
			val (n, _) = HubTileText.wrap(t.name, t.info.firstOrNull(), 78, measure)
			assertEquals(t.name.replace(Regex("§."), ""), n.joinToString(" ") { it.replace(Regex("§."), "") }.replace("  ", " "))
		}
	}
}
