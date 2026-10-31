// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.h6.HubLayout
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView

class ScrollClipTest {
	@Test fun floorClampMaths() {
		assertEquals(200, ScrollClip.maxScroll(500, 300), "last item's bottom stops at the floor")
		assertEquals(0, ScrollClip.maxScroll(250, 300), "content shorter than the view: no scroll")
		assertEquals(0, ScrollClip.clamp(-5, 500, 300))
		assertEquals(200, ScrollClip.clamp(999, 500, 300), "no overscroll")
		assertEquals(12, ScrollClip.scrolled(0, -1.0, 500, 300))
		assertEquals(200, ScrollClip.scrolled(195, -1.0, 500, 300))
		assertEquals(0, ScrollClip.scrolled(5, 1.0, 500, 300))

		assertEquals(ScrollClip.maxScroll(431, 270), HubLayout.maxScroll(431, 270))
	}

	@Test fun scrollViewStopsWithTheLastItemFullyVisible() {
		val v = ScrollView()
		v.layout(MenuBounds(10, 40, 200, 100), 260)
		repeat(100) { v.scroll(-1.0) }
		assertEquals(160, v.offset)
		assertEquals(v.floor, v.contentBottom - v.offset, "last item bottom exactly at the floor")
		v.layout(MenuBounds(10, 40, 200, 100), 120)
		assertEquals(20, v.offset)
		val short = ScrollView(); short.layout(MenuBounds(0, 0, 100, 100), 50)
		assertFalse(short.scroll(-1.0)); assertEquals(0, short.offset)
	}

	@Test fun hitsOutsideTheViewAreRejected() {
		val view = MenuBounds(0, 40, 300, 100)
		val tile = MenuBounds(10, 120, 50, 35)
		assertTrue(ScrollClip.hit(20, 130, tile, view), "visible part")
		assertFalse(ScrollClip.hit(20, 145, tile, view), "clipped-away part (over the bottom bar)")
		assertFalse(ScrollClip.hit(20, 139 + 1, tile, view))
		val above = MenuBounds(10, 20, 50, 35)
		assertFalse(ScrollClip.hit(20, 30, above, view), "scrolled up under the header")
		assertTrue(ScrollClip.hit(20, 45, above, view))
		assertEquals(MenuBounds(10, 120, 50, 20), ScrollClip.clip(tile, view), "click area = visible part only")
		assertNull(ScrollClip.clip(MenuBounds(10, 150, 50, 20), view), "fully hidden: no area")
		assertEquals(10 to 10, ScrollClip.visibleSpan(0, 5, 10, 50).let { it.first to it.second })
	}

	@Test fun fitScaledScalesBeforeCutting() {
		val m: (String) -> Int = { it.length * 6 }
		assertEquals(MenuTheme.Fitted("Starter", 1f), MenuTheme.fitScaled("Starter", 56, m))
		val f = MenuTheme.fitScaled("Intermediate", 56, m)
		assertEquals("Intermediate", f.text); assertTrue(f.scale < 1f && f.scale >= MenuTheme.MIN_TEXT_SCALE)
		assertTrue(m(f.text) * f.scale <= 56f)
		val long = MenuTheme.fitScaled("Obtain all the Rift Timecharms and more words", 120, m)
		assertEquals(MenuTheme.MIN_TEXT_SCALE, long.scale); assertTrue(long.text.endsWith("…"))
		assertTrue(m(long.text) * long.scale <= 120f + 0.01f)
		assertNotNull(long)
	}
}
