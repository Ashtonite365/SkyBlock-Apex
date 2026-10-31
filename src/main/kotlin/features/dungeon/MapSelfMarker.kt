// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object MapSelfMarker {

	const val WORLD_ORIGIN = -200

	const val WORLD_STEP = 32

	const val CELLS = 6

	const val SAMPLES = 3

	const val MAX_DRIFT_STEPS = 1.5

	data class Calibration(val originX: Int, val originY: Int, val step: Int) {
		val pxPerBlock: Double get() = step.toDouble() / WORLD_STEP
	}

	fun worldToMap(cal: Calibration, x: Double, z: Double): Pair<Double, Double> =
		cal.originX + (x - WORLD_ORIGIN) * cal.pxPerBlock to cal.originY + (z - WORLD_ORIGIN) * cal.pxPerBlock

	fun worldCell(x: Double, z: Double): Pair<Int, Int>? {
		val c = Math.floorDiv(floor(x).toInt() - WORLD_ORIGIN, WORLD_STEP)
		val r = Math.floorDiv(floor(z).toInt() - WORLD_ORIGIN, WORLD_STEP)
		return if (c in 0 until CELLS && r in 0 until CELLS) c to r else null
	}

	fun decorationPixel(b: Int): Double = (b + 128) / 2.0

	fun sample(grid: DungeonMap.Grid, markerPx: Double, markerPy: Double, x: Double, z: Double): Calibration? {
		worldCell(x, z) ?: return null
		val ppb = grid.step.toDouble() / WORLD_STEP
		fun snap(raw: Double, lattice: Int): Int? {
			val k = ((raw - lattice) / grid.step).roundToInt()
			val o = lattice + k * grid.step
			return if (abs(raw - o) <= grid.step / 4.0) o else null
		}
		val ox = snap(markerPx - (x - WORLD_ORIGIN) * ppb, grid.x) ?: return null
		val oy = snap(markerPy - (z - WORLD_ORIGIN) * ppb, grid.y) ?: return null
		return Calibration(ox, oy, grid.step)
	}

	class Calibrator {
		var calibration: Calibration? = null; private set
		private var grid: DungeonMap.Grid? = null
		private var candidate: Calibration? = null
		private var streak = 0

		fun reset() { calibration = null; grid = null; candidate = null; streak = 0 }

		fun update(g: DungeonMap.Grid?, markerPx: Double?, markerPy: Double?, x: Double, z: Double): Calibration? {
			if (g != grid) { reset(); grid = g }
			if (g == null || calibration != null) return calibration
			val s = if (markerPx != null && markerPy != null) sample(g, markerPx, markerPy, x, z) else null
			if (s == null) return null
			if (s == candidate) streak++ else { candidate = s; streak = 1 }
			if (streak >= SAMPLES) calibration = s
			return calibration
		}
	}

	fun lerp(pt: Float, old: Double, new: Double): Double = old + (new - old) * pt

	fun lerpYaw(pt: Float, old: Float, new: Float): Float {
		var d = (new - old) % 360F
		if (d >= 180F) d -= 360F
		if (d < -180F) d += 360F
		return old + d * pt
	}

	fun markerRotation(yaw: Float): Float = (yaw % 360F + 360F) % 360F

	enum class Source { LIVE, PACKET }

	data class Placement(val source: Source, val x: Double, val y: Double, val rotation: Float)

	fun place(
		enabled: Boolean, cal: Calibration?, inBoss: Boolean,
		x: Double, z: Double, yaw: Float,
		packet: Triple<Double, Double, Float>?, size: Int = DungeonMap.SIZE,
	): Placement? {
		packet ?: return null
		val fallback = Placement(Source.PACKET, packet.first, packet.second, packet.third)
		if (!enabled || cal == null || inBoss || worldCell(x, z) == null) return fallback
		val (lx, ly) = worldToMap(cal, x, z)
		if (lx < 0 || ly < 0 || lx >= size || ly >= size) return fallback
		val dx = lx - packet.first; val dy = ly - packet.second
		val max = MAX_DRIFT_STEPS * cal.step
		if (dx * dx + dy * dy > max * max) return fallback
		return Placement(Source.LIVE, lx, ly, markerRotation(yaw))
	}
}
