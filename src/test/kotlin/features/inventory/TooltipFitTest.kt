// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.TooltipFit

class TooltipFitTest {
	@Test fun smallTooltipNotScaled() = assertEquals(1f, TooltipFit.scale(150, 100, 480, 270))

	@Test fun tallTooltipScaledToFit() {
		val s = TooltipFit.scale(180, 420, 480, 270)
		assertTrue(s < 1f)
		assertTrue(420 * s <= 270 - 2 * (TooltipFit.MARGIN + TooltipFit.FRAME) + 0.01)
	}

	@Test fun wideTooltipScaledToFit() = assertTrue(TooltipFit.scale(900, 50, 480, 270) * 900 <= 480)

	@Test fun bottomFlipsUpAndClamps() {
		val (_, y) = TooltipFit.position(100, 250, 150, 200, 480, 270)
		assertEquals(270 - 200 - TooltipFit.FRAME - TooltipFit.MARGIN, y)
	}

	@Test fun neverAboveTop() {
		val (_, y) = TooltipFit.position(100, 2, 150, 50, 480, 270)
		assertEquals(TooltipFit.MARGIN + TooltipFit.FRAME, y)
	}

	@Test fun rightEdgeFlipsLeft() {
		val (x, _) = TooltipFit.position(420, 100, 150, 50, 480, 270)
		assertEquals(420 - 24 - 150, x)
	}

	@Test fun neverOffLeft() {
		val (x, _) = TooltipFit.position(20, 100, 300, 50, 320, 270)
		assertTrue(x >= TooltipFit.MARGIN + TooltipFit.FRAME)
	}

	@Test fun scaledSpaceStaysInside() {
		val s = TooltipFit.scale(180, 420, 480, 270)
		val sw = (480 / s).toInt(); val sh = (270 / s).toInt()
		val (_, y) = TooltipFit.position((200 / s).toInt(), (260 / s).toInt(), 180, 420, sw, sh)
		assertTrue(y >= 0 && y + 420 <= sh)
	}
}
