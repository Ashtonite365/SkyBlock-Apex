package dev.ashtonite365.apex.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

class Timer {
	private var markedAtNanos = NEVER

	fun timePassed(): Duration =
		if (markedAtNanos == NEVER) Duration.INFINITE else (System.nanoTime() - markedAtNanos).nanoseconds

	fun markNow() {
		markedAtNanos = System.nanoTime()
	}

	fun markFarPast() {
		markedAtNanos = NEVER
	}

	private companion object {
		const val NEVER = Long.MIN_VALUE
	}
}
