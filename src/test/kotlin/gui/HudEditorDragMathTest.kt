package dev.ashtonite365.apex.test.gui

import dev.ashtonite365.apex.gui.hud.editor.EditorLayout
import org.joml.Matrix3x2f
import org.joml.Vector2i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HudEditorDragMathTest {
	private class FakeHud(pos: Vector2i, s: Float, w: Int, h: Int) : TestScaledHud(pos, w, h, s)

	private fun outlineRect(h: FakeHud): FloatArray {
		val pose = Matrix3x2f()
		h.applyTo(pose)
		return EditorLayout.onScreen(pose, 0f, 0f, h.w.toFloat(), h.h.toFloat())
	}

	@Test
	fun outlineTracksElementDuringAndAfterDrag() {
		for (guiScale in listOf(1, 2, 3, 4 )) for (scale in listOf(0.5f, 1f, 1.37f, 2f)) {
			val hud = FakeHud(Vector2i(40, 30), scale, 128, 128)
			val grab = Pair(50.0, 45.0)
			val off = Pair(grab.first - hud.pos.x(), grab.second - hud.pos.y())
			for (step in 1..20) {

				val mx = grab.first + step * 7.0 / guiScale
				val my = grab.second + step * 5.0 / guiScale
				hud.moveTo(Math.round(mx - off.first).toInt(), Math.round(my - off.second).toInt())
				val r = outlineRect(hud)
				assertEquals(hud.pos.x().toFloat(), r[0], 1e-3f, "x gs=$guiScale s=$scale")
				assertEquals(hud.pos.y().toFloat(), r[1], 1e-3f, "y gs=$guiScale s=$scale")
				assertEquals(hud.w * scale, r[2], 1e-3f)
				assertEquals(hud.h * scale, r[3], 1e-3f)

				assertEquals(mx - off.first, hud.pos.x().toDouble(), 0.5)
			}
		}
	}

	@Test
	fun labelStaysOutsideElementBox() {
		val r = EditorLayout.labelSpot(40, 30, 128, 64, 50, 9, 480, 270)
		assert(r[1] + 9 <= 30)
		val top = EditorLayout.labelSpot(40, 0, 128, 64, 50, 9, 480, 270)
		assert(top[1] >= 64)
		val right = EditorLayout.labelSpot(470, 30, 10, 10, 50, 9, 480, 270)
		assert(right[0] + 50 <= 480)
	}
}
