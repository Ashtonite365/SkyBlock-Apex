// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.salvage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.h6.HubLayout
import dev.ashtonite365.apex.features.menus.salvage.SalvageButtonLayout
import dev.ashtonite365.apex.features.menus.salvage.SalvageMenu
import dev.ashtonite365.apex.features.menus.salvage.SalvageParser
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SalvageButtonLayoutTest {

	private val measure: (String) -> Int = { s ->
		var w = 0; var i = 0
		while (i < s.length) {
			if (s[i] == '§') { i += 2; continue }
			w += when (s[i]) { 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2; 'l' -> 3; 'I', 't', '[', ']', ' ' -> 4; 'f', 'k', '<', '>', '(', ')' -> 5; else -> 6 }
			i++
		}
		w
	}

	private data class R(val x: Int, val y: Int, val w: Int, val h: Int) {
		fun overlaps(o: R) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
	}

	private val screens = listOf(320 to 180, 360 to 202, 400 to 225, 455 to 256, 480 to 270, 640 to 360, 683 to 384, 960 to 540, 1280 to 720)

	@Test fun leftOfInventoryAndNeverOverlapping() {
		val (dw, dh) = SalvageMenu.defaultSize
		for ((sw, sh) in screens) {
			val b = MenuBounds.centred(sw, sh, dw, dh)
			val nb = SalvageButtonLayout.box(b.x, b.y, b.w, b.h, "Salvage Items", measure)
			assertNotNull(nb, "$sw x $sh"); val box = nb!!
			val r = R(box.x, box.y, box.w, box.h)
			val (ix, iy) = HubLayout.inventoryOrigin(b.x, b.y, b.w, b.h)
			val msg = "$sw x $sh: $box"

			assertTrue(box.x + box.w + SalvageButtonLayout.GAP == ix, msg)
			assertEquals(iy + 10, box.y, msg)
			assertTrue(box.x >= b.x + 6 && box.x >= 0 && box.y + box.h <= b.y + b.h && box.y + box.h <= sh, msg)

			assertTrue(box.w <= SalvageButtonLayout.MAX_W && box.w * box.h < 104 * 30, msg)

			assertFalse(r.overlaps(R(ix, iy, InventoryPanel.WIDTH, InventoryPanel.HEIGHT)), msg)
			for (row in 0..3) for (col in 0..8) {
				val (ox, oy) = InventoryPanel.offset(row, col)
				assertFalse(r.overlaps(R(ix + ox, iy + 10 + oy, 18, 18)), "$msg cell $row/$col")
			}

			val clipY = minOf(HubLayout.navTop(b.y, b.h) - ScrollClip.BAR_GAP, b.y + b.h - 22 - InventoryPanel.RESERVED)
			assertFalse(r.overlaps(R(b.x, b.y + 22, b.w, clipY - b.y - 22)), "$msg tiles")
			assertFalse(r.overlaps(R(b.x, b.y, b.w, 22)), "$msg header")
			assertFalse(r.overlaps(R(b.x, HubLayout.navTop(b.y, b.h), b.w, 14)), "$msg nav")

			assertEquals("Salvage Items", box.lines.joinToString(" "), msg)
			assertTrue(box.lines.all { measure(it) <= box.w - 2 * SalvageButtonLayout.PAD && !it.contains('…') }, msg)
		}
	}

	@Test fun tooNarrowFallsBackToTheGridTile() {

		assertNull(SalvageButtonLayout.box(0, 0, InventoryPanel.WIDTH + 20, 324, "Salvage Items", measure))

		assertNull(SalvageButtonLayout.box(0, 0, 360, 324, "Salvageeeeeeeeeeeeeee Items", measure))
	}

	@Test fun sameTileSameClickOnlyMovedOutOfTheGrid() {
		val real = RealDumpFixtures.load("salvage-items-1301-19")
		val m = SalvageParser.parse(real.title, real.slots)!!
		val t = m.sections.first().tiles.first(SalvageButtonLayout::isButton)
		assertEquals(40, t.slot); assertEquals(0, t.button); assertFalse(t.delayed); assertTrue(t.clickable)
		val rest = SalvageButtonLayout.withoutTile(m, t.slot)
		assertEquals(m.allTiles.filter { it.slot != 40 }, rest.allTiles)
		assertEquals(m.nav, rest.nav)
		assertEquals(listOf(50, 51, 52), rest.sections.first().tiles.map { it.slot })
	}

	@Test fun previewNumbers() {
		val (dw, dh) = SalvageMenu.defaultSize
		val real = RealDumpFixtures.load("salvage-items-1301-19")
		val m = SalvageParser.parse(real.title, real.slots)!!
		for ((sw, sh) in listOf(640 to 360, 320 to 180)) {
			val b = MenuBounds.centred(sw, sh, dw, dh)
			val box = SalvageButtonLayout.box(b.x, b.y, b.w, b.h, "Salvage Items", measure)!!
			val (ix, iy) = HubLayout.inventoryOrigin(b.x, b.y, b.w, b.h)
			val clipY = minOf(HubLayout.navTop(b.y, b.h) - ScrollClip.BAR_GAP, b.y + b.h - 22 - InventoryPanel.RESERVED)
			println("PREVIEW screen=$sw,$sh menu=${b.x},${b.y},${b.w},${b.h} inv=$ix,$iy,${InventoryPanel.WIDTH},${InventoryPanel.HEIGHT} " +
				"box=${box.x},${box.y},${box.w},${box.h} lines=${box.lines.joinToString("|")} clipY=$clipY navTop=${HubLayout.navTop(b.y, b.h)} " +
				"tiles=${SalvageButtonLayout.withoutTile(m, 40).sections.joinToString(";") { s -> s.title + ":" + s.tiles.joinToString(",") { it.name } }} " +
				"nav=${m.nav.joinToString(",") { it.first }}")
		}
	}
}

class SalvageConfirmStateTest {
	private val measure: (String) -> Int = { s -> s.filter { it != '§' }.length * 6 }
	private val before = RealDumpFixtures.load("salvage-items-1859-before-click")
	private val confirm = RealDumpFixtures.load("salvage-items-1859-confirm")

	@Test fun confirmPageStaysReadableWithTheSameButton() {
		val a = SalvageParser.parse(before.title, before.slots)!!
		val b = SalvageParser.parse(confirm.title, confirm.slots)
		assertNotNull(b, "Confirm Salvage page must keep the Apex menu up")
		val ta = a.sections.first().tiles.first(SalvageButtonLayout::isButton)
		val tb = b!!.sections.first().tiles.first(SalvageButtonLayout::isButton)
		assertEquals("Salvage Items" to 40, ta.name to ta.slot)
		assertEquals("Confirm Salvage" to 40, tb.name to tb.slot)
		assertFalse(ta.highlight); assertTrue(tb.highlight)

		for (t in listOf(ta, tb)) { assertEquals(0, t.button); assertFalse(t.delayed); assertTrue(t.clickable) }
		assertEquals(a.allTiles.filter { it.slot != 40 }.map { it.slot }, b.allTiles.filter { it.slot != 40 }.map { it.slot })
		assertTrue(SalvageParser.BUTTON.matches("Salvage Item") && !SalvageParser.BUTTON.matches("Salvage Assist"))
	}

	@Test fun hittingTheDrawnBoxSendsTheButtonClickOnBothStates() {
		val (dw, dh) = SalvageMenu.defaultSize
		for (d in listOf(before, confirm)) {
			val m = SalvageParser.parse(d.title, d.slots)!!
			val t = m.sections.first().tiles.first(SalvageButtonLayout::isButton)
			for ((sw, sh) in listOf(640 to 360, 320 to 180, 1280 to 720)) {
				val bnd = MenuBounds.centred(sw, sh, dw, dh)
				val box = SalvageButtonLayout.box(bnd.x, bnd.y, bnd.w, bnd.h, t.name, measure)
				assertNotNull(box, "${t.name} $sw x $sh"); box!!
				assertEquals(t.name, box.lines.joinToString(" "))
				val cx = box.x + box.w / 2.0; val cy = box.y + box.h / 2.0
				assertEquals(40 to 0, SalvageButtonLayout.clickAt(box, t, cx, cy, 0), "${t.name} $sw x $sh centre")
				assertEquals(40 to 0, SalvageButtonLayout.clickAt(box, t, box.x.toDouble(), box.y.toDouble(), 0))
				assertEquals(40 to 0, SalvageButtonLayout.clickAt(box, t, box.x + box.w - 0.5, box.y + box.h - 0.5, 0))
				assertEquals(40 to 1, SalvageButtonLayout.clickAt(box, t, cx, cy, 1))
				assertNull(SalvageButtonLayout.clickAt(box, t, box.x - 1.0, cy, 0))
				assertNull(SalvageButtonLayout.clickAt(box, t, cx, (box.y + box.h).toDouble(), 0))

				val (ix, iy) = HubLayout.inventoryOrigin(bnd.x, bnd.y, bnd.w, bnd.h)
				assertNull(InventoryPanel.cellAt(ix to iy, 90, cx, cy))
			}
		}
	}
}
