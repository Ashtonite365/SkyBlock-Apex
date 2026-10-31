// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry.Grip as Handle

class HudResizeStretchedTest {
	private val sw = 480; private val sh = 270

	@Test
	fun stretchedAndFreeDragsPinTheOppositeEdgeEveryFrame() {
		val starts = listOf(Triple(1.2f, 0.8f, 20 to 20), Triple(0.9f, 1.4f, 300 to 120), Triple(1.0f, 1.0001f, 205 to 118),
			Triple(1.31f, 1.3f, 190 to 225), Triple(0.75f, 2.0f, 400 to 40))
		for ((sx0, sy0, xy) in starts) for (uw in listOf(77, 90, 151)) for (uh in listOf(9, 33)) for (h in Handle.entries)
			for (free in listOf(false, true)) for (grow in listOf(1, -1)) {
				val pos = HudPosition(xy.first, xy.second, 1f); pos.setScales(sx0, sy0)
				val sx0e = pos.effectiveScaleX; val sy0e = pos.effectiveScaleY
				val w0 = (uw * sx0e).toInt(); val h0 = (uh * sy0e).toInt()
				pos.place(xy.first, xy.second, w0, h0, sw, sh)
				val start = pos.resolve(w0, h0, sw, sh)
				for (f in 1..80) {
					val d = grow * f * 0.41
					val r = HudGeometry.resize(h, start.x, start.y, uw, uh, sx0e, sy0e, h.pullX * d, h.pullY * d, free, sw, sh)
					pos.dragTo(r.scaleX, r.scaleY, r.x, r.y, uw, uh, sw, sh)
					val w = (uw * pos.effectiveScaleX).toInt(); val hh = (uh * pos.effectiveScaleY).toInt()
					val at = pos.resolve(w, hh, sw, sh)
					val tag = "s=$sx0/$sy0 uw=$uw uh=$uh $h free=$free grow=$grow f=$f"

					assertEquals((uw * r.scaleX).toInt(), w, "width differs from the resize maths: $tag")
					assertEquals((uh * r.scaleY).toInt(), hh, "height differs from the resize maths: $tag")
					if (h.pullX < 0) assertEquals(start.x + w0, at.x + w, "right edge moved: $tag") else assertEquals(start.x, at.x, "left edge moved: $tag")
					if (h.pullY < 0) assertEquals(start.y + h0, at.y + hh, "bottom edge moved: $tag") else assertEquals(start.y, at.y, "top edge moved: $tag")
				}
			}
	}
}
