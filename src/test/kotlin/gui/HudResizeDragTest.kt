// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry.Grip as Handle

class HudResizeDragTest {
	private val sw = 480; private val sh = 270
	private data class Panel(val uw: Int, val uh: Int, val x: Int, val y: Int, val scale: Float)
	private val panels = listOf(
		Panel(100, 20, 20, 20, 1f),
		Panel(77, 33, 200, 10, 1.3f),
		Panel(151, 9, 300, 120, 0.7f),
		Panel(63, 41, 205, 118, 1.1f),
		Panel(90, 15, 190, 225, 1f),
		Panel(45, 120, 400, 140, 0.9f),
	)

	@Test
	fun monotonicDragNeverStepsBackAndPinsTheOppositeEdge() {
		for (p in panels) for (h in Handle.entries) for (grow in listOf(1, -1)) {
			val pos = HudPosition(p.x, p.y, p.scale)
			val w0i = (p.uw * p.scale).toInt(); val h0i = (p.uh * p.scale).toInt()
			pos.place(p.x, p.y, w0i, h0i, sw, sh)
			val anchor = pos.anchor
			val start = pos.resolve(w0i, h0i, sw, sh)
			val right0 = start.x + w0i; val bottom0 = start.y + h0i
			var lastW = w0i; var lastH = h0i
			for (f in 1..120) {
				val d = grow * f * 0.37
				val r = HudGeometry.resize(h, start.x, start.y, p.uw, p.uh, p.scale, p.scale, h.pullX * d, h.pullY * d, false, sw, sh)
				pos.dragTo(r.scaleX, r.scaleY, r.x, r.y, p.uw, p.uh, sw, sh)
				val w = (p.uw * pos.effectiveScaleX).toInt(); val hh = (p.uh * pos.effectiveScaleY).toInt()
				val at = pos.resolve(w, hh, sw, sh)
				val tag = "$p $h grow=$grow frame=$f"
				assertEquals(anchor, pos.anchor, "anchor re-picked mid-drag: $tag")
				assertTrue(if (grow > 0) w >= lastW && hh >= lastH else w <= lastW && hh <= lastH, "size stepped back: $tag")
				if (h.pullX == 0) assertEquals(w0i, w, tag); if (h.pullY == 0) assertEquals(h0i, hh, tag)

				if (h.pullX < 0) assertEquals(right0, at.x + w, "right edge moved: $tag") else assertEquals(start.x, at.x, "left edge moved: $tag")
				if (h.pullY < 0) assertEquals(bottom0, at.y + hh, "bottom edge moved: $tag") else assertEquals(start.y, at.y, "top edge moved: $tag")
				if (h.isCorner) assertEquals(pos.effectiveScaleX, pos.effectiveScaleY, 1e-4f, "corner kept aspect: $tag")
				lastW = w; lastH = hh
			}
		}
	}

	@Test
	fun centreAnchorRoundTripIsExactForOddWidths() {
		val pos = HudPosition(0, 0, 1f, HudAnchor.CENTRE)
		for (w in 60..70) for (x in 200..205) {
			pos.dragTo(1f, 1f, x, 100, w, 41, sw, sh)
			assertEquals(x, pos.resolve(w, 41, sw, sh).x, "w=$w x=$x")
		}
	}
}
