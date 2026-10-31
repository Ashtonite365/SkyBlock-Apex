// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.render

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.render.gui.pip.RenderLineRenderer

class LineScreenSizeTest {

	private fun legacy(w: Int, h: Int) = intArrayOf(w, h)

	@Test
	fun screenSizeMatchesKnownGoodForEveryGuiScale() {
		for (scale in 1..6) for ((w, h) in listOf(0 to 0, 6 to 6, 46 to 13, 400 to 3, 1 to 250))
			assertArrayEquals(legacy(w, h), RenderLineRenderer.lineScreenSize(w, h, scale), "box ${w}x$h at gui scale $scale")
	}
}
