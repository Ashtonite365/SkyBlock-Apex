package dev.ashtonite365.apex.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TimeMark private constructor(private val millis: Long) : Comparable<TimeMark> {
	private val never get() = millis == NEVER

	fun passedAt(fakeNow: TimeMark): Duration =
		if (never) Duration.INFINITE else (fakeNow.millis - millis).milliseconds

	fun passedTime(): Duration = passedAt(now())

	operator fun minus(other: TimeMark): Duration {
		if (millis == other.millis) return Duration.ZERO
		if (other.never) return Duration.INFINITE
		if (never) return -Duration.INFINITE
		return (millis - other.millis).milliseconds
	}

	fun before(delta: Duration): TimeMark {
		val reachesNever = never || delta.isInfinite()
		if (!reachesNever) return TimeMark(millis - delta.inWholeMilliseconds)
		require(never || delta.isPositive())
		return farPast()
	}

	fun after(delta: Duration): TimeMark {
		require(delta.isFinite())
		return TimeMark(millis + delta.inWholeMilliseconds)
	}

	override fun compareTo(other: TimeMark): Int = millis.compareTo(other.millis)
	override fun equals(other: Any?): Boolean = other is TimeMark && other.millis == millis
	override fun hashCode(): Int = millis.hashCode()
	override fun toString(): String = "https://time.is/$millis"

	companion object {
		private const val NEVER = 0L

		fun now(): TimeMark = TimeMark(System.currentTimeMillis())
		fun farPast(): TimeMark = TimeMark(NEVER)

		fun ago(span: Duration): TimeMark = now().before(span)
	}
}

private const val TICK_MILLIS = 50L

fun Duration.toTicks(): Long = inWholeMilliseconds / TICK_MILLIS
