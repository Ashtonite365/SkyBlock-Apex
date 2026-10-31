// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

object CloseGhost {
	fun draw(c: StyleCanvas, kind: MenuAnim, p: StylePalette, x: Int, y: Int, w: Int, h: Int, visible: Double, seed: Long, elapsedMs: Long) {
		val v = visible.coerceIn(0.0, 1.0)
		if (v <= 0.0) return
		when (kind) {
			MenuAnim.FADE -> Shapes.round(c, x, y, w, h, p.cardRadius, StyleWidgets.withAlpha(p.window, v))
			MenuAnim.CIRCLE -> {
				val r = StyleAnim.circleRadius(w, h, v)
				val cx = x + w / 2; val cy = y + h / 2.0
				var row = y
				while (row < y + h) {
					val half = minOf(w / 2, StyleAnim.circleHalfWidth(r, row + 1 - cy))
					if (half > 0) c.fill(cx - half, row, cx + half, row + 2, p.window or (0xFF shl 24))
					row += 2
				}
			}
			MenuAnim.GLITCH -> {

				for (b in StyleAnim.glitchBoxes(seed, w, h)) {
					val g = StyleAnim.glitchState(b, v, elapsedMs)
					if (!g.on) continue
					val col = if (g.settled) p.window or (0xFF shl 24) else StyleWidgets.withAlpha(p.accent, 0.6)
					c.fill(x + g.x, y + g.y, x + g.x + b.w, y + g.y + b.h, col)
				}
			}
		}
	}
}
