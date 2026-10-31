// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.render.TooltipDeferral

class TooltipTopLayerTest {
	private class G(val name: String)

	@Test fun tooltipDrawsAfterEveryOverlay() {
		val d = TooltipDeferral<G>(); val g = G("gui"); val order = mutableListOf<String>()
		d.beginFrame()
		order += "screen"
		if (!d.defer(g, 10, 20, 0.5f)) order += "tooltip(early)"
		order += "container-value"; order += "side-panel"; order += "apex-overlay"
		d.flush { order += "tooltip@${it.mouseX},${it.mouseY}" }
		order += "toasts"
		assertEquals(listOf("screen", "container-value", "side-panel", "apex-overlay", "tooltip@10,20", "toasts"), order)
	}

	@Test fun outsideAFrameNothingIsDeferred() {
		val d = TooltipDeferral<G>()
		assertFalse(d.defer(G("x"), 0, 0, 0f))
		var ran = 0; d.flush { ran++ }; assertEquals(0, ran)
	}

	@Test fun oncePerGraphicsLastCallWinsAndFlushIsIdempotent() {
		val d = TooltipDeferral<G>(); val a = G("a"); val b = G("b")
		d.beginFrame()
		assertTrue(d.defer(a, 1, 1, 0f)); assertTrue(d.defer(b, 2, 2, 0f)); assertTrue(d.defer(a, 3, 3, 0f))
		val ran = mutableListOf<String>()
		d.flush { ran += "${it.graphics.name}${it.mouseX}" }
		d.flush { ran += "again" }
		assertEquals(listOf("a3", "b2"), ran)
		assertFalse(d.inFrame)
		assertFalse(d.defer(a, 0, 0, 0f))
	}

	@Test fun newFrameDropsStalePending() {
		val d = TooltipDeferral<G>(); val g = G("g")
		d.beginFrame(); d.defer(g, 1, 1, 0f)
		d.beginFrame()
		var ran = 0; d.flush { ran++ }; assertEquals(0, ran)
	}
}
