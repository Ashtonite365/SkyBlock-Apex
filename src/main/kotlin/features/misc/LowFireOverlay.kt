/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): geometry of the "Low on Fire" first-person fire overlay (MiscRender
 * fire-overlay = low-on-fire). Idea from the "Low On Fire" resource pack by Haikis (idea only, no assets or code
 * used). Original implementation.
 */
package dev.ashtonite365.apex.features.misc

object LowFireOverlay {

	const val VIEW_SHIFT = -0.3F
	private val TAN_HALF_FOV = Math.tan(Math.toRadians(35.0))

	@JvmField val SCREEN_HALF = (0.5 * TAN_HALF_FOV).toFloat()

	@JvmField val FAR_DEPTH = (0.5 + 0.5 * Math.sin(Math.toRadians(10.0))).toFloat()

	const val TOP_SCREEN_FRACTION = 0.2F

	const val TRIM_ROWS = 1

	@JvmField val TOP_Y = -SCREEN_HALF + 2 * SCREEN_HALF * TOP_SCREEN_FRACTION - VIEW_SHIFT

	@JvmField val BOTTOM_Y = (-TAN_HALF_FOV * FAR_DEPTH).toFloat() - VIEW_SHIFT

	data class Quad(val yBottom: Float, val yTop: Float, val vBottom: Float, val vTop: Float)

	fun quad(
		v0: Float, v1: Float, frameHeightPx: Int,
		trimRows: Int = TRIM_ROWS, bottomY: Float = BOTTOM_Y, topY: Float = TOP_Y,
	): Quad {
		val trim = (trimRows.toFloat() / frameHeightPx.coerceAtLeast(1)).coerceIn(0F, 1F)
		return Quad(yBottom = bottomY, yTop = topY, vBottom = v1, vTop = v0 + (v1 - v0) * trim)
	}
}
