// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.math

import kotlin.math.abs
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.math.Projections

class ProjectionsBoxTest {
	private fun at(degrees: Double) = Projections.Two.projectAngleOntoUnitBox(Math.toRadians(degrees))

	@Test
	fun cardinalDirectionsHitEdgeMidpoints() {
		for ((deg, x, y) in listOf(Triple(0.0, 1f, 0f), Triple(90.0, 0f, 1f), Triple(180.0, -1f, 0f), Triple(270.0, 0f, -1f))) {
			val p = at(deg)
			assertEquals(x, p.x, 1e-4f, "x at $deg")
			assertEquals(y, p.y, 1e-4f, "y at $deg")
		}
	}

	@Test
	fun diagonalsHitCorners() {
		val p = at(225.0)
		assertEquals(-1f, p.x, 1e-4f)
		assertEquals(-1f, p.y, 1e-4f)
	}

	@Test
	fun everyAngleLandsOnTheBorderAlongTheRay() {
		for (deg in 0 until 360 step 7) {
			val p = at(deg.toDouble())
			assertEquals(1f, max(abs(p.x), abs(p.y)), 1e-4f, "on border at $deg")
			val angle = Math.toDegrees(kotlin.math.atan2(p.y.toDouble(), p.x.toDouble())).let { if (it < 0) it + 360 else it }
			assertEquals(deg.toDouble(), angle, 1e-3, "direction at $deg")
		}
	}

	@Test
	fun anglesWrapAroundFullTurns() {
		val a = at(30.0)
		val b = at(30.0 + 720.0)
		assertEquals(a.x, b.x, 1e-4f)
		assertEquals(a.y, b.y, 1e-4f)
	}
}
