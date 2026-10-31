// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.perf

import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayGeometry
import dev.ashtonite365.apex.features.menus.style.Shapes
import me.shedaniel.math.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuPerf1031Test {
	@Test fun openPageIsSizedFromTheLiveChest() {
		assertEquals(5, StorageOverlayGeometry.boxRows(null, isOpen = true, liveRows = 5))
		assertEquals(5, StorageOverlayGeometry.boxRows(2, isOpen = true, liveRows = 5))
		assertEquals(2, StorageOverlayGeometry.boxRows(2, isOpen = false, liveRows = 5))
		assertEquals(null, StorageOverlayGeometry.boxRows(null, isOpen = false, liveRows = 0))
	}

	@Test fun pageRowsNeverOverlapWithTheGap() {
		val g = StorageOverlayGeometry(1920 / 2, 1080 / 2, 3, 400)
		val heights = listOf(5, null, 3, 4, 5, 1, null, 2).map { StorageOverlayGeometry.pageHeight(it, 9) }
		val boxes = g.layoutPages(heights, 0F, StorageOverlayGeometry.ROW_GAP).boxes
		for (a in boxes) for (b in boxes) if (a !== b) {
			val overlap = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
			assertFalse(overlap, "$a overlaps $b")
		}

		val box = boxes[0]
		val lastSlotBottom = StorageOverlayGeometry.Page.slotY(box.y, 44, 9) + 16
		assertTrue(lastSlotBottom <= box.y + box.height)
	}

	@Test fun noGapKeepsTheRecordedLayout() {
		val g = StorageOverlayGeometry(960, 540, 3, 400)
		val h = listOf(100, 60, 18, 90)
		assertEquals(g.layoutPages(h, 0F).contentHeight, 100 + 90)
		assertEquals(g.layoutPages(h, 0F, 4).contentHeight, 100 + 4 + 90)
	}

	@Test fun offPanelPagesAreCulled() {
		val panel = Rectangle(0, 100, 200, 200)
		assertTrue(StorageOverlayGeometry.intersectsPanel(Rectangle(0, 90, 166, 20), panel))
		assertFalse(StorageOverlayGeometry.intersectsPanel(Rectangle(0, 0, 166, 100), panel))
		assertFalse(StorageOverlayGeometry.intersectsPanel(Rectangle(0, 300, 166, 50), panel))
	}

	@Test fun cornerTableMatchesTheFormula() {
		for (r in 1..40) for (row in 0 until r) {
			val rr = r.toDouble(); val dy = rr - row - 0.5
			val want = kotlin.math.round(rr - Math.sqrt(maxOf(0.0, rr * rr - dy * dy))).toInt().coerceIn(0, r)
			assertEquals(want, Shapes.cornerInset(r, row), "r=$r row=$row")
		}
		assertEquals(0, Shapes.cornerInset(6, 6))
		assertEquals(0, Shapes.cornerInset(0, 0))
	}
}
