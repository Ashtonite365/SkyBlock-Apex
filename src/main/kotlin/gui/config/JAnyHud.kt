// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Vector2i
import org.joml.Vector2ic
import kotlin.math.sqrt
import dev.ashtonite365.apex.gui.hud.HudTextScale
import dev.ashtonite365.apex.gui.hud.editor.ResizableHud

@Serializable
data class HudPosition(
	var x: Int,
	var y: Int,
	var scale: Float,
	var anchor: HudAnchor? = null,
	var scaleX: Float? = null,
	var scaleY: Float? = null,
) {
	val effectiveScaleX: Float get() = scaleX ?: scale
	val effectiveScaleY: Float get() = scaleY ?: scale

	fun setScales(sx: Float, sy: Float) {
		if (sx == sy) {
			scale = sx
			scaleX = null
			scaleY = null
		} else {
			scale = sqrt(sx * sy)
			scaleX = sx
			scaleY = sy
		}
	}

	fun setUniformScale(s: Float) {
		val sx = scaleX
		val sy = scaleY
		if (sx == null || sy == null || scale == 0f) {
			scale = s
			return
		}
		val factor = s / scale
		setScales(sx * factor, sy * factor)
		scale = s
	}

	fun resetScale() = setScales(1f, 1f)

	fun resolve(w: Int, h: Int, screenW: Int, screenH: Int): Vector2i {
		val at = anchor ?: return Vector2i(x, y)
		val (ax, ay) = HudAnchor.absolute(at, x, y, w, h, screenW, screenH)
		return Vector2i(ax, ay)
	}

	private fun storeAgainst(at: HudAnchor, absX: Int, absY: Int, w: Int, h: Int, screenW: Int, screenH: Int) {
		anchor = at
		val (ox, oy) = HudAnchor.offsetFor(at, absX, absY, w, h, screenW, screenH)
		x = ox
		y = oy
		val back = resolve(w, h, screenW, screenH)
		x += absX - back.x
		y += absY - back.y
	}

	fun place(absX: Int, absY: Int, w: Int, h: Int, screenW: Int, screenH: Int) =
		storeAgainst(HudAnchor.nearest(absX, absY, w, h, screenW, screenH), absX, absY, w, h, screenW, screenH)

	fun dragTo(sx: Float, sy: Float, absX: Int, absY: Int, uw: Int, uh: Int, screenW: Int, screenH: Int) {
		setScales(sx, sy)
		val at = anchor
		if (at == null) {
			x = absX
			y = absY
			return
		}
		storeAgainst(at, absX, absY, (uw * effectiveScaleX).toInt(), (uh * effectiveScaleY).toInt(), screenW, screenH)
	}

	fun migrate(w: Int, h: Int, screenW: Int, screenH: Int) {
		if (anchor != null) return
		place(x, y, w, h, screenW, screenH)
	}
}

data class HudMeta(
	val position: HudPosition,
	override val hudId: Identifier,
	override val label: Component,
	private val width: Int,
	private val height: Int,
) : ResizableHud {

	private val translationKey: String? get() = (label.contents as? TranslatableContents)?.key
	private val liveSize: Vector2i? get() = translationKey?.let { sizeOverrides[it] }?.invoke()

	override val unscaledWidth: Int get() = liveSize?.x ?: width
	override val unscaledHeight: Int get() = liveSize?.y ?: height

	companion object {

		@JvmStatic
		val sizeOverrides: MutableMap<String, () -> Vector2i> = mutableMapOf()

		@JvmStatic
		val hiddenEditorLabels: MutableSet<String> = mutableSetOf()
	}

	override val showsEditorLabel: Boolean get() = translationKey !in hiddenEditorLabels
	override val isEnabled: Boolean get() = HudEnabled.isEnabled(translationKey)

	private fun guiW(): Int = Minecraft.getInstance().window.guiScaledWidth
	private fun guiH(): Int = Minecraft.getInstance().window.guiScaledHeight

	override val topLeft: Vector2ic
		get() {
			val w = drawnWidth
			val h = drawnHeight
			position.migrate(w, h, guiW(), guiH())
			return position.resolve(w, h, guiW(), guiH())
		}

	override fun moveTo(x: Int, y: Int) = position.place(x, y, drawnWidth, drawnHeight, guiW(), guiH())

	override val scaleX: Float get() = position.effectiveScaleX
	override val scaleY: Float get() = position.effectiveScaleY

	override var uniformScale: Float
		get() = position.scale
		set(value) = keepingTopLeft { position.setUniformScale(value) }

	override fun setScales(sx: Float, sy: Float) = keepingTopLeft { position.setScales(sx, sy) }

	private fun keepingTopLeft(change: () -> Unit) {
		val at = topLeft
		val ax = at.x()
		val ay = at.y()
		change()
		moveTo(ax, ay)
	}

	override fun resizeFrame(sx: Float, sy: Float, x: Int, y: Int) =
		position.dragTo(sx, sy, x, y, unscaledWidth, unscaledHeight, guiW(), guiH())

	override fun finishResize() {
		val at = topLeft
		moveTo(at.x(), at.y())
	}

	val isStretched: Boolean get() = scaleX != scaleY

	fun textFit(contentW: Float = unscaledWidth.toFloat(), contentH: Float = unscaledHeight.toFloat()): HudTextScale.Fit {
		val at = topLeft
		return HudTextScale.fitElement(at.x(), at.y(), unscaledWidth, unscaledHeight, scaleX, scaleY, contentW, contentH)
	}

	fun applyTextTransformations(pose: Matrix3x2f, contentW: Float = unscaledWidth.toFloat(), contentH: Float = unscaledHeight.toFloat()): HudTextScale.Fit {
		val fit = textFit(contentW, contentH)
		pose.translate(fit.x, fit.y)
		pose.scale(fit.scale, fit.scale)
		return fit
	}
}
