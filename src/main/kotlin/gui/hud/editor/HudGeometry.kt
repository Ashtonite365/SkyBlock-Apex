// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

object HudGeometry {
	const val SMALLEST_SCALE = 0.25f
	const val LARGEST_SCALE = 6f

	const val SMALLEST_SIDE_PX = 6

	const val GRIP_PX = 4
	private const val GRIP_REACH = GRIP_PX / 2.0 + 1

	enum class Grip(val pullX: Int, val pullY: Int) {
		TOP_LEFT(-1, -1), TOP(0, -1), TOP_RIGHT(1, -1),
		LEFT(-1, 0), RIGHT(1, 0),
		BOTTOM_LEFT(-1, 1), BOTTOM(0, 1), BOTTOM_RIGHT(1, 1);

		val isCorner: Boolean get() = pullX != 0 && pullY != 0
		fun centreX(left: Double, width: Double): Double = left + width * (pullX + 1) / 2.0
		fun centreY(top: Double, height: Double): Double = top + height * (pullY + 1) / 2.0
	}

	data class Resized(val x: Int, val y: Int, val scaleX: Float, val scaleY: Float)

	fun gripAt(mouseX: Double, mouseY: Double, left: Double, top: Double, width: Double, height: Double): Grip? =
		Grip.entries.firstOrNull { g ->
			kotlin.math.abs(mouseX - g.centreX(left, width)) <= GRIP_REACH && kotlin.math.abs(mouseY - g.centreY(top, height)) <= GRIP_REACH
		}

	fun lowestScale(unscaled: Int): Float =
		if (unscaled <= 0) SMALLEST_SCALE else maxOf(SMALLEST_SCALE, SMALLEST_SIDE_PX.toFloat() / unscaled)

	fun highestScale(unscaled: Int, screen: Int): Float {
		val fitsScreen = if (unscaled > 0 && screen > 0) minOf(LARGEST_SCALE, screen.toFloat() / unscaled) else LARGEST_SCALE
		return maxOf(fitsScreen, lowestScale(unscaled))
	}

	fun limitScale(scale: Float, unscaled: Int, screen: Int): Float =
		maxOf(lowestScale(unscaled), minOf(highestScale(unscaled, screen), scale))

	private fun stretchAxis(startPx: Double, pulled: Double, unscaled: Int, screen: Int): Float =
		limitScale(((startPx + pulled) / unscaled).toFloat(), unscaled, screen)

	fun resize(grip: Grip, left: Int, top: Int, unscaledW: Int, unscaledH: Int, startSx: Float, startSy: Float,
	           moveX: Double, moveY: Double, independent: Boolean, screenW: Int, screenH: Int): Resized {
		val startW: Double = (unscaledW * startSx).toDouble()
		val startH: Double = (unscaledH * startSy).toDouble()
		var sx = startSx
		var sy = startSy
		if (grip.isCorner && !independent) {
			val growth = ((startW + grip.pullX * moveX) / startW + (startH + grip.pullY * moveY) / startH) / 2.0
			val floor: Double = maxOf(lowestScale(unscaledW) / startSx, lowestScale(unscaledH) / startSy).toDouble()
			val ceiling: Double = minOf(highestScale(unscaledW, screenW) / startSx, highestScale(unscaledH, screenH) / startSy).toDouble()
			val factor = maxOf(floor, minOf(ceiling, growth))
			sx = (startSx * factor).toFloat()
			sy = (startSy * factor).toFloat()
		} else {
			if (grip.pullX != 0) sx = stretchAxis(startW, grip.pullX * moveX, unscaledW, screenW)
			if (grip.pullY != 0) sy = stretchAxis(startH, grip.pullY * moveY, unscaledH, screenH)
		}

		val x = if (grip.pullX < 0) left + startW.toInt() - (unscaledW * sx).toInt() else left
		val y = if (grip.pullY < 0) top + startH.toInt() - (unscaledH * sy).toInt() else top
		return Resized(x, y, sx, sy)
	}
}
