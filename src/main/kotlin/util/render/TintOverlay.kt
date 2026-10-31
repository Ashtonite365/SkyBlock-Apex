package dev.ashtonite365.apex.util.render

import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.ARGB
import dev.ashtonite365.apex.util.Faults

class TintOverlay : OverlayTexture() {
	companion object {
		const val SIDE = 16
		private const val HURT_ROWS = 8
		private val HURT_RED = 0xB2FF0000.toInt()
	}

	private var currentTint: Int? = null

	fun tinted(color: Int): TintOverlay {
		val pixels = Faults.presentOr(texture.pixels, "Disposed TintedOverlayTexture written to") { return this }
		if (currentTint == color) return this
		currentTint = color

		for (row in 0..<SIDE) for (column in 0..<SIDE) {
			val argb = if (row < HURT_ROWS) HURT_RED else ARGB.color(fadeAlpha(column), color)
			pixels.setPixel(column, row, argb)
		}
		texture.upload()
		return this
	}

	private fun fadeAlpha(column: Int): Int = ((1F - column / 15F * 0.75F) * 255F).toInt()
}
