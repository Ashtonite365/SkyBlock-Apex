// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.collections.TileLayout
import dev.ashtonite365.apex.features.menus.collections.TileSpec
import dev.ashtonite365.apex.features.menus.h6.BoosterCookieParser
import dev.ashtonite365.apex.features.menus.h6.HubLayout
import dev.ashtonite365.apex.features.menus.skyblock.SbTile
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenu
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuLayout
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuParser
import dev.ashtonite365.apex.features.menus.support.ChestFrame
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ApexLayoutTest {

	private fun mc(s: String): Int = s.replace(Regex("§."), "").sumOf { c ->
		when (c) {
			'i', '!', '.', ',', ':', ';', '\'', '|', '·' -> 2
			'l', '`' -> 3
			't', 'I', '[', ']', ' ', '(', ')', '"', '*' -> 4
			'f', 'k', '<', '>', '{', '}' -> 5
			'@', '~' -> 7
			else -> 6
		}
	}
	private fun MenuBounds.overlaps(o: MenuBounds) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
	private fun MenuBounds.inside(o: MenuBounds) = x >= o.x && y >= o.y && x + w <= o.x + o.w && y + h <= o.y + o.h

	@Test fun chestFrameIsVanillasGenericContainer() {
		val f = ChestFrame.of(480, 270)
		assertEquals(MenuBounds(152, 24, 176, 222), f.bounds)
		assertEquals(6, ChestFrame.rowsFor(90)); assertEquals(3, ChestFrame.rowsFor(63))
		assertEquals(152 + 8 to 24 + 18, f.slot(0))
		assertEquals(152 + 8 + 4 * 18 to 24 + 18 + 18, f.slot(13))
		assertEquals(24 + 18 + 108, f.chestBottom)
		assertEquals(24 + 222 - 82, f.inventoryTop); assertEquals(24 + 222 - 24, f.hotbarTop)
		assertEquals(13, f.slotAt(f.slot(13).first + 5, f.slot(13).second + 5))
		assertNull(f.slotAt(f.x, f.y))

		assertFalse(SkyBlockMenu.chestFrame)
	}

	private val sb = RealDumpFixtures.load("skyblock-menu")

	private val gui3 = 640 to 360
	private val gui4 = 480 to 270

	private fun sbBounds(sw: Int, sh: Int) = MenuBounds.centred(sw, sh, SkyBlockMenu.defaultSize.first, SkyBlockMenu.defaultSize.second)

	@Test fun skyBlockMenuNewLayout() {
		for (fixture in listOf("skyblock-menu", "skyblock-menu-0922")) {
			val d = RealDumpFixtures.load(fixture)
			val m = SkyBlockMenuParser.parse(d.title, d.slots)!!
			for ((sw, sh) in listOf(gui4, gui3, 960 to 540)) {
				val b = sbBounds(sw, sh)
				assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= sw && b.y + b.h <= sh, "fits $sw x $sh: $b")
				assertTrue(b.h > 222 && b.w > 176, "taller and wider than the old chest footprint: $b")
				val cells = SkyBlockMenuLayout.cells(m, b)
				assertEquals(m.tiles.size, cells.size)
				fun cell(t: SbTile) = cells.first { it.tile == t }
				for (c in cells) {
					assertTrue(c.bounds.inside(b), "${c.tile} inside the panel on $sw x $sh")
					assertTrue(c.y >= b.y + SkyBlockMenuLayout.HEADER, "${c.tile} below the header")
				}
				for (a in cells) for (c in cells) if (a !== c) assertFalse(a.bounds.overlaps(c.bounds), "${a.tile} overlaps ${c.tile}")
				val vb = SkyBlockMenuLayout.vanillaButton(b, mc("Vanilla"))
				assertTrue(vb.inside(b)); assertTrue(cells.none { it.bounds.overlaps(vb) })

				val nav = listOf(SbTile.FAST_TRAVEL, SbTile.PROFILES, SbTile.CLOSE, SbTile.SETTINGS, SbTile.COOKIE).map(::cell)
				assertTrue(nav.all { it.y == nav[0].y && it.y + it.h == b.y + b.h - SkyBlockMenuLayout.PAD }, "nav pinned to the bottom")
				assertEquals(nav.sortedBy { it.x }, nav)
				assertTrue(nav.all { it.w >= 70 }, "nav tiles longer: ${nav.map { it.w }}")

				val stats = cell(SbTile.STATS)
				assertTrue(kotlin.math.abs(b.x + b.w / 2 - (stats.x + stats.w / 2)) <= 2, "stats centred")
				val mid = listOf(SbTile.SKILLS, SbTile.COLLECTIONS, SbTile.RECIPES, SbTile.LEVELING, SbTile.QUESTS, SbTile.CALENDAR,
					SbTile.STORAGE, SbTile.BAGS, SbTile.PETS, SbTile.CRAFTING, SbTile.LOADOUTS, SbTile.BANK).map(::cell)
				assertTrue(mid.all { it.y > stats.y && it.y + it.h < nav[0].y }, "middle rows between stats and nav")
				assertEquals(3, mid.map { it.y }.distinct().size, "12 tiles in 3 rows of 4")
				assertTrue(mid.all { it.w >= 90 } && stats.w >= 90, "wide tiles: ${mid.map { it.w }}")

				for (c in cells) {
					val t = SkyBlockMenuLayout.tileText(c, c.tile.label, m.tiles[c.tile]?.second, ::mc)
					assertTrue(t.box.inside(c.bounds), "${c.tile} text box inside")
					assertEquals(1f, t.scale, "${c.tile} at full size on $sw x $sh: ${t.lines}")
					assertTrue(t.lines.none { it.endsWith("…") }, "${c.tile} not truncated")
					assertEquals(c.tile.label, t.lines.take(t.labelLines).joinToString(" "), "${c.tile} full label")
					m.tiles[c.tile]?.second?.let { assertEquals(it, t.lines.drop(t.labelLines).joinToString(" "), "${c.tile} full info") }
					assertTrue(t.lines.all { mc(it) <= t.box.w } && t.height() <= t.box.h, "${c.tile} fits its tile")
				}
			}
		}

		val m = SkyBlockMenuParser.parse(sb.title, sb.slots)!!
		val b = sbBounds(gui4.first, gui4.second)
		val cells = SkyBlockMenuLayout.cells(m, b)
		val cal = cells.first { it.tile == SbTile.CALENDAR }; val cookie = cells.first { it.tile == SbTile.COOKIE }
		assertEquals(1f, SkyBlockMenuLayout.tileText(cal, cal.tile.label, "Early Autumn 31st", ::mc).scale)
		assertEquals(1f, SkyBlockMenuLayout.tileText(cookie, cookie.tile.label, "3d 23h 59m 59s", ::mc).scale)
	}

	@Test fun skyBlockMenuFitTextScalesBeforeCutting() {
		val f = SkyBlockMenuLayout.fitText("Personal Bank", 60, 20, ::mc)
		assertEquals(listOf("Personal", "Bank"), f.lines); assertEquals(1f, f.scale)
		val g = SkyBlockMenuLayout.fitText("Profile Management", 40, 9, ::mc)
		assertTrue(g.scale < 1f && g.scale >= SkyBlockMenuLayout.MIN_SCALE)
		assertTrue(g.lines.joinToString(" ").replace("…", "").isNotEmpty())
		assertTrue(g.lines.none { it.endsWith("…") } || g.scale == SkyBlockMenuLayout.MIN_SCALE, "cut only at the minimum scale")
	}

	@Test fun skyBlockMenuPositionsFollowTheLiveSlot() {

		val moved = sb.slots.map { if (it.cleanName == "Pets") it.copy(slot = 40) else if (it.slot == 40) it.copy(slot = 30) else it }
		val m = SkyBlockMenuParser.parse(sb.title, moved)!!
		val b = sbBounds(480, 270)
		val cells = SkyBlockMenuLayout.cells(m, b)
		val pets = cells.first { it.tile == SbTile.PETS }; val bank = cells.first { it.tile == SbTile.BANK }
		assertTrue(pets.y > bank.y || (pets.y == bank.y && pets.x > bank.x), "Pets now after Personal Bank")
	}

	@Test fun skyBlockMenuTooltipHasFullNameAndLiveInfo() {
		assertEquals(listOf("§fStats & Equipment"), SkyBlockMenuLayout.tooltip(SbTile.STATS, null))
		assertEquals(listOf("§fPersonal Bank", "§77 Hours"), SkyBlockMenuLayout.tooltip(SbTile.BANK, "7 Hours"))
	}

	@Test fun nineCookieStatsSplitFiveFourUntruncated() {
		val d = RealDumpFixtures.load("booster-cookie")
		val m = BoosterCookieParser.parse(d.title, d.slots)!!

		val extra = mapOf("Per cookie" to "+5,400 Bits", "Next rank" to "Citizen (26,900/80k)", "Cookie left" to "0d 12h 50m 33s")
		val chips = listOf("Bits Purse", "Bits Available", "Multiplier", "Cookie left", "Per cookie", "Fame", "Next rank", "Gems", "Locked")
			.map { l -> l to (m.chips.firstOrNull { it.first == l }?.second ?: extra.getValue(l)) }
		assertEquals(9, chips.size)
		val inner = dev.ashtonite365.apex.features.menus.h6.BoosterCookieMenu.defaultSize.first - 12
		assertEquals(3, (inner + 4) / (104 + 4), "tile grid keeps its 3 columns")
		val rows = HubLayout.chipRows(chips, inner, ::mc)
		assertEquals(listOf(5, 4), rows.map { it.size })
		assertEquals((0 until 9).toList(), rows.flatten())
		for (r in rows) {
			val row = r.map { chips[it] }
			val cols = HubLayout.chipColumns(row, inner, ::mc)
			assertEquals(inner, cols.sum(), "$row ${row.map { HubLayout.chipWidth(it, ::mc) }}")
			row.forEachIndexed { i, (l, v) ->
				assertEquals(l, MenuTheme.fit(l, cols[i] - 8, ::mc), "label $l not cut")
				assertEquals(v, MenuTheme.fit(v, cols[i] - 8, ::mc), "value $v not cut")
			}
		}

		assertEquals(1, HubLayout.chipRows(listOf("Mayor" to "Diaz", "Next" to "Spooky"), inner, ::mc).size)
	}

	@Test fun cookieViewportFloorIsBackButtonTop() {
		val y = 40; val h = 230
		val floor = HubLayout.navTop(y, h)
		assertEquals(y + h - 18, floor)

		val top = y + 22 + HubLayout.chipPanelHeight(2) + 4
		val gap = 4; val th = 30
		val heights = listOf(12, th + gap, th + gap, 12, th + gap, th + gap)
		val bottom = HubLayout.contentBottom(top, heights, true, gap)
		assertEquals(top + heights.sum() - gap, bottom)
		val ahRow = bottom - th
		assertTrue(ahRow < floor && bottom > floor, "AH/Bazaar row partly behind the floor by default")
		val max = HubLayout.maxScroll(bottom, floor)
		assertEquals(bottom - floor, max)
		assertEquals(floor, bottom - max, "fully scrolled: the last row's bottom stops exactly at the floor")
		assertEquals(0, HubLayout.maxScroll(floor - 5, floor), "no overscroll when everything fits")

		val tx = 10; val tw = 104
		assertEquals(floor - ahRow, HubLayout.visibleHeight(ahRow, th, floor))
		assertTrue(HubLayout.hit(tx + 5, floor - 1, tx, ahRow, tw, th, floor, top))
		assertFalse(HubLayout.hit(tx + 5, floor, tx, ahRow, tw, th, floor, top), "below the floor goes to Back")
		assertFalse(HubLayout.hit(tx + 5, top - 1, tx, top - 10, tw, th, floor, top), "scrolled above the viewport top")
		assertEquals(top to top + 20, HubLayout.visibleSpan(top - 10, th, top, floor))
		assertEquals(0, HubLayout.visibleHeight(floor + 2, th, floor))
	}

	@Test fun widenedGridKeepsColumnsAndCentres() {
		for (tileW in listOf(112, 104)) for (w in listOf(360, 400, 470, 520, 640, 900)) {
			val pad = 6
			val oldCols = TileLayout.cols(w - 2 * pad - 4, tileW, 3)
			val innerW = w - 2 * pad
			val g = TileLayout.widened(innerW, oldCols, tileW, 3)
			assertEquals(oldCols, g.cols, "cols at $w")
			assertEquals(innerW, 2 * g.margin + g.used, "margins equal at $w/$tileW")
			val maxW = (innerW - (g.cols - 1) * 3) / g.cols
			val ratio = g.tileW.toDouble() / tileW
			if (Math.round(tileW * TileLayout.WIDEN) < maxW - 1) assertTrue(ratio in 1.14..1.2, "ratio $ratio at $w")
			else assertTrue(g.tileW >= maxW - 1 && g.used <= innerW, "clamped at $w")
			assertTrue(g.tileW >= tileW || g.tileW >= maxW - 1)
		}
	}

	@Test fun recipeBookGrownRowsStayInTheFrame() {
		val (w, h) = 470 to 280
		val tiles = List(11) { TileSpec(it, "Cat $it", null, 0.5, 0, barText = "64.4% · 134/208") }
		val th = TileLayout.tileHeight(28, tiles)
		assertEquals(28, th)
		val cols = TileLayout.cols(w - 16, 104, 3)
		val g = TileLayout.widened(w - 12, cols, 104, 3)
		val top = 22 + 15 + 2; val bottom = h - 24
		assertTrue(TileLayout.height(listOf(11), g.cols, th, g.gap) <= bottom - top)
		val r = TileLayout.rects(0, 0, g.tileW, th, tiles[0])
		val bar = r.bar!!
		val fit = MenuTheme.fitBarTextRect(bar.x, bar.y, bar.w, bar.h, mc("64.4% · 134/208"))
		assertTrue(fit.x >= bar.x && fit.y >= bar.y && fit.x + fit.w <= bar.x + bar.w && fit.y + fit.h <= bar.y + bar.h)
		assertEquals(1f, fit.scale)
	}

	@Test fun fitBarTextStaysInsideTheBar() {
		for ((bw, bh, tw) in listOf(Triple(140, 10, 90), Triple(60, 10, 120), Triple(80, 8, 40), Triple(300, 12, 100), Triple(40, 9, 39))) {
			val r = MenuTheme.fitBarTextRect(5, 7, bw, bh, tw)
			assertTrue(r.x >= 5 && r.y >= 7 && r.x + r.w <= 5 + bw + 0.001f && r.y + r.h <= 7 + bh + 0.001f, "$bw x $bh / $tw")
			assertEquals(minOf(1f, bw.toFloat() / tw, bh / 9f), r.scale, 0.0001f)
		}

		val r = MenuTheme.fitBarTextRect(10, 20, 100, 10, 60)
		assertEquals(Triple(1f, 30f, 21f), Triple(r.scale, r.x, r.y))
	}

	@Test fun everyProgressBarTileStacksWithoutOverlap() {
		data class Kind(val name: String, val tw: Int, val th: Int, val hasSub: Boolean, val rects: (Int, Int, Int, Int) -> MenuTheme.BarTile.Rects)
		val full = MenuTheme.BarTile.FULL
		fun skill(sub: Boolean) = { tx: Int, ty: Int, tw: Int, th: Int -> MenuTheme.BarTile.rects(tx, ty, tw, th, tx + 22, sub, true) }
		fun coll(sub: Boolean) = { tx: Int, ty: Int, tw: Int, th: Int -> TileLayout.rects(tx, ty, tw, th, TileSpec(0, "t", if (sub) "s" else null, 0.3, 0)) }
		val kinds = listOf(
			Kind("skills", 110, maxOf(32, full), true, skill(true)),
			Kind("dungeoneering", 146, maxOf(32, full), true, skill(true)),
			Kind("attributes", 146, maxOf(32, full), true, skill(true)),
			Kind("levelling track", 110, maxOf(32, full), true, skill(true)),
			Kind("leveling rewards", 220, 40, true, skill(true)),
			Kind("rewards", 146, maxOf(32, full), true, skill(true)),
			Kind("xp guide tasks", 146, maxOf(30, full), true, skill(true)),
			Kind("collections", 124, TileLayout.tileHeight(30, listOf(TileSpec(0, "t", "s", 0.3, 0))), true, coll(true)),
			Kind("bestiary", 124, TileLayout.tileHeight(30, listOf(TileSpec(0, "t", "s", 0.3, 0))), true, coll(true)),
			Kind("recipe book", 123, TileLayout.tileHeight(28, listOf(TileSpec(0, "t", null, 0.3, 0))), false, coll(false)),
		)
		for (k in kinds) {
			val tile = MenuBounds(50, 60, k.tw, k.th)
			val r = k.rects(tile.x, tile.y, tile.w, tile.h)
			assertNotNull(r.bar, k.name)
			val bar = r.bar!!
			val parts = listOfNotNull(r.title, r.sub, bar)
			if (k.hasSub) assertNotNull(r.sub, "${k.name}: sub shown")
			for (p in parts) assertTrue(p.inside(tile), "${k.name}: $p inside $tile")
			for (a in parts) for (b in parts) if (a !== b) assertFalse(a.overlaps(b), "${k.name}: $a overlaps $b")
			val fit = MenuTheme.fitBarTextRect(bar.x, bar.y, bar.w, bar.h, mc("6308/19.82k (32%)"))
			assertTrue(fit.x >= bar.x && fit.y >= bar.y && fit.x + fit.w <= bar.x + bar.w + 0.001f && fit.y + fit.h <= bar.y + bar.h + 0.001f, k.name)
		}
	}

	@Test fun waysToLevelUpBarTextAtScaleOne() {
		val w = 460; val cols = 3; val gap = 3
		val tw = (w - 16 - gap * (cols - 1)) / cols
		val th = maxOf(30, MenuTheme.BarTile.FULL)
		assertTrue(th in 34..36, "grown ~17 %")
		val r = MenuTheme.BarTile.rects(0, 0, tw, th, 22, true, true)
		val bar = r.bar!!
		assertTrue(r.sub!!.y + r.sub!!.h <= bar.y, "'+XP · tasks' line above the bar")
		for (t in listOf("6308/19.82k (32%)", "1054/2760 (38.2%)", "2281/12.32k (18.5%)", "1354/11.73k (11.5%)"))
			assertEquals(1f, MenuTheme.fitBarTextRect(bar.x, bar.y, bar.w, bar.h, mc(t)).scale, t)
	}
}
