// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.gui.config.styled.StyledSettingsLayout as L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsDockBoundsTest {
	@Test fun dockBoxIsInsideTheWindowAtSmallSizes() {
		for ((sw, sh) in listOf(320 to 180, 427 to 240, 480 to 270, 640 to 360, 960 to 540)) {
			val f = L.frame(sw, sh); val b = L.dockBox(f)
			assertTrue(b[0] >= f.x && b[0] + L.DOCK_WIDE <= f.x + f.w, "x at $sw x $sh")
			assertTrue(b[1] >= f.y && b[1] + b[2] <= f.y + f.h - 8, "dock ${b.toList()} in $f at $sw x $sh")
		}
	}

	@Test fun longListScrollsAndKeepsTheSelectedItemVisible() {
		val count = 13; val h = 200
		assertEquals(92, L.dockMaxScroll(count, h))
		val s = L.dockKeepVisible(0, 12, count, h)
		val top = 12 * L.DOCK_ITEM - s
		assertTrue(top >= 0 && top + 20 <= h - 2 * L.DOCK_PAD, "last item visible: top $top scroll $s")
		assertEquals(0, L.dockKeepVisible(s, 0, count, h))
		assertEquals(0, L.dockMaxScroll(5, 200))
	}
}
