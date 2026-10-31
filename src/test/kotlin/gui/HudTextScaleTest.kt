package dev.ashtonite365.apex.test.gui

import dev.ashtonite365.apex.gui.hud.HudTextScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudTextScaleTest {
	private val scales = listOf(1f to 1f, 1.5f to 1.5f, 0.5f to 0.5f, 2.2f to 0.6f, 0.6f to 2.2f, 3f to 1f, 1f to 3f, 1.0001f to 1f, 0.25f to 4f)
	private val sizes = listOf(80 to 10, 90 to 5, 100 to 16, 80 to 80, 200 to 40, 1 to 1)

	@Test
	fun textTransformIsUniformForEverySize() {
		for ((uw, uh) in sizes) for ((sx, sy) in scales) for (content in listOf(uw.toFloat() to uh.toFloat(), uw * 2f to uh * 3f, 5f to 5f)) {
			val f = HudTextScale.fitElement(10, 20, uw, uh, sx, sy, content.first, content.second)
			val tag = "uw=$uw uh=$uh s=$sx/$sy content=$content"
			assertEquals(f.scaleX, f.scaleY, tag)
			assertEquals(minOf(sx, sy), f.scale, tag)

			assertEquals(uw * sx, f.localW * f.scale, 1e-3f, tag)
			assertEquals(uh * sy, f.localH * f.scale, 1e-3f, tag)
		}
	}

	@Test
	fun unstretchedElementsKeepTheirOldPose() {
		for ((uw, uh) in sizes) for (s in listOf(0.5f, 1f, 1.5f, 2f)) {

			val f = HudTextScale.fitElement(10, 20, uw, uh, s, s, uw * 3f, uh * 2f)
			assertEquals(10f, f.x); assertEquals(20f, f.y); assertEquals(s, f.scale)
		}
	}

	@Test
	fun stretchedTextIsCentredWhereItOverflowsAndNeverCut() {

		val f = HudTextScale.fitElement(0, 0, 100, 16, 2.2f, 0.6f, 100f, 30f)
		assertEquals(0f, f.x)
		assertEquals((16 * 0.6f - 30 * 0.6f) / 2f, f.y, 1e-4f)
		assertTrue(f.y < 0f)
		val g = HudTextScale.fitElement(50, 0, 10, 40, 1f, 3f, 30f, 10f)
		assertEquals(50f + (10f - 30f) / 2f, g.x, 1e-4f)
	}

	@Test
	fun barLabelIsUniformAndFits() {
		for ((w, h) in listOf(90f to 5f, 90f to 11f, 220f to 9.6f, 40f to 30f, 10f to 5f)) for (tw in listOf(20, 60, 140)) {
			val s = HudTextScale.barLabel(w, h, tw)
			assertTrue(s > 0f)
			assertTrue(tw * s <= maxOf(w - 2f, 1f) + 1e-3f, "label overflows: w=$w h=$h tw=$tw s=$s")
		}

		assertEquals(HudTextScale.barLabel(200f, 11f, 30), HudTextScale.barLabel(400f, 11f, 30))
	}
}
