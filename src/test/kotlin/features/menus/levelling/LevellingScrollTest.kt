// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.levelling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.levelling.LevellingLayout
import dev.ashtonite365.apex.features.menus.levelling.LevellingMenu
import dev.ashtonite365.apex.features.menus.levelling.LevellingParser
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class LevellingScrollTest {

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
	private fun plain(l: List<String>) = l.joinToString(" ") { it.replace(Regex("§."), "") }

	@Test fun clipEndsAboveTheButtonRowAndScrolls() {
		val d = RealDumpFixtures.load("skyblock-levelling")
		val m = LevellingParser.parse(d.title, d.slots)!!
		for ((sw, sh) in listOf(480 to 270, 640 to 360)) {
			val (w, h) = LevellingMenu.defaultSize
			val b = MenuBounds.centred(sw, sh, w, h)
			val navTop = b.y + b.h - 18
			val view = LevellingLayout.view(b.x, b.y, b.w, navTop)
			assertEquals(navTop - ScrollClip.BAR_GAP, view.y + view.h, "clip line just above Back/Close")
			assertTrue(view.y >= b.y + LevellingLayout.HEADER)
			val plan = LevellingLayout.plan(m, b.x, b.w, ::mc, showRanking = true, showRewards = true)
			val sv = ScrollView(); sv.layout(view, plan.contentHeight)
			assertTrue(sv.max > 0, "content taller than the view on $sw x $sh -> scrolls")

			repeat(200) { sv.scroll(-1.0) }
			assertEquals(plan.contentHeight - view.h, sv.offset)

			val last = plan.tiles.maxBy { it.y }
			sv.reset(); sv.layout(view, plan.contentHeight)
			val atTop = MenuBounds(last.x, view.y + last.y - sv.offset, last.w, last.h)

			val cut = view.y + view.h
			assertTrue(atTop.y + atTop.h > cut, "last tile reaches below the clip line at the top of the scroll")
			ScrollClip.clip(atTop, view)?.let { assertTrue(it.y + it.h <= cut, "clipped part ends at the clip line") }
			assertFalse(ScrollClip.hit(atTop.x + 2, cut, atTop, view), "the part below the clip line is not clickable")
			assertFalse(ScrollClip.hit(atTop.x + 2, atTop.y + atTop.h - 1, atTop, view))
			repeat(200) { sv.scroll(-1.0) }
			val scrolled = MenuBounds(last.x, view.y + last.y - sv.offset, last.w, last.h)
			assertEquals(scrolled, ScrollClip.clip(scrolled, view), "fully visible at the bottom")
			assertTrue(ScrollClip.hit(scrolled.x + 2, scrolled.y + 2, scrolled, view))

			val kept = sv.offset; sv.layout(view, plan.contentHeight); assertEquals(kept, sv.offset)
			sv.layout(view, view.h - 10); assertEquals(0, sv.offset)
		}
	}

	@Test fun tileTextWrapsInFull() {
		for (fixture in listOf("skyblock-levelling", "skyblock-levelling-1045")) {
			val d = RealDumpFixtures.load(fixture)
			val m = LevellingParser.parse(d.title, d.slots)!!
			val b = MenuBounds.centred(480, 270, LevellingMenu.defaultSize.first, LevellingMenu.defaultSize.second)
			val plan = LevellingLayout.plan(m, b.x, b.w, ::mc, showRanking = true, showRewards = true)
			for (t in plan.tiles) {
				val textW = t.w - LevellingLayout.TEXT_X - 4
				assertTrue((t.title + t.sub).all { mc(it) <= textW || !it.contains(' ') }, "${t.title} wraps inside the tile")
				assertTrue((t.title + t.sub).none { it.endsWith("…") }, "never cut")
				assertTrue(LevellingLayout.tileH(t.title.size + t.sub.size, t.progress != null) <= t.h, "${t.title} tile tall enough")
			}

			val l125 = plan.tiles.first { plain(it.title) == "Level 125" }
			assertEquals(listOf("+1", "Strength,", "+5", "Health"), plain(l125.sub).replace(Regex("\\s*\\p{Co}"), "").split(" "))
			val chat = plan.tiles.first { plain(it.title).startsWith("SkyBlock Levels") }
			assertEquals("SkyBlock Levels in Chat", plain(chat.title))
			assertTrue(chat.title.size > 1, "wrapped over lines: ${chat.title}")

			for (a in plan.tiles) for (c in plan.tiles) if (a !== c) {
				val o = a.x < c.x + c.w && c.x < a.x + a.w && a.y < c.y + c.h && c.y < a.y + a.h
				assertFalse(o, "${a.title} overlaps ${c.title}")
			}
			assertTrue(plan.groups.zipWithNext().all { (a, c) -> a.y + a.h <= c.y }, "sections stacked")
			assertEquals(plan.groups.last().y + plan.groups.last().h + 4, plan.contentHeight)
		}
	}

	@Test fun wrapKeepsColour() {

		assertEquals(listOf("§7+1 Strength, +5", "§7Health"), LevellingLayout.wrap("§7+1 Strength, +5 Health", 80, ::mc))
	}
}
