/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.features.fishing

import net.minecraft.world.phys.Vec3

object HotspotRadarMath {
	const val FIRST_MAX_DIST = 5.0
	const val CHAIN_MAX_DIST = 3.0
	const val MIN_POINTS = 4

	fun isTrailParticle(isFlame: Boolean, count: Int, speed: Float) = isFlame && count == 1 && speed == 0f

	fun accept(points: List<Vec3>, p: Vec3, origin: Vec3): Boolean {
		if (points.isEmpty()) return p.distanceTo(origin) <= FIRST_MAX_DIST
		return p.distanceTo(points.last()) <= CHAIN_MAX_DIST
	}

	private fun quadFit(ys: DoubleArray): Triple<Double, Double, Double> {
		val n = ys.size
		var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0
		var y0 = 0.0; var y1 = 0.0; var y2 = 0.0
		for (i in 0 until n) {
			val t = i.toDouble(); val t2 = t * t
			s0 += 1; s1 += t; s2 += t2; s3 += t2 * t; s4 += t2 * t2
			y0 += ys[i]; y1 += ys[i] * t; y2 += ys[i] * t2
		}

		fun det(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double, g: Double, h: Double, i: Double) =
			a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
		val d = det(s0, s1, s2, s1, s2, s3, s2, s3, s4)
		if (n < 3 || kotlin.math.abs(d) < 1e-9) {

			val m = if (n > 1) (ys.last() - ys.first()) / (n - 1) else 0.0
			return Triple(ys.first(), m, 0.0)
		}
		val a = det(y0, s1, s2, y1, s2, s3, y2, s3, s4) / d
		val b = det(s0, y0, s2, s1, y1, s3, s2, y2, s4) / d
		val c = det(s0, s1, y0, s1, s2, y1, s2, s3, y2) / d
		return Triple(a, b, c)
	}

	fun polyFit(ys: DoubleArray, degree: Int): DoubleArray? {
		val m = degree + 1
		if (ys.size < m) return null
		val a = Array(m) { DoubleArray(m + 1) }
		for ((i, y) in ys.withIndex()) {
			val pw = DoubleArray(2 * m) { 1.0 }
			for (k in 1 until 2 * m) pw[k] = pw[k - 1] * i
			for (r in 0 until m) {
				for (c in 0 until m) a[r][c] += pw[r + c]
				a[r][m] += y * pw[r]
			}
		}
		for (col in 0 until m) {
			val piv = (col until m).maxBy { kotlin.math.abs(a[it][col]) }
			if (kotlin.math.abs(a[piv][col]) < 1e-12) return null
			val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
			for (r in 0 until m) if (r != col) {
				val f = a[r][col] / a[col][col]
				for (c in col..m) a[r][c] -= f * a[col][c]
			}
		}
		return DoubleArray(m) { a[it][m] / a[it][it] }
	}

	private fun eval(c: DoubleArray, t: Double) = c.foldRight(0.0) { k, acc -> acc * t + k }
	private fun deriv(c: DoubleArray, t: Double): Double {
		var r = 0.0
		for (k in c.size - 1 downTo 1) r = r * t + k * c[k]
		return r
	}

	fun controlPointDistance(pitchDegrees: Double): Double =
		kotlin.math.sqrt(25.0 + 24.0 * kotlin.math.sin(Math.toRadians(pitchDegrees)))

	fun guessFromPitch(points: List<Vec3>, pitchDegrees: Double): Vec3? {
		if (points.size < MIN_POINTS) return null
		val n = points.size
		val cx = polyFit(DoubleArray(n) { points[it].x }, 3) ?: return null
		val cy = polyFit(DoubleArray(n) { points[it].y }, 3) ?: return null
		val cz = polyFit(DoubleArray(n) { points[it].z }, 3) ?: return null
		val d0 = Vec3(deriv(cx, 0.0), deriv(cy, 0.0), deriv(cz, 0.0)).length()
		if (d0 < 1e-6) return null
		val t = 3 * controlPointDistance(pitchDegrees) / d0
		if (!t.isFinite() || t < n - 1 || t > 10_000) return null
		return Vec3(eval(cx, t), eval(cy, t), eval(cz, t))
	}

	fun guess(points: List<Vec3>, pitchDegrees: Double? = null): Vec3? {
		if (points.size < MIN_POINTS) return null
		if (pitchDegrees != null) guessFromPitch(points, pitchDegrees)?.let { return it }

		val n = points.size
		val fx = quadFit(DoubleArray(n) { points[it].x })
		val fy = quadFit(DoubleArray(n) { points[it].y })
		val fz = quadFit(DoubleArray(n) { points[it].z })
		val t = (n - 1).toDouble()
		fun at(f: Triple<Double, Double, Double>) = f.first + f.second * t + f.third * t * t
		fun slope(f: Triple<Double, Double, Double>) = f.second + 2 * f.third * t
		val end = Vec3(at(fx), at(fy), at(fz))
		var dir = Vec3(slope(fx), slope(fy), slope(fz))
		if (dir.lengthSqr() < 1e-9) dir = points.last().subtract(points.first())
		if (dir.lengthSqr() < 1e-9) return null
		dir = dir.normalize()
		val length = (1 until n).sumOf { points[it].distanceTo(points[it - 1]) }
		return end.add(dir.scale(maxOf(30.0, length * 4)))
	}
}
