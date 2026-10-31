package dev.ashtonite365.apex.gui.config

object IntegerStep {
	fun bigStep(min: Int, max: Int): Int {
		val range = (max - min).coerceAtLeast(0)
		return (range / 10).coerceAtLeast(1).coerceAtMost((range / 2).coerceAtLeast(1))
	}

	fun next(current: Int, direction: Int, min: Int, max: Int, shift: Boolean = false): Int {
		val size = if (shift) bigStep(min, max) else 1
		val d = if (direction < 0) -size else size
		return (current.toLong() + d).coerceIn(min.toLong(), max.toLong()).toInt()
	}
}
