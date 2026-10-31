// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

class WikiHold(var holdMs: Long = 1500, val delayMs: Long = 0) {
	sealed interface State {
		data object Hidden : State

		data class Filling(val raw: Float, val shown: Float) : State
		data object Fire : State
	}

	private var key: Any? = null
	private var start = -1L
	private var suppressed = false
	var state: State = State.Hidden
		private set

	fun suppressUntilRelease() { suppressed = true; reset() }

	private fun reset() { key = null; start = -1L }

	fun update(now: Long, keyDown: Boolean, hovered: Any?): State {
		state = step(now, keyDown, hovered)
		return state
	}

	private fun step(now: Long, keyDown: Boolean, hovered: Any?): State {
		if (!keyDown) { suppressed = false; reset(); return State.Hidden }
		if (suppressed) return State.Hidden
		if (hovered == null) { reset(); return State.Hidden }
		if (hovered != key || start < 0) { key = hovered; start = now }
		val elapsed = now - start - delayMs
		if (elapsed <= 0) return State.Filling(0f, 0f)
		val total = holdMs.coerceAtLeast(1)
		if (elapsed >= total) { suppressUntilRelease(); return State.Fire }
		val raw = progress(elapsed, total)
		return State.Filling(raw, raw)
	}

	companion object {

		fun progress(elapsedMs: Long, totalMs: Long): Float =
			(elapsedMs.toDouble() / totalMs.coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
	}
}

object ItemWikiHint {
	const val SEGMENTS = 24
	data class Lines(val keyName: String, val filled: Int, val total: Int = SEGMENTS)

	fun lines(state: WikiHold.State, keyName: String): Lines? {
		val s = state as? WikiHold.State.Filling ?: return null
		return Lines(keyName, (s.shown * SEGMENTS).toInt().coerceIn(0, SEGMENTS))
	}
}
