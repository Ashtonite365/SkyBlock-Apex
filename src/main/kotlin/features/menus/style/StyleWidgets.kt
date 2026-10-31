// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

enum class Press { NORMAL, HOVER, PRESSED, ACTIVE, DISABLED }

object StyleWidgets {
	fun press(hover: Boolean, held: Boolean, active: Boolean = false, enabled: Boolean = true) = when {
		!enabled -> Press.DISABLED
		hover && held -> Press.PRESSED
		active -> Press.ACTIVE
		hover -> Press.HOVER
		else -> Press.NORMAL
	}

	data class ButtonLook(val fill: Int, val border: Int, val text: Int, val nudge: Int, val edge: Boolean)
	fun buttonLook(p: StylePalette, s: Press) = when (s) {
		Press.NORMAL -> ButtonLook(p.buttonFill, p.border, p.text, 0, true)
		Press.HOVER -> ButtonLook(p.buttonHover, p.borderHover, p.text, 0, true)
		Press.PRESSED -> ButtonLook(p.buttonPressed, p.accent, p.text, 1, false)
		Press.ACTIVE -> ButtonLook(p.buttonActive, p.accent, if (p.style == Style.SKYGLASS) p.textOnAccent else p.accent2, 0, true)
		Press.DISABLED -> ButtonLook(p.well, p.border, p.textDim, 0, false)
	}

	fun button(c: StyleCanvas, p: StylePalette, x: Int, y: Int, w: Int, h: Int, label: String, s: Press) {
		val l = buttonLook(p, s)
		val r = p.controlRadius
		if (l.edge) Shapes.round(c, x, y + 1, w, h, r, p.buttonEdge)
		Shapes.roundFrame(c, x, y + l.nudge, w, h - (if (l.edge) 1 else 0), r, l.fill, l.border, p.style == Style.SKYGLASS)
		if (s != Press.PRESSED && s != Press.DISABLED) c.fill(x + r + 1, y + 1 + l.nudge, x + w - r - 1, y + 2 + l.nudge, p.rimHighlight)
		c.text(label, x + (w - c.textWidth(label)) / 2, y + (h - 8) / 2 + l.nudge, l.text)
	}

	fun card(c: StyleCanvas, p: StylePalette, x: Int, y: Int, w: Int, h: Int, hot: Boolean = false, fill: Int = p.card, frosted: Boolean = true) {
		val r = p.cardRadius
		if (p.style == Style.SKYGLASS) {
			Shapes.round(c, x - 1, y + 1, w + 2, h + 2, r + 1, p.shadow)
			Shapes.round(c, x, y + 2, w, h + 1, r, p.shadow)

			if (p.blurWorld && frosted) c.frost(x, y, w, h, r)
			Shapes.roundFrame(c, x, y, w, h, r, fill, p.border, glass = true)
			c.fill(x + r, y, x + w - r, y + 1, p.rimHighlight)
		} else {
			Shapes.round(c, x, y + 1, w, h + 1, r, p.shadow)
			Shapes.roundFrame(c, x, y, w, h, r, fill, p.border)
			c.fill(x + r, y + 1, x + w - r, y + 3, if (hot) p.accent else p.border)
		}
	}

	fun toggle(c: StyleCanvas, p: StylePalette, x: Int, y: Int, t: Double) {
		val h = if (p.style == Style.SKYGLASS) 12 else 10
		val on = t >= 0.5
		val track = if (on) p.accent else p.well
		if (on && p.style == Style.SKYGLASS) Shapes.pill(c, x - 1, y - 1, 24, h + 2, p.glow)
		Shapes.pill(c, x, y, 22, h, track, if (on && p.style == Style.FORGE) p.accent2 else p.border)
		val k = h - 2
		val kx = x + 1 + ((22 - 2 - k) * t.coerceIn(0.0, 1.0)).toInt()
		Shapes.round(c, kx, y + 1, k, k, k / 2, if (on) (if (p.style == Style.FORGE) p.accent2 else 0xFFFFFFFF.toInt()) else p.textDim)
	}

	fun cell(c: StyleCanvas, p: StylePalette, x: Int, y: Int, size: Int, hover: Boolean) {
		val r = minOf(p.controlRadius + 1, size / 4)
		Shapes.roundFrame(c, x, y, size, size, r, if (hover) p.buttonHover else p.well, if (hover) p.borderHover else p.border, p.style == Style.SKYGLASS)
	}

	fun cover(c: StyleCanvas, kind: MenuAnim, cover: Int, x: Int, y: Int, w: Int, h: Int, amount: Double, seed: Long, elapsedMs: Long) {
		if (amount >= 1.0) return
		val a = amount.coerceIn(0.0, 1.0)
		when (kind) {
			MenuAnim.FADE -> c.fill(x, y, x + w, y + h, withAlpha(cover, 1 - a))
			MenuAnim.CIRCLE -> {
				val r = StyleAnim.circleRadius(w, h, a)
				val cx = x + w / 2; val cy = y + h / 2.0
				var row = y
				while (row < y + h) {
					val half = StyleAnim.circleHalfWidth(r, row + 1 - cy)
					val solid = cover or (0xFF shl 24)
					if (half <= 0) c.fill(x, row, x + w, row + 2, solid)
					else { c.fill(x, row, maxOf(x, cx - half), row + 2, solid); c.fill(minOf(x + w, cx + half), row, x + w, row + 2, solid) }
					row += 2
				}
			}
			MenuAnim.GLITCH -> {

				val solid = cover or (0xFF shl 24)
				val boxes = StyleAnim.glitchBoxes(seed, w, h)
				for (b in boxes) if (!StyleAnim.glitchState(b, a, elapsedMs).settled) c.fill(x + b.x, y + b.y, x + b.x + b.w, y + b.y + b.h, solid)
				for (b in boxes) {
					val g = StyleAnim.glitchState(b, a, elapsedMs)
					if (g.settled || !g.on) continue
					val gx0 = maxOf(x, x + g.x); val gy0 = maxOf(y, y + g.y); val gx1 = minOf(x + w, x + g.x + b.w); val gy1 = minOf(y + h, y + g.y + b.h)
					c.fill(gx0, gy0, gx1, gy1, withAlpha(0xFFFFFFFF.toInt(), 0.35))
				}
			}
		}
	}

	fun overlaySurface(c: StyleCanvas, p: StylePalette, x: Int, y: Int, w: Int, h: Int, r: Int) {
		c.layer()
		if (p.blurWorld) c.frost(x, y, w, h, r)
		Shapes.roundFrame(c, x, y, w, h, r, p.overlay, p.border, p.style == Style.SKYGLASS)
	}

	fun withAlpha(argb: Int, f: Double): Int {
		val base = (argb ushr 24).let { if (it == 0) 255 else it }
		return (argb and 0xFFFFFF) or ((base * f.coerceIn(0.0, 1.0)).toInt() shl 24)
	}
}
