package dev.ashtonite365.apex.test.features.menus

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.MenuButtons

class MouseVsPacketButtonTest {
	@Test
	fun mouseSpaceIsNotPacketSpace() {
		assertEquals(1, InputConstants.MOUSE_BUTTON_LEFT)
		assertEquals(3, InputConstants.MOUSE_BUTTON_RIGHT)
		assertTrue(MenuButtons.isLeft(InputConstants.MOUSE_BUTTON_LEFT))
		assertFalse(MenuButtons.isLeft(0))
		assertTrue(MenuButtons.isRight(InputConstants.MOUSE_BUTTON_RIGHT))
		assertFalse(MenuButtons.isRight(1))
		assertEquals(0, MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_LEFT))
		assertEquals(1, MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_RIGHT))
		assertNull(MenuButtons.packetButton(InputConstants.MOUSE_BUTTON_MIDDLE))
	}
}
