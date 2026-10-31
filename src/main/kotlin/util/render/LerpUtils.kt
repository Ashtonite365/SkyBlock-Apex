package dev.ashtonite365.apex.util.render

import me.shedaniel.math.Color
import kotlin.math.abs

val HALF_TURN = Math.PI

val FULL_TURN = 2 * Math.PI

private fun turns(radians: Double): Double {
	val r = radians % FULL_TURN
	return if (r < 0) r + FULL_TURN else r
}

fun blendAngle(from: Float, to: Float, t: Float): Float {
	val gap = to - from
	if (abs(abs(gap) - HALF_TURN) < 1e-4) return interpolate(from, to, t)

	val anticlockwise = turns(to.toDouble() - from.toDouble())
	val shortest = if (anticlockwise < HALF_TURN) anticlockwise else anticlockwise - FULL_TURN
	return turns(from + shortest * t).toFloat()
}

fun normaliseAngle(angle: Double): Double {
	val once = angle % FULL_TURN

	return (once + FULL_TURN) % FULL_TURN
}

fun normaliseAngle(angle: Float): Float = normaliseAngle(angle.toDouble()).toFloat()

fun interpolate(from: Float, to: Float, t: Float): Float = from + (to - from) * t

fun interpolate(from: Int, to: Int, t: Float): Int {

	val exact = from + (to - from) * t
	return exact.toInt()
}

fun progressOf(from: Float, to: Float, value: Float): Float = (value - from) / (to - from)

fun interpolate(from: Color, to: Color, t: Float): Color {
	fun channel(of: (Color) -> Int) = interpolate(of(from), of(to), t)
	return Color.ofRGBA(channel { it.red }, channel { it.green }, channel { it.blue }, channel { it.alpha })
}
