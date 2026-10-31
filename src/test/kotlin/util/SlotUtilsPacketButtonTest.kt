package dev.ashtonite365.apex.test.util

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.mc.SlotUtils

class SlotUtilsPacketButtonTest {
	@Test
	fun packetButtons() {
		assertEquals(0, SlotUtils.PACKET_LEFT)
		assertEquals(1, SlotUtils.PACKET_RIGHT)
		assertEquals(2, SlotUtils.PACKET_MIDDLE)
	}

	@Test
	fun notInputCodes() {
		assertNotEquals(InputConstants.MOUSE_BUTTON_LEFT, SlotUtils.PACKET_LEFT)
		assertNotEquals(InputConstants.MOUSE_BUTTON_RIGHT, SlotUtils.PACKET_RIGHT)
	}
}
