// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HudAnchor(val fx: Float, val fy: Float) {
	@SerialName("top-left") TOP_LEFT(0f, 0f),
	@SerialName("top") TOP(.5f, 0f),
	@SerialName("top-right") TOP_RIGHT(1f, 0f),
	@SerialName("left") LEFT(0f, .5f),
	@SerialName("centre") CENTRE(.5f, .5f),
	@SerialName("right") RIGHT(1f, .5f),
	@SerialName("bottom-left") BOTTOM_LEFT(0f, 1f),
	@SerialName("hotbar") HOTBAR(.5f, 1f),
	@SerialName("bottom-right") BOTTOM_RIGHT(1f, 1f),
	;

	fun screenX(screenW: Int): Float = fx * screenW
	fun screenY(screenH: Int): Float = if (this == HOTBAR) (screenH - HOTBAR_HEIGHT).toFloat() else fy * screenH

	companion object {

		const val HOTBAR_HEIGHT = 22

		fun nearest(x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int): HudAnchor {
			val cx = (x + w / 2f) / screenW.coerceAtLeast(1)
			val cy = (y + h / 2f) / screenH.coerceAtLeast(1)
			val col = if (cx < 1 / 3f) 0 else if (cx > 2 / 3f) 2 else 1
			val row = if (cy < 1 / 3f) 0 else if (cy > 2 / 3f) 2 else 1
			return entries[row * 3 + col]
		}

		fun offsetFor(anchor: HudAnchor, x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int): Pair<Int, Int> =
			Math.round(x + anchor.fx * w - anchor.screenX(screenW)) to Math.round(y + anchor.fy * h - anchor.screenY(screenH))

		fun absolute(anchor: HudAnchor, ox: Int, oy: Int, w: Int, h: Int, screenW: Int, screenH: Int): Pair<Int, Int> =
			Math.round(anchor.screenX(screenW) + ox - anchor.fx * w) to Math.round(anchor.screenY(screenH) + oy - anchor.fy * h)
	}
}
