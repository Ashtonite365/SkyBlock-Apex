// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Vector2ic

interface PlacedHud {
	val hudId: Identifier
	val label: Component

	val isEnabled: Boolean
	val unscaledWidth: Int
	val unscaledHeight: Int

	val topLeft: Vector2ic

	fun moveTo(x: Int, y: Int)

	val showsEditorLabel: Boolean get() = true
	val scaleX: Float get() = 1f
	val scaleY: Float get() = 1f

	val drawnWidth: Int get() = (unscaledWidth * scaleX).toInt()
	val drawnHeight: Int get() = (unscaledHeight * scaleY).toInt()

	fun applyTo(pose: Matrix3x2f) {
		val at = topLeft
		pose.translate(at.x().toFloat(), at.y().toFloat())
		if (this is ResizableHud) pose.scale(scaleX, scaleY)
	}
}

interface ResizableHud : PlacedHud {
	var uniformScale: Float
	override val scaleX: Float get() = uniformScale
	override val scaleY: Float get() = uniformScale

	fun setScales(sx: Float, sy: Float) { uniformScale = (sx + sy) / 2f }

	fun resizeFrame(sx: Float, sy: Float, x: Int, y: Int) { setScales(sx, sy); moveTo(x, y) }

	fun finishResize() {}
	fun resetSize() = setScales(1f, 1f)
}
