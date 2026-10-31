// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.Rect
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumSearchIcon
import dev.ashtonite365.apex.features.menus.museum.MuseumTabs

class MuseumSearchIconTest {
	private val museum = RealDumpFixtures.load("museum-main")
	private fun overlap(a: Rect, b: Rect) = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

	@Test
	fun searchLeavesBottomRowAndKeepsItsSlot() {
		val tabs = MuseumTabs.of(MuseumParser.parse(museum.title, museum.slots)!!.categories)
		val (search, rest) = MuseumSearchIcon.split(tabs.more)
		assertEquals("Museum Search", search!!.name)
		assertEquals(tabs.more.single { it.name == "Museum Search" }.slot, search.slot)
		assertFalse(rest.any { it.name == "Museum Search" })
		assertEquals(tabs.more.size - 1, rest.size)
		assertEquals("Museum Search", MuseumSearchIcon.TOOLTIP)
	}

	@Test
	fun iconInHeaderBoundsNoOverlap() {
		for (w in listOf(176, 248, 328, 480)) {
			val x = 10; val y = 20; val vw = 6 * 7 + 10
			val vanilla = Rect(x + w - vw - 4, y + 3, vw, 14)
			val h = MuseumSearchIcon.header(vanilla.x, y)
			val back = Rect(h.backX, y + 3, 44, 14)
			val icon = h.icon
			assertTrue(icon.x >= x && icon.x + icon.w <= x + w, "w=$w")
			assertTrue(icon.y >= y && icon.y + icon.h <= y + 21, "w=$w: above the header strip/tabs at y+21")
			assertFalse(overlap(icon, vanilla), "w=$w"); assertFalse(overlap(icon, back), "w=$w")
			assertTrue(icon.x + icon.w <= vanilla.x, "left of Vanilla")
		}
	}

	@Test
	fun titleNeverReachesHeaderControls() {
		for (w in listOf(176, 248, 328)) {
			val x = 10; val y = 20; val pad = 6; val vw = 52; val vx = x + w - vw - 4
			val h = MuseumSearchIcon.header(vx, y)
			val withBoth = MuseumSearchIcon.titleMaxW(x, pad, vx, y, hasSearch = true, hasBack = true, oldBackX = x + w - vw - 52)
			assertTrue(x + pad + withBoth < h.backX, "w=$w")
			val searchOnly = MuseumSearchIcon.titleMaxW(x, pad, vx, y, hasSearch = true, hasBack = false, oldBackX = 0)
			assertTrue(x + pad + searchOnly < h.icon.x, "w=$w")
			val none = MuseumSearchIcon.titleMaxW(x, pad, vx, y, hasSearch = false, hasBack = false, oldBackX = 0)
			assertTrue(x + pad + none < vx && none >= 0, "w=$w")
		}
		assertEquals(0, MuseumSearchIcon.titleMaxW(0, 6, 20, 0, hasSearch = true, hasBack = true, oldBackX = 0))
	}
}
