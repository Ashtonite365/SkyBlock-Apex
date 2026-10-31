// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.levelling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.levelling.XpGuideLayout
import dev.ashtonite365.apex.features.menus.levelling.XpGuideParser
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class XpGuideLayoutTest {
	private val pages = listOf("xpguide-starter-1046", "xpguide-amateur-1046", "xpguide-intermediate-p1-1046",
		"xpguide-starter-accessories-1046", "xpguide-starter-museum-p1-1046", "xpguide-starter-skills-1046")

	@Test fun everyPageScrollsAboveAFixedBottomBar() {
		for (n in pages) for ((w, h) in listOf(460 to 290, 400 to 220)) {
			val d = RealDumpFixtures.load(n)
			val m = XpGuideParser.parse(d.title, d.slots).also { assertNotNull(it, n) }!!
			val x = 100; val y = 50
			val extras = m.sort != null || m.extras.isNotEmpty()
			val fr = XpGuideLayout.frame(x, y, w, h, m.isStagePage, extras)
			val navTop = y + h - 18
			val floor = fr.view.y + fr.view.h
			assertTrue(floor + ScrollClip.BAR_GAP <= fr.barTop, "$n: small gap above the bar")
			assertTrue(fr.barTop <= navTop, "$n: bar row(s) above the panel bottom")
			if (extras) assertTrue(fr.barTop + 14 <= navTop, "$n: Sort/extras row doesn't overlap Back/Close")
			assertTrue(fr.view.y >= y + 22 + (if (m.isStagePage) 18 else 0), "$n: below header and stage tabs")
			val groups = listOf(m.open.size, m.done.size, m.locked.size)
			val v = ScrollView(); v.layout(fr.view, XpGuideLayout.contentHeight(m.header != null, groups))
			repeat(200) { v.scroll(-1.0) }
			val lastBottom = v.contentBottom - v.offset
			assertTrue(lastBottom <= floor, "$n: last tile fully visible at the floor")
			if (v.max > 0) assertEquals(floor, lastBottom, "$n: no overscroll past the last tile")
		}
	}

	@Test fun intermediateOverflowsAndClipsMidTile() {
		val d = RealDumpFixtures.load("xpguide-intermediate-p1-1046")
		val m = XpGuideParser.parse(d.title, d.slots)!!
		val fr = XpGuideLayout.frame(0, 0, 460, 290, m.isStagePage, m.sort != null || m.extras.isNotEmpty())
		val total = XpGuideLayout.contentHeight(m.header != null, listOf(m.open.size, m.done.size, m.locked.size))
		assertTrue(total > fr.view.h, "21 to-do tiles don't fit: the view scrolls")
	}

	@Test fun tilesFitTitleSubAndBarWithoutOverlap() {
		val r = MenuTheme.BarTile.rects(0, 0, XpGuideLayout.tileW(460), XpGuideLayout.TILE_H, 22, true, true)
		val sub = r.sub.also { assertNotNull(it, "sub") }!!; val bar = r.bar.also { assertNotNull(it, "bar") }!!
		assertTrue(r.title.y + r.title.h <= sub.y); assertTrue(sub.y + sub.h <= bar.y)
		assertTrue(bar.y + bar.h <= XpGuideLayout.TILE_H)
	}
}
