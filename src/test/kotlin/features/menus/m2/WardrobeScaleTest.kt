package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeMenu
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeScale

class WardrobeScaleTest {
	private val bw = WardrobeMenu.defaultSize.first
	private val bh = WardrobeMenu.defaultSize.second

	@Test fun fullFactorWhenItFits() {
		val f = WardrobeScale.fit(bw, bh, 1280, 720)
		assertEquals(1.2f, f.scale)
		assertEquals((bw * 1.2f).toInt(), f.w); assertEquals((bh * 1.2f).toInt(), f.h)
		assertEquals((1280 - f.w) / 2, f.x); assertEquals((720 - f.h) / 2, f.y)
		assertEquals(bw, f.logicalW); assertEquals(bh, f.logicalH)
	}

	@Test fun clampsDownToFitButNotBelowOne() {

		val f = WardrobeScale.fit(bw, bh, 640, 360)
		assertTrue(f.scale > 1f && f.scale < 1.2f, "scale ${f.scale}")
		assertEquals((360 - WardrobeScale.MARGIN).toFloat() / bh, f.scale, 1e-4f)
		assertTrue(f.w <= 640 - WardrobeScale.MARGIN && f.h <= 360 - WardrobeScale.MARGIN)
		assertTrue(f.x >= 0 && f.y >= 0)

		assertEquals(1f, WardrobeScale.scale(bw, bh, bw + WardrobeScale.MARGIN, bh + WardrobeScale.MARGIN))

		assertTrue(WardrobeScale.scale(bw, bh, bw + 40, bh + 40) >= 1f)
	}

	@Test fun belowOneOnlyWhenOneDoesNotFit() {

		val f = WardrobeScale.fit(bw, bh, 480, 270)
		assertTrue(f.scale < 1f)
		assertTrue(f.w <= 480 - WardrobeScale.MARGIN && f.h <= 270 - WardrobeScale.MARGIN)
		assertEquals(1f, WardrobeScale.scale(0, 0, 100, 100))
	}

	@Test fun screenAndLayoutCoordinatesRoundTrip() {
		val f = WardrobeScale.fit(bw, bh, 1280, 720)
		assertEquals(f.x, f.toScreenX(f.x)); assertEquals(f.y, f.toScreenY(f.y))
		assertTrue(kotlin.math.abs(f.x + f.w - f.toScreenX(f.x + bw)) <= 1, "the layout right edge lands on the scaled bounds")
		val lx = f.x + 100; val ly = f.y + 50
		assertEquals(lx.toDouble(), f.toLocalX(f.toScreenX(lx).toDouble()), 0.5)
		assertEquals(ly.toDouble(), f.toLocalY(f.toScreenY(ly).toDouble()), 0.5)
	}

	@Test fun scaledCellHitBoxMatchesTheScaledFrame() {
		val s = 1.2f

		assertTrue(InventoryPanel.hitsCell(100, 100, 99.0, 99.0)); assertFalse(InventoryPanel.hitsCell(100, 100, 117.0, 108.0))

		assertTrue(InventoryPanel.hitsCell(100, 100, 100 + 17 * s - 0.01, 108.0, s))
		assertFalse(InventoryPanel.hitsCell(100, 100, 100 + 17 * s + 0.01, 108.0, s))
		assertTrue(InventoryPanel.hitsCell(100, 100, 100 - s + 0.01, 100 - s + 0.01, s))
		assertFalse(InventoryPanel.hitsCell(100, 100, 100 - s - 0.01, 108.0, s))
		val cells = mapOf(9 to (100 to 100))
		assertEquals(9, InventoryPanel.cellAt(null, 90, 120.0, 108.0, cells, s))
		assertEquals(null, InventoryPanel.cellAt(null, 90, 120.0, 108.0, cells))
		assertTrue(InventoryPanel.hitsSlot(9, 90, null, 100, 100, 120.0, 108.0, cells, s))
	}
}
