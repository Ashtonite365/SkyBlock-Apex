package dev.ashtonite365.apex.test.features.menus.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.InventoryPanel

class InventoryPanelNativeSlotsTest {
	private val window = 54 + 36
	private val origin = 100 to 200
	private val left = 40; private val top = 30

	@Test fun playerSlotsSitExactlyUnderTheirDrawnCells() {
		for (row in 0..3) for (col in 0..8) {
			val idx = InventoryPanel.slotIndex(row, col, 54)
			val (ox, oy) = InventoryPanel.offset(row, col)
			val cellX = origin.first + ox; val cellY = origin.second + 10 + oy
			val (sx, sy) = InventoryPanel.slotPlacement(idx, window, origin, left, top)!!

			assertEquals(cellX + 1, sx + left); assertEquals(cellY + 1, sy + top)
		}
		assertEquals((100 + 1 - 40) to (200 + 10 + 58 + 1 - 30), InventoryPanel.slotPlacement(81, window, origin, left, top))
	}

	@Test fun chestSlotsAndPanelLessPagesStayHidden() {
		for (i in 0 until 54) assertNull(InventoryPanel.slotPlacement(i, window, origin, left, top))
		assertNull(InventoryPanel.slotPlacement(60, window, null, left, top))
		assertNull(InventoryPanel.slotPlacement(90, window, origin, left, top))
		assertNull(InventoryPanel.slotPlacement(0, 20, origin, left, top))
	}

	@Test fun hitTestingOnlyForInventorySlots() {
		val (sx, sy) = InventoryPanel.itemPos(0, 0, origin.first, origin.second)
		assertTrue(InventoryPanel.hitsSlot(54, window, origin, sx, sy, sx + 8.0, sy + 8.0))
		assertTrue(InventoryPanel.hitsSlot(54, window, origin, sx, sy, sx - 1.0, sy - 1.0))
		assertFalse(InventoryPanel.hitsSlot(54, window, origin, sx, sy, sx + 17.0, sy + 8.0))

		for (i in 0 until 54) assertFalse(InventoryPanel.hitsSlot(i, window, origin, sx, sy, sx + 8.0, sy + 8.0))
		assertFalse(InventoryPanel.hitsSlot(54, window, null, sx, sy, sx + 8.0, sy + 8.0))
	}

	@Test fun cellLookupCoversEveryCellAndNothingElse() {
		val seen = mutableSetOf<Int>()
		for (row in 0..3) for (col in 0..8) {
			val (sx, sy) = InventoryPanel.itemPos(row, col, origin.first, origin.second)
			seen += InventoryPanel.cellAt(origin, window, sx + 8.0, sy + 8.0)!!
		}
		assertEquals((54 until 90).toSet(), seen)
		assertNull(InventoryPanel.cellAt(origin, window, 50.0, 50.0))
		assertNull(InventoryPanel.cellAt(origin, window, origin.first + 5.0, origin.second + 3.0))
		assertNull(InventoryPanel.cellAt(origin, window, origin.first + 5.0, origin.second + 10 + 54 + 2.0))
		assertNull(InventoryPanel.cellAt(null, window, origin.first + 5.0, origin.second + 15.0))
	}

	@Test fun dragSpreadGestureFollowsThePressToVanilla() {
		val g = InventoryPanel.Gesture()

		assertTrue(g.press(true)); assertTrue(g.drag()); assertTrue(g.drag()); assertTrue(g.release())
		assertFalse(g.drag())

		assertFalse(g.press(false)); assertFalse(g.drag()); assertFalse(g.release())
		assertTrue(g.scroll(true)); assertFalse(g.scroll(false))
	}
}
