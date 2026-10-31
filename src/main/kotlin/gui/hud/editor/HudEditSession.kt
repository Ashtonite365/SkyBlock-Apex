// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import kotlin.math.sqrt

class HudEditSession(
	private val everyHud: List<PlacedHud>,
	private val snapping: HudSnapping?,
	private val screenSize: () -> Pair<Int, Int>,
) {
	private enum class Mode { MOVE, SCALE }

	private var mode = Mode.MOVE
	var held: PlacedHud? = null; private set
	var selected: PlacedHud? = null; private set
	private var grabX = 0.0
	private var grabY = 0.0
	private var startRect: IntArray? = null
	private var pivotX = 0.0
	private var pivotY = 0.0
	private var pivotFx = 0f
	private var pivotFy = 0f
	private var scalePerPx = 0.0

	private class HandleDrag(val hud: ResizableHud, val grip: HudGeometry.Grip, val mouseX: Double, val mouseY: Double,
	                         val left: Int, val top: Int, val sx: Float, val sy: Float, val uw: Int, val uh: Int)
	private var handleDrag: HandleDrag? = null
	val resizingWith: HudGeometry.Grip? get() = handleDrag?.grip
	val resizingHud: ResizableHud? get() = handleDrag?.hud

	var guideX: Int? = null; private set
	var guideY: Int? = null; private set
	val isScaling: Boolean get() = mode == Mode.SCALE

	fun shown(): List<PlacedHud> {
		val list = visibleOf(everyHud)
		if (held != null && held !in list) held = null
		if (handleDrag != null && handleDrag!!.hud !in list) handleDrag = null
		if (selected != null && selected !in list) selected = null
		return list
	}

	fun everyHud(): List<PlacedHud> = everyHud

	fun rectOf(hud: PlacedHud): IntArray = hud.topLeft.let { intArrayOf(it.x(), it.y(), hud.drawnWidth, hud.drawnHeight) }

	private fun isOver(hud: PlacedHud, x: Double, y: Double): Boolean {
		val r = rectOf(hud)
		return x > r[0] && x < r[0] + r[2] && y > r[1] && y < r[1] + r[3]
	}

	fun hudAt(x: Double, y: Double): PlacedHud? = shown().firstOrNull { isOver(it, x, y) }

	fun pressLeft(x: Double, y: Double, toolbarClick: (Double, Double) -> Boolean = { _, _ -> false }): Boolean {
		if (held != null || handleDrag != null) return false
		if (startHandleDrag(x, y)) return true
		if (hudAt(x, y) == null && toolbarClick(x, y)) return true
		mode = Mode.MOVE
		grab(x, y)
		return true
	}

	fun pressRight(x: Double, y: Double): Boolean {
		if (held != null) return false
		mode = Mode.SCALE
		grab(x, y)
		val hud = held as? ResizableHud
		if (hud == null) { letGo(); return false }
		scalePerPx = hud.uniformScale / distanceToPivot(x, y)
		return true
	}

	fun pressMiddle(x: Double, y: Double): Boolean? = hudAt(x, y)?.let { resetSize(it); true }

	fun releaseLeft(): Boolean? {
		handleDrag?.let { it.hud.finishResize(); handleDrag = null; return true }
		if (mode == Mode.SCALE) return null
		letGo()
		return true
	}

	fun releaseRight(): Boolean? {
		if (mode != Mode.SCALE) return null
		letGo()
		return true
	}

	fun move(x: Double, y: Double, shift: () -> Boolean) {
		val drag = handleDrag
		when {
			drag != null -> {
				val (w, h) = screenSize()
				val r = HudGeometry.resize(drag.grip, drag.left, drag.top, drag.uw, drag.uh, drag.sx, drag.sy,
					x - drag.mouseX, y - drag.mouseY, shift(), w, h)
				drag.hud.resizeFrame(r.scaleX, r.scaleY, r.x, r.y)
			}
			mode == Mode.SCALE -> scaleHeld(x, y)
			else -> dragHeld(x, y, shift)
		}
	}

	fun arrow(dx: Int, dy: Int, shift: Boolean, mouseX: Double, mouseY: Double): Boolean {
		val target = selected?.takeIf { it in shown() } ?: hudAt(mouseX, mouseY) ?: return false
		val (w, h) = screenSize()
		EditorLayout.arrowMove(target, rectOf(target), dx, dy, shift, snapping, w, h)
		selected = target
		return true
	}

	fun resetSize(hud: PlacedHud) { (hud as? ResizableHud)?.resetSize() }

	private fun startHandleDrag(x: Double, y: Double): Boolean {
		for (hud in shown()) {
			if (hud !is ResizableHud) continue
			val r = rectOf(hud)
			val grip = HudGeometry.gripAt(x, y, r[0].toDouble(), r[1].toDouble(), r[2].toDouble(), r[3].toDouble()) ?: continue
			handleDrag = HandleDrag(hud, grip, x, y, r[0], r[1], hud.scaleX, hud.scaleY, hud.unscaledWidth, hud.unscaledHeight)
			return true
		}
		return false
	}

	private fun grab(x: Double, y: Double) {
		val hud = hudAt(x, y) ?: return
		val r = rectOf(hud)
		grabX = x - r[0]
		grabY = y - r[1]
		val pivot = pivotFor(r, grabX, grabY)
		pivotX = pivot.x; pivotY = pivot.y; pivotFx = pivot.fx; pivotFy = pivot.fy
		held = hud
		selected = hud
		startRect = r
		pressX = x
		pressY = y
	}

	private var pressX = 0.0
	private var pressY = 0.0

	private fun scaleHeld(x: Double, y: Double) {
		val hud = held as? ResizableHud ?: return
		val scale = distanceToPivot(x, y) * scalePerPx
		if (scale < 0.2) return
		hud.uniformScale = scale.toFloat()
		hud.moveTo((pivotX - hud.drawnWidth * pivotFx.toDouble()).toInt(), (pivotY - hud.drawnHeight * pivotFy.toDouble()).toInt())
	}

	private fun dragHeld(x: Double, y: Double, shift: () -> Boolean) {
		val hud = held ?: return
		val start = startRect
		if (snapping != null && start != null) {
			val (w, h) = screenSize()
			val others = shown().filter { it !== hud }.map(::rectOf)
			val d = snapping.drag(start, Math.round(x - pressX).toInt(), Math.round(y - pressY).toInt(), w, h, others, !shift())
			guideX = d.guideX
			guideY = d.guideY
			hud.moveTo(d.x, d.y)
			return
		}
		hud.moveTo((x - grabX).toInt(), (y - grabY).toInt())
	}

	private fun distanceToPivot(x: Double, y: Double): Double {
		val dx = pivotX - x
		val dy = pivotY - y
		return sqrt(dx * dx + dy * dy)
	}

	private fun letGo() {
		held = null
		startRect = null
		guideX = null
		guideY = null
	}

	data class Pivot(val x: Double, val y: Double, val fx: Float, val fy: Float)

	companion object {

		fun pivotFor(rect: IntArray, grabX: Double, grabY: Double): Pivot {
			val fx = if (grabX < rect[2] / 2) 1f else 0f
			val fy = if (grabY < rect[3] / 2) 1f else 0f
			return Pivot(rect[0] + rect[2] * fx.toDouble(), rect[1] + rect[3] * fy.toDouble(), fx, fy)
		}

		fun visibleOf(all: List<PlacedHud>): List<PlacedHud> = all.filter { runCatching { it.isEnabled }.getOrDefault(true) }
	}
}
