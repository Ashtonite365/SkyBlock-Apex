package dev.ashtonite365.apex.test.features.items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.ViewerGridRows
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs

class ViewerGridRowsTest {
	@Test
	fun pureRowRule() {

		assertEquals(22, ViewerGridRows.rows(22, 16, fill = true))
		assertEquals(40, ViewerGridRows.rows(40, 1, fill = true))

		assertEquals(16, ViewerGridRows.rows(22, 16, fill = false))

		assertEquals(9, ViewerGridRows.rows(9, 16, fill = true))
		assertEquals(9, ViewerGridRows.rows(9, 16, fill = false))
		assertEquals(0, ViewerGridRows.rows(0, 16, fill = true))
		assertEquals(-1, ViewerGridRows.rows(-1, 16, fill = true))
	}

	@Test
	fun onlyTheItemListFillsAndOnlyWithTheStrip() {
		val before = ViewerGridRows.stripActive
		try {
			ViewerGridRows.stripActive = true
			ViewerGridRows.beginLayout(ViewerGridRows.isItemListGrid("IngredientListOverlay"))
			assertEquals(22, ViewerGridRows.rows(22, 16))
			ViewerGridRows.endLayout()
			assertEquals(16, ViewerGridRows.rows(22, 16))
			for (other in listOf("BookmarkOverlay", "IngredientListLookupHistory", "BookmarkLookupHistory", null)) {
				assertFalse(ViewerGridRows.isItemListGrid(other))
				ViewerGridRows.beginLayout(ViewerGridRows.isItemListGrid(other))
				assertEquals(16, ViewerGridRows.rows(22, 16), "$other keeps the user's limit")
				ViewerGridRows.endLayout()
			}
			ViewerGridRows.stripActive = false
			ViewerGridRows.beginLayout(true)
			assertEquals(16, ViewerGridRows.rows(22, 16))
			ViewerGridRows.endLayout()
		} finally {
			ViewerGridRows.stripActive = before
			ViewerGridRows.endLayout()
		}
	}

	@Test
	fun gridReachesTheTabsWithMaxRows16() {
		val four = listOf("items" to 46, "mobs" to 40, "howto" to 76, "prices" to 72)
		val cfg = ViewerTabs.JeiListConfig(maxRows = 16)

		val screens = listOf(Triple(514, 853, 480), Triple(408, 640, 360), Triple(728, 1280, 720), Triple(568, 960, 540),
			Triple(328, 480, 270), Triple(301, 427, 240), Triple(248, 320, 240))
		for ((gr, sw, sh) in screens) {
			val panel = ViewerTabs.panelFromJeiLayout(gr, (sh + 166) / 2, sw, sh, cfg) ?: continue
			val band = ViewerTabs.stripExclusion(ViewerTabs.layout(panel.bottom, panel.left, panel.right, four), panel)!!
			val gridTop = 28; val listBottom = sh - 22 - 6
			val fit = (listBottom - gridTop) / 18
			fun drawnRows(rows: Int) = (0 until rows).filter { r ->
				val y = gridTop + r * 18
				!(y - 2 < band.y + band.h && y + 20 > band.y)
			}
			val ctx = "${sw}x$sh"
			val filled = drawnRows(ViewerGridRows.rows(fit, cfg.maxRows, fill = true))
			val lastBottom = gridTop + (filled.max() + 1) * 18
			assertTrue(lastBottom + 2 <= band.y, ctx)
			assertTrue(band.y - lastBottom < 18 + 2, "$ctx: ${band.y - lastBottom} px blank above the tabs")
			assertEquals(ViewerTabs.rowsThatFit(band.y, cfg), filled.size, ctx)
			assertEquals(filled.size, filled.max() + 1, ctx)

			val capped = drawnRows(ViewerGridRows.rows(fit, cfg.maxRows, fill = false))
			assertEquals(minOf(filled.size, 16), capped.size, ctx)
		}

		val p = ViewerTabs.panelFromJeiLayout(514, 323, 853, 480, cfg)!!
		val band = ViewerTabs.stripExclusion(ViewerTabs.layout(p.bottom, p.left, p.right, four), p)!!
		assertEquals(21, ViewerTabs.rowsThatFit(band.y, cfg))
		assertEquals(23, ViewerGridRows.rows((480 - 28 - 28) / 18, 16, fill = true))
	}
}
