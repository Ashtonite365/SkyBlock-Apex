// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.hud

import kotlin.math.min

object HudTextScale {

	data class Fit(val x: Float, val y: Float, val scale: Float, val localW: Float, val localH: Float) {
		val scaleX: Float get() = scale
		val scaleY: Float get() = scale
	}

	fun uniform(sx: Float, sy: Float): Float {
		val s = min(sx, sy)
		return if (s > 0f && s.isFinite()) s else 1f
	}

	fun fit(x: Int, y: Int, boxW: Float, boxH: Float, sx: Float, sy: Float, contentW: Float, contentH: Float): Fit {
		val s = uniform(sx, sy)
		val cw = contentW * s
		val ch = contentH * s
		val stretched = sx != sy
		val ox = if (stretched && cw > boxW) (boxW - cw) / 2f else 0f
		val oy = if (stretched && ch > boxH) (boxH - ch) / 2f else 0f
		return Fit(x + ox, y + oy, s, boxW / s, boxH / s)
	}

	fun fitElement(x: Int, y: Int, uw: Int, uh: Int, sx: Float, sy: Float, contentW: Float = uw.toFloat(), contentH: Float = uh.toFloat()): Fit =
		fit(x, y, uw * sx, uh * sy, sx, sy, contentW, contentH)

	fun barLabel(barW: Float, barH: Float, textW: Int, max: Float = 2.5f): Float {
		val base = ((barH - 2f).coerceAtLeast(5f) / 8f).coerceAtMost(max)
		val tw = textW * base
		return if (tw > barW - 2f && tw > 0f) base * (barW - 2f).coerceAtLeast(1f) / tw else base
	}
}
