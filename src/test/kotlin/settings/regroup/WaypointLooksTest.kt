// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings.regroup

import kotlin.math.abs
import kotlin.math.sqrt
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.HudHidden
import dev.ashtonite365.apex.util.render.MarkerGeometry
import dev.ashtonite365.apex.util.render.MarkerShape
import dev.ashtonite365.apex.util.render.WaypointLooks
import dev.ashtonite365.apex.util.render.WaypointLooks.Look
import dev.ashtonite365.apex.util.render.WaypointType

class WaypointLooksTest {
	private val general = Look(MarkerShape.SPHERE_OUTLINE, 80, 2.0)

	@Test fun useGeneralCopiesTheGeneralLook() {
		assertEquals(general, WaypointLooks.resolve(general, true, MarkerShape.SQUARE_FILLED, 10, 3.0))
		val today = Look(MarkerShape.CUBE_FILLED, 100, 1.0)
		assertTrue(WaypointLooks.isPlainCube(WaypointLooks.resolve(today, true, MarkerShape.CUBE_FILLED, 100, 1.0)), "defaults = today's cube")
	}

	@Test fun ownLookMultipliesTotalAndSize() {
		val own = WaypointLooks.resolve(general, false, MarkerShape.CIRCLE_FILLED, 50, 1.5)
		assertEquals(Look(MarkerShape.CIRCLE_FILLED, 40, 3.0), own)
		assertEquals(WaypointLooks.MAX_SIZE, WaypointLooks.resolve(Look(MarkerShape.CUBE_FILLED, 100, 4.0), false, MarkerShape.CUBE_FILLED, 100, 4.0).size)
		assertEquals(0, WaypointLooks.resolve(Look(MarkerShape.CUBE_FILLED, 0, 1.0), false, MarkerShape.CUBE_FILLED, 100, 1.0).opacity)
	}

	@Test fun sizeClampAndRounding() {
		assertEquals(0.25, WaypointLooks.MIN_SIZE)
		assertEquals(0.25, WaypointLooks.resolve(Look(MarkerShape.CUBE_FILLED, 100, 0.25), false, MarkerShape.CUBE_FILLED, 100, 0.25).size)
		assertEquals(33, WaypointLooks.percentOf(55, 60), "55% of 60% = 33%")
		assertEquals(17, WaypointLooks.percentOf(33, 50), "16.5 rounds up")
		assertEquals(0x80FF0000.toInt(), WaypointLooks.fade(0xFFFF0000.toInt(), 50), "127.5 rounds to 128")
	}

	@Test fun fadeAlpha() {
		assertEquals(0x40FF0000, WaypointLooks.fade(0x80FF0000.toInt(), 50))
		assertEquals(0x80FF0000.toInt(), WaypointLooks.fade(0x80FF0000.toInt(), 100))
		assertEquals(0x00FF0000, WaypointLooks.fade(0x80FF0000.toInt(), 0))
	}

	@Test fun enumsMatchThePlan() {
		assertEquals(8, MarkerShape.entries.size)
		assertEquals(19, WaypointType.entries.size)
		assertEquals("cube_filled", MarkerShape.CUBE_FILLED.serializedName)
		assertEquals(4, MarkerShape.entries.count { it.flat })
	}

	@Test fun circleAndSphereGeometry() {
		val n = MarkerGeometry.CIRCLE_SEGMENTS
		assertEquals(32, n)
		for (i in 0..n) assertEquals(1F, MarkerGeometry.COS[i] * MarkerGeometry.COS[i] + MarkerGeometry.SIN[i] * MarkerGeometry.SIN[i], 1e-5F)
		assertEquals(MarkerGeometry.COS[0], MarkerGeometry.COS[n], 1e-5F)
		val p = MarkerGeometry.SPHERE
		for (i in p.indices step 3) assertEquals(0.5, sqrt((p[i] * p[i] + p[i + 1] * p[i + 1] + p[i + 2] * p[i + 2]).toDouble()), 1e-5)
		assertEquals(0.5F, p[MarkerGeometry.sphereIndex(0, 0) + 1], 1e-6F)
	}

	@Test fun billboardFacesTheCamera() {
		val right = Vector3f(); val up = Vector3f()

		val q = Quaternionf().rotateY(Math.toRadians(90.0).toFloat()).rotateX(Math.toRadians(-30.0).toFloat())
		MarkerGeometry.flatAxes(true, q, right, up)
		val forward = Vector3f(0F, 0F, -1F).rotate(q)
		assertEquals(0F, right.dot(forward), 1e-5F, "plane contains no view direction")
		assertEquals(0F, up.dot(forward), 1e-5F)
		assertEquals(1F, right.length(), 1e-5F)
		MarkerGeometry.flatAxes(false, q, right, up)
		assertEquals(Vector3f(1F, 0F, 0F), right)
		assertEquals(Vector3f(0F, 0F, 1F), up)
		val out = FloatArray(12)
		MarkerGeometry.squareCorners(1F, 2F, 3F, 2F, right, up, out)
		for (k in 0 until 4) {
			assertEquals(2F, out[k * 3 + 1], 1e-6F, "flat square lies in y = centre")
			assertTrue(abs(out[k * 3] - 1F) == 1F && abs(out[k * 3 + 2] - 3F) == 1F)
		}
	}

	@Test fun hudHiddenKeyOfLabel() {
		assertEquals("timer-boards.show-on-x", HudHidden.keyOfLabel("apex.config.timer-boards.show-on-x"))
		assertEquals(null, HudHidden.keyOfLabel("other.key"))
		assertEquals(null, HudHidden.keyOfLabel("apex.config."))
		assertEquals(null, HudHidden.keyOfLabel(null))
	}
}
