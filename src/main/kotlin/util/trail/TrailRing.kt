/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail: fixed-size, age-limited ring of recent events, kept in memory only.
 */
package dev.ashtonite365.apex.util.trail

class TrailRing(val capacity: Int, val maxAgeMs: Long) {
	init {
		require(capacity > 0) { "capacity must be positive" }
	}

	private val times = LongArray(capacity)
	private val kinds = arrayOfNulls<Any>(capacity)
	private val details = arrayOfNulls<Any>(capacity)
	private val threads = arrayOfNulls<String>(capacity)
	private val numbers = LongArray(capacity)
	private var start = 0
	private var count = 0

	@Volatile
	var added = 0L
		private set

	val size: Int @Synchronized get() = count

	@Synchronized
	fun add(now: Long, kind: Any, detail: Any?, thread: String?, number: Long) {
		dropOlderThan(now - maxAgeMs)
		val slot: Int
		if (count == capacity) {
			slot = start
			start = (start + 1) % capacity
		} else {
			slot = (start + count) % capacity
			count++
		}
		times[slot] = now
		kinds[slot] = kind
		details[slot] = detail
		threads[slot] = thread
		numbers[slot] = number
		added++
	}

	private fun dropOlderThan(limit: Long) {
		while (count > 0 && times[start] < limit) {
			kinds[start] = null
			details[start] = null
			threads[start] = null
			start = (start + 1) % capacity
			count--
		}
	}

	data class Event(val time: Long, val kind: Any, val detail: Any?, val thread: String?, val number: Long)

	@Synchronized
	fun snapshot(now: Long): List<Event> {
		dropOlderThan(now - maxAgeMs)
		return List(count) {
			val i = (start + it) % capacity
			Event(times[i], kinds[i]!!, details[i], threads[i], numbers[i])
		}
	}

	@Synchronized
	fun clear() {
		while (count > 0) dropOlderThan(Long.MAX_VALUE)
	}
}
