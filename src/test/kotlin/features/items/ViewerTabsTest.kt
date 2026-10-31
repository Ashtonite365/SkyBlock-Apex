package dev.ashtonite365.apex.test.features.items

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.ViewerTab
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs

class ViewerTabsTest {
	@Test
	fun stripSitsInsideThePanelAboveTheSearchBarAndWrapsInsteadOfTruncating() {

		val panel = ViewerTabs.panelFromGui(301, 480, 270)
		assertEquals(301 + ViewerTabs.PANEL_MARGIN, panel.left)
		assertEquals(480 - ViewerTabs.PANEL_MARGIN, panel.right)
		assertEquals(270 - ViewerTabs.SEARCH_BAR_H - ViewerTabs.SEARCH_LIFT, panel.bottom)
		val four = listOf("items" to 46, "mobs" to 40, "howto" to 76, "prices" to 72)
		val rects = ViewerTabs.layout(panel.bottom, panel.left, panel.right, four)
		assertTrue(rects.all { it.x >= panel.left && it.x + it.w <= panel.right })
		assertTrue(rects.all { it.y + it.h <= panel.bottom })
		assertTrue(rects.all { ViewerTabs.drawnBox(it, true).y >= 0 })
		assertEquals(panel.bottom - ViewerTabs.TAB_H, rects.maxOf { it.y })

		rects.zipWithNext().filter { (a, b) -> a.y == b.y }.forEach { (a, b) -> assertEquals(a.x + a.w, b.x) }

		for ((gr, sw, sh) in listOf(Triple(568, 960, 540), Triple(408, 640, 360), Triple(248, 320, 240))) {
			val p = ViewerTabs.panelFromGui(gr, sw, sh)
			assertTrue(ViewerTabs.layout(p.bottom, p.left, p.right, four).all { it.x >= p.left && it.x + it.w <= maxOf(p.right, it.x + it.w) && it.y + it.h <= p.bottom })
		}
		val narrow = ViewerTabs.layout(216, 300, 340, listOf("items" to 36, "a-very-long-tab" to 90))
		assertEquals(90, narrow.last().w)
		assertTrue(narrow.last().y > narrow.first().y)
	}

	@Test
	fun screenshotGeometryLinesUpWithJeisListAndSearchBar() {
		val old = ViewerTabs.panelFromGui(408, 640, 360)
		assertEquals(414, old.left)
		val cfg = ViewerTabs.JeiListConfig.parse("[search]\n\tcenterSearch = STANDARD\n[lists]\n\tdrawBackground = NONE\n[ingredientList]\n\tmaxRows = 16\n\tmaxColumns = 9\n\thorizontalAlignment = RIGHT\n[bookmarkList]\n\tmaxColumns = 4\n\thorizontalAlignment = LEFT\n")
		assertEquals(ViewerTabs.JeiListConfig(9, "RIGHT", false, false), cfg)
		val p = ViewerTabs.panelFromJeiLayout(408, (360 + 166) / 2, 640, 360, cfg)!!
		assertEquals(472, p.left); assertEquals(634, p.right)
		assertEquals(334 - ViewerTabs.SEARCH_LIFT, p.bottom)
		val four = listOf("items" to 46, "mobs" to 40, "howto" to 76, "prices" to 72)
		val r = ViewerTabs.layout(p.bottom, p.left, p.right, four)
		assertEquals(472, r.minOf { it.x })
		assertTrue(r.all { it.x >= p.left && it.x + it.w <= p.right })
		assertEquals(2, r.map { it.y }.distinct().size)

		val s4 = ViewerTabs.panelFromJeiLayout(328, 218, 480, 270, cfg)!!
		assertEquals(348, s4.left); assertEquals(474, s4.right); assertEquals(244 - ViewerTabs.SEARCH_LIFT, s4.bottom)

		assertEquals(414, ViewerTabs.panelFromJeiLayout(408, 263, 640, 360, cfg.copy(alignment = "LEFT"))!!.left)
		assertEquals(443, ViewerTabs.panelFromJeiLayout(408, 263, 640, 360, cfg.copy(alignment = "CENTER"))!!.left)
		assertEquals(418, ViewerTabs.panelFromJeiLayout(408, 263, 640, 360, cfg.copy(maxColumns = 12))!!.left)
		assertEquals(null, ViewerTabs.panelFromJeiLayout(600, 263, 640, 360, cfg))
		assertEquals(ViewerTabs.JeiListConfig(), ViewerTabs.JeiListConfig.parse("garbage"))
	}

	@Test
	fun gridFillsDownToTheStripWithOneFullWidthBand() {
		val four = listOf("items" to 46, "mobs" to 40, "howto" to 76, "prices" to 72)
		val three = four.take(3)

		val screens = listOf(Triple(408, 640, 360), Triple(514, 853, 480), Triple(728, 1280, 720), Triple(328, 480, 270), Triple(568, 960, 540), Triple(354, 532, 300))
		for ((gr, sw, sh) in screens) for (tabs in listOf(three, four)) {
			val cfg = ViewerTabs.JeiListConfig(maxRows = 100)
			val panel = ViewerTabs.panelFromJeiLayout(gr, (sh + 166) / 2, sw, sh, cfg) ?: continue
			val rects = ViewerTabs.layout(panel.bottom, panel.left, panel.right, tabs)
			val band = ViewerTabs.stripExclusion(rects, panel)!!
			val ctx = "screen ${sw}x$sh, ${tabs.size} tabs"

			assertEquals(panel.left, band.x, ctx); assertEquals(panel.right, band.x + band.w, ctx)
			for (r in rects) {
				val d = ViewerTabs.drawnBox(r, true)
				assertTrue(d.y >= band.y && d.y + d.h <= band.y + band.h && d.x >= band.x && d.x + d.w <= band.x + band.w, ctx)
			}
			assertEquals(rects.minOf { it.y } - ViewerTabs.SELECTED_RISE - ViewerTabs.STRIP_MARGIN, band.y, ctx)
			assertTrue(band.y + band.h <= sh - 6 - 20, ctx)

			val gridTop = 28; val listBottom = sh - 22 - 6
			val cols = (panel.right - panel.left) / 18
			val drawn = mutableListOf<Pair<Int, Int>>()
			var row = 0
			while (gridTop + (row + 1) * 18 <= listBottom) {
				for (c in 0 until cols) {
					val x = panel.left + c * 18; val y = gridTop + row * 18
					val blocked = x - 2 < band.x + band.w && x + 20 > band.x && y - 2 < band.y + band.h && y + 20 > band.y
					if (!blocked) drawn.add(row to c)
				}
				row++
			}

			drawn.groupBy { it.first }.values.forEach { assertEquals(cols, it.size, ctx) }
			val lastRow = drawn.maxOf { it.first }
			val lastBottom = gridTop + (lastRow + 1) * 18

			assertTrue(lastBottom + 2 <= band.y, ctx)
			assertTrue(band.y - lastBottom < 18 + 2, "$ctx: ${band.y - lastBottom} px blank above the strip")
			assertEquals(lastRow + 1, ViewerTabs.rowsThatFit(band.y, cfg), ctx)
		}

		val cfg16 = ViewerTabs.JeiListConfig.parse("[ingredientList]\n\tmaxRows = 16\n\tmaxColumns = 9\n")
		assertEquals(16, cfg16.maxRows)
		val tall = ViewerTabs.panelFromJeiLayout(514, 323, 853, 480, cfg16)!!
		val band = ViewerTabs.stripExclusion(ViewerTabs.layout(tall.bottom, tall.left, tall.right, three), tall)!!
		assertEquals(22, ViewerTabs.rowsThatFit(band.y, cfg16))
		assertEquals(6, ViewerTabs.rowsLostToMaxRows(band.y, cfg16))
		assertEquals(0, ViewerTabs.rowsLostToMaxRows(band.y, cfg16.copy(maxRows = 100)))
		assertEquals(100, ViewerTabs.JeiListConfig.parse("[ingredientList]\nmaxRows = 999\n").maxRows)
		assertEquals(16, ViewerTabs.JeiListConfig.parse("[bookmarkList]\nmaxRows = 40\n").maxRows)
		assertEquals(null, ViewerTabs.stripExclusion(listOf(), tall))
	}

	@Test
	fun noHistoryButtons() {
		ViewerTabs.resetForTest()
		ViewerTabs.register(ViewerTab("items", Component.literal("Items"), 0, { }))
		ViewerTabs.register(ViewerTab("mobs", Component.literal("Mobs"), 10, { }))
		val ids = ViewerTabs.layout(200, 0, 300, ViewerTabs.registered().map { it.id to 40 }).map { it.id }
		assertEquals(listOf("items", "mobs"), ids)
		ViewerTabs.resetForTest()
	}

	@Test
	fun navigateSwitchesTabs() {
		ViewerTabs.resetForTest()
		val opened = mutableListOf<String>()
		ViewerTabs.register(ViewerTab("items", Component.literal("Items"), 0, { opened.add("items:$it") }))
		ViewerTabs.register(ViewerTab("mobs", Component.literal("Mobs"), 10, { opened.add("mobs:$it") }))
		assertFalse(ViewerTabs.navigate("prices", "X"))
		assertTrue(ViewerTabs.navigate("mobs", "scatha"))
		assertEquals("mobs", ViewerTabs.activeTab)
		assertEquals(listOf("mobs:scatha"), opened)
		ViewerTabs.resetForTest()
	}

	@Test
	fun renderPreview() {
		val sw = 427; val sh = 240; val s = 3
		val pr = ViewerTabs.panelFromJeiLayout((sw + 176) / 2, (sh + 166) / 2, sw, sh)!!; val panelL = pr.left; val panelR = pr.right
		val labels = listOf("items" to "Items", "mobs" to "Mobs", "howto" to "How to get", "prices" to "Bazaar/AH")
		val widths = mapOf("items" to 41, "mobs" to 37, "howto" to 61, "prices" to 61)
		val rects = ViewerTabs.layout(pr.bottom, panelL, panelR, labels.map { it.first to widths.getValue(it.first) })
		val img = BufferedImage(sw * s, sh * s, BufferedImage.TYPE_INT_RGB)
		val g = img.createGraphics()
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
		g.scale(s.toDouble(), s.toDouble())
		g.color = Color(0x3C3C3C); g.fillRect(0, 0, sw, sh)
		g.color = Color(0xC6C6C6); g.fillRect((sw - 176) / 2, (sh - 166) / 2, 176, 166)
		g.color = Color(0x8B8B8B)
		for (row in 0 until 9) for (col in 0 until 6) g.fillRect(panelL + col * 18, 6 + row * 18, 16, 16)
		g.color = Color(0x000000); g.fillRect(panelL, sh - 26, panelR - panelL, 20)
		g.color = Color(0xA0A0A0); g.font = g.font.deriveFont(8f); g.drawString("search...", panelL + 4, sh - 9)
		for (r in rects) {
			g.color = Color.BLACK; g.fillRect(r.x, r.y, r.w, r.h)
			g.color = if (r.id == "items") Color(0xC6C6C6) else Color(0x8B8B8B); g.fillRect(r.x + 1, r.y + 1, r.w - 2, r.h - 2)
			val text = labels.first { it.first == r.id }.second
			g.color = Color.WHITE
			g.drawString(text, r.x + (r.w - g.fontMetrics.stringWidth(text)) / 2, r.y + 10)
		}
		g.dispose()
		val out = File(System.getProperty("apex.previewOut", "jei-tabs-preview.png"))
		ImageIO.write(img, "png", out)
		assertTrue(out.length() > 0)
	}
}
