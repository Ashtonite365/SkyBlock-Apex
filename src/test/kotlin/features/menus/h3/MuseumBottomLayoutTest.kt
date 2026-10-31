// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow

class MuseumBottomLayoutTest {
	private val labels = listOf("Museum Milestones · Milestone 3/40 · next 25%", "Museum Search", "Museum Appraisal Services")
	private val width = { s: String -> s.length * 6 }

	@Test
	fun milestonesFullWidthAcrossBottomNoOverflow() {
		for (w in listOf(176, 248, 328, 480, 900)) {
			val x = 6; val bottom = 260; val inner = w - 12
			val l = MuseumBottomRow.layout(x, bottom, inner, labels)
			val ms = l.rects[0]
			assertEquals(MuseumBottomRow.Rect(x, bottom - 16, inner, 16), ms, "w=$w")
			for (r in l.rects) {
				assertTrue(r.x >= x && r.x + r.w <= x + inner, "w=$w $r")
				assertTrue(r.y >= bottom - l.height && r.y + r.h <= bottom, "w=$w $r")
				assertTrue(r.w > 0, "w=$w $r")
			}

			for (r in l.rects.drop(1)) assertTrue(r.y + r.h <= ms.y, "w=$w $r")
			val sc = MuseumBottomRow.scaleToFit(labels, l.rects, width)
			assertTrue(MuseumBottomRow.fits(labels, l.rects, width, sc), "w=$w scale=$sc")
		}
	}

	@Test
	fun milestonesAloneAndNoMilestones() {
		val solo = MuseumBottomRow.layout(6, 200, 300, listOf("Museum Milestones"))
		assertEquals(listOf(MuseumBottomRow.Rect(6, 184, 300, 16)), solo.rects); assertEquals(16, solo.height)
		val none = MuseumBottomRow.layout(6, 200, 300, listOf("Museum Search"))
		assertEquals(16, none.height); assertEquals(300, none.rects.single().w)
	}
}
