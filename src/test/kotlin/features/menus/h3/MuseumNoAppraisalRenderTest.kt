// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumCategory
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumSearchIcon
import dev.ashtonite365.apex.features.menus.museum.MuseumTabs

class MuseumNoAppraisalRenderTest {
	private val museum = RealDumpFixtures.load("museum-main")
	private fun appraisal(c: MuseumCategory) = c.slot == 51 || c.name.contains("Appraisal", ignoreCase = true)

	@Test
	fun appraisalSlotNeverRenderedButHeaderValueParses() {
		val raw = museum.slots.single { it.slot == 51 }
		assertTrue(raw.cleanName.startsWith("Museum Appraisal Service"), raw.cleanName)
		val m = MuseumParser.parse(museum.title, museum.slots)!!
		assertFalse(m.categories.any(::appraisal), "categories/extras: ${m.categories.map { it.name }}")
		val tabs = MuseumTabs.of(m.categories)
		assertFalse(tabs.main.any(::appraisal)); assertFalse(tabs.more.any(::appraisal))
		assertFalse(tabs.special?.let(::appraisal) ?: false)
		val (search, bottom) = MuseumSearchIcon.split(tabs.more)
		assertFalse(search?.let(::appraisal) ?: false); assertFalse(bottom.any(::appraisal))

		val st = m.stats; assertNotNull(st)
		assertTrue(st!!.lines.contains("Value: 134,101,143 Coins"), "${st.lines}")
		assertTrue(st.lines.contains("Rank #135,875"), "${st.lines}")
	}

	@Test
	fun staleRememberedAppraisalCategoryIsFilteredToo() {
		val tabs = MuseumTabs.of(listOf(MuseumCategory("Special Items", 32, null, null), MuseumCategory("Museum Milestones", 48, null, null),
			MuseumCategory("Museum Appraisal Service", 51, null, null)))
		assertFalse(tabs.more.any(::appraisal))
	}
}

class MuseumAppraisalBoxLayoutTest {
	private fun overlap(a: dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.Rect, b: dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.Rect) =
		a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

	@Test
	fun oneBoxUnderVanillaNoOverlap() {
		val museum = RealDumpFixtures.load("museum-main")
		val tabs = MuseumTabs.of(MuseumParser.parse(museum.title, museum.slots)!!.categories)
		for (w in listOf(248, 328, 480)) {
			val x = 10; val y = 20; val h = 240; val vw = 52
			val vanilla = dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.Rect(x + w - vw - 4, y + 3, vw, 14)
			val hdr = MuseumSearchIcon.header(vanilla.x, y)
			val back = dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.Rect(hdr.backX, y + 3, 44, 14)
			for (labelW in listOf(6 * 17, 6 * 17 + 40)) {
				val box = dev.ashtonite365.apex.features.menus.museum.MuseumAppraisalBox.rect(vanilla.x, y, vw, labelW)
				assertTrue(box.y >= vanilla.y + vanilla.h && box.x + box.w == vanilla.x + vanilla.w, "under Vanilla, right-aligned")
				assertTrue(box.x >= x && box.y + box.h <= y + dev.ashtonite365.apex.features.menus.museum.MuseumAppraisalBox.CONTENT_Y, "w=$w above tabs/header")
				for (o in listOf(vanilla, hdr.icon, back)) assertFalse(overlap(box, o), "w=$w $o")
				val bottom = dev.ashtonite365.apex.features.menus.museum.MuseumBottomRow.layout(x + 6, y + h - 6, w - 12,
					MuseumSearchIcon.split(tabs.more).second.map { it.name })
				for (r in bottom.rects) assertFalse(overlap(box, r), "w=$w milestones/bottom $r")
			}
		}

		assertFalse((tabs.main + tabs.more).any { it.name.contains("Appraisal", true) })
	}
}
