// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.hud.HudContentTransform

class HudContentTransformTest {
	private val t = HudContentTransform(40, 30, 2f, 0.5f)

	@Test
	fun contentFillsTheStretchedBox() {

		assertEquals(240f, t.screenX(100f)); assertEquals(40f, t.screenY(20f))
		assertEquals(40f, t.screenX(0f)); assertEquals(30f, t.screenY(0f))
	}

	@Test
	fun mouseMapsBackThroughTheSameAxes() {
		assertEquals(50f, t.localX(140f)); assertEquals(10f, t.localY(35f))
		for (lx in listOf(0f, 13.5f, 99f)) for (ly in listOf(0f, 7.25f, 19f)) {
			assertEquals(lx, t.localX(t.screenX(lx)), 1e-4f); assertEquals(ly, t.localY(t.screenY(ly)), 1e-4f)
		}

		assertEquals(100f, HudContentTransform(40, 30, 1f, 1f).localX(140f))
	}
}
