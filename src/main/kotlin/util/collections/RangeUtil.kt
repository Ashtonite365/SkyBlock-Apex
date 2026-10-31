// SPDX-License-Identifier: GPL-3.0-or-later
@file:JvmName("RangeUtil")
package dev.ashtonite365.apex.util.collections

import kotlin.math.floor

val ClosedFloatingPointRange<Float>.centre: Float
	get() = (endInclusive + start) / 2

fun ClosedFloatingPointRange<Float>.nonNegligibleSubSectionsAlignedWith(interval: Float): Iterable<Float> {
	require(interval.isFinite()) { "Failed requirement." }
	return Iterable { AlignedCuts(start, endInclusive, interval) }
}

private class AlignedCuts(first: Float, private val last: Float, private val step: Float) : FloatIterator() {

	private var pending: Float = first
	private var previous: Float = first

	override fun hasNext(): Boolean = when {
		!pending.isNaN() -> true
		previous == last -> false
		else -> {
			val candidate = (floor(previous / step) + 1) * step
			pending = if (candidate > last) last else candidate
			true
		}
	}

	override fun nextFloat(): Float =
		if (!this.hasNext()) throw NoSuchElementException()
		else pending.also { value -> previous = value; pending = Float.NaN }
}
