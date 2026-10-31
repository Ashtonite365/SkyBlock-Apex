// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import dev.ashtonite365.apex.gui.hud.HudSnap
import org.joml.Matrix3x2f
import org.joml.Vector2f

object EditorLayout {
	const val EDGE_GAP = 2
	const val TOOLBAR_PADDING = 3

	enum class Target { HANDLE, ELEMENT, TOOLBAR, NONE }

	fun target(onHandle: Boolean, onElement: Boolean, onToolbar: Boolean): Target = when {
		onHandle -> Target.HANDLE
		onElement -> Target.ELEMENT
		onToolbar -> Target.TOOLBAR
		else -> Target.NONE
	}

	const val EYE_SIZE = 9

	fun eyeRect(r: IntArray, screenW: Int): IntArray {
		val left = r[0] - EYE_SIZE - 1
		val x = if (left >= 0 || r[0] + r[2] + 1 + EYE_SIZE > screenW) maxOf(left, 0) else r[0] + r[2] + 1
		return intArrayOf(x, maxOf(r[1], 0), EYE_SIZE, EYE_SIZE)
	}

	fun contains(rect: IntArray?, x: Double, y: Double): Boolean =
		rect != null && x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3]

	private fun coveredArea(a: IntArray, b: IntArray): Long {
		val across = minOf(a[0] + a[2], b[0] + b[2]) - maxOf(a[0], b[0])
		val down = minOf(a[1] + a[3], b[1] + b[3]) - maxOf(a[1], b[1])
		return if (across <= 0 || down <= 0) 0L else across.toLong() * down
	}

	fun toolbarRect(w: Int, h: Int, screenW: Int, screenH: Int, elements: List<IntArray>): IntArray {
		val right = maxOf(EDGE_GAP, screenW - w - EDGE_GAP)
		val bottom = maxOf(EDGE_GAP, screenH - h - EDGE_GAP)
		val middle = maxOf(EDGE_GAP, (screenW - w) / 2)
		val spots = listOf(EDGE_GAP to EDGE_GAP, right to EDGE_GAP, EDGE_GAP to bottom, right to bottom, middle to EDGE_GAP, middle to bottom)
		val scored = spots.asSequence().map { (x, y) ->
			val box = intArrayOf(x, y, w, h)
			box to elements.sumOf { coveredArea(box, it) }
		}
		return (scored.firstOrNull { it.second == 0L } ?: scored.minBy { it.second }).first
	}

	fun keepOnScreen(pos: Int, size: Int, screen: Int): Int = if (size >= screen) 0 else pos.coerceIn(0, screen - size)

	fun stepped(rect: IntArray, dx: Int, dy: Int, step: Int, screenW: Int, screenH: Int): IntArray =
		intArrayOf(keepOnScreen(rect[0] + step * dx, rect[2], screenW), keepOnScreen(rect[1] + step * dy, rect[3], screenH))

	fun arrowMove(hud: PlacedHud, rect: IntArray, dx: Int, dy: Int, shift: Boolean, snapping: HudSnapping?, screenW: Int, screenH: Int): IntArray {
		val to = snapping?.nudge(rect, dx, dy, shift, screenW, screenH) ?: stepped(rect, dx, dy, if (shift) 10 else 1, screenW, screenH)
		hud.moveTo(to[0], to[1])
		return to
	}

	fun labelSpot(x: Int, y: Int, w: Int, h: Int, textW: Int, lineH: Int, screenW: Int, screenH: Int): IntArray {
		val above = y - lineH - 1
		var top = if (above >= 0) above else y + h + 2
		if (top + lineH > screenH) top = maxOf(0, y + 1)
		return intArrayOf(maxOf(1, minOf(x, screenW - textW - 1)), top)
	}

	fun onScreen(pose: Matrix3x2f, l: Float, t: Float, r: Float, b: Float): FloatArray {
		val a = pose.transformPosition(Vector2f(l, t))
		val z = pose.transformPosition(Vector2f(r, b))
		return floatArrayOf(a.x, a.y, z.x - a.x, z.y - a.y)
	}
}

class HudSnapping(private val settingOn: () -> Boolean) {
	data class Drag(val x: Int, val y: Int, val guideX: Int?, val guideY: Int?)

	private fun rect(a: IntArray) = HudSnap.Rect(a[0], a[1], a[2], a[3])

	fun drag(start: IntArray, dx: Int, dy: Int, screenW: Int, screenH: Int, others: List<IntArray>, wanted: Boolean): Drag {
		val s = HudSnap.drag(rect(start), dx, dy, screenW, screenH, others.map(::rect), enabled = wanted && settingOn())
		return Drag(s.x, s.y, s.guideX, s.guideY)
	}

	fun nudge(rect: IntArray, dx: Int, dy: Int, shift: Boolean, screenW: Int, screenH: Int): IntArray =
		HudSnap.nudge(rect(rect), dx, dy, shift, screenW, screenH).let { intArrayOf(it.x, it.y) }
}
