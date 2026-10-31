// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.features.fishing.HotspotRadarMath
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PestTrailMath {
	const val MIN_POINTS = 4
	private const val MAX_SPAN = 2_000.0

	fun derivativePitch(d: Vec3): Double {
		val horizontal = sqrt(d.x * d.x + d.z * d.z)
		return -atan2(d.y, horizontal)
	}

	private fun startSlope(lookPitch: Double) = atan2(sin(lookPitch) - 0.75, cos(lookPitch))

	fun lookPitchFromSlope(observed: Double): Double {
		var low = -PI / 2
		var high = PI / 2
		for (step in 0 until 100) {
			val middle = (low + high) / 2
			if (startSlope(middle) < observed) low = middle else high = middle
		}
		return (low + high) / 2
	}

	fun controlPointDistance(lookPitch: Double): Double = sqrt(25.0 - 24.0 * sin(lookPitch))

	private fun cubic(c: DoubleArray, t: Double) = c[0] + c[1] * t + c[2] * t * t + c[3] * t * t * t

	fun solve(points: List<Vec3>): Vec3? {
		val count = points.size
		if (count < MIN_POINTS) return null
		val fits = listOf<(Vec3) -> Double>({ it.x }, { it.y }, { it.z }).map { axis ->
			HotspotRadarMath.polyFit(DoubleArray(count) { axis(points[it]) }, 3) ?: return null
		}
		val (fx, fy, fz) = fits
		val tangent = Vec3(fx[1], fy[1], fz[1])
		val speed = tangent.length()
		if (speed < 1e-6) return null
		val end = 3 * controlPointDistance(lookPitchFromSlope(derivativePitch(tangent))) / speed
		if (!end.isFinite() || end < count - 1 || end > MAX_SPAN) return null
		return Vec3(cubic(fx, end), cubic(fy, end), cubic(fz, end))
	}
}
