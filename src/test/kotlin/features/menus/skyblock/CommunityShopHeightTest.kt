// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.skyblock.CommunityMenu

class CommunityShopHeightTest {

	private val screens = listOf(960 to 540, 640 to 360, 480 to 270, 1280 to 720, 853 to 480, 683 to 384, 960 to 540)

	@Test
	fun clampedToScreenFooterInside() {
		val (dw, dh) = CommunityMenu.defaultSize
		assertEquals(480, dw)
		assertTrue(dh > 260, "taller than before")
		for ((sw, sh) in screens) {
			val b = MenuBounds.centred(sw, sh, dw, dh)
			assertEquals(minOf(dh, sh - 12), b.h, "clamped to $sw x $sh")
			assertTrue(b.y >= 0 && b.y + b.h <= sh && b.x >= 0 && b.x + b.w <= sw, "on screen at $sw x $sh: $b")

			val footerTop = b.y + b.h - 38
			assertTrue(footerTop > b.y + 99 && b.y + b.h <= sh, "footer visible at $sw x $sh")
		}

		assertEquals(dh, MenuBounds.centred(1920, 1440, dw, dh).h)
	}
}
