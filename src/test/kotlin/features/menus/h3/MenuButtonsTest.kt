// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.features.menus.h3

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.MenuButtons

class MenuButtonsTest {
	@Test
	fun inputToPacket() {
		assertEquals(0, MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_LEFT))
		assertEquals(1, MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_RIGHT))
		assertNull(MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_MIDDLE))
	}
}
