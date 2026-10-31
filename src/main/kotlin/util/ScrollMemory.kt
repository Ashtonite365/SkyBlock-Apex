// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object ScrollMemory {
	const val SETTLE_MS = 1000L

	private val offsets = java.util.concurrent.ConcurrentHashMap<String, Int>()

	@JvmStatic
	var clock: () -> Long = System::currentTimeMillis

	@JvmStatic
	fun get(key: String): Int = offsets[key] ?: 0

	@JvmStatic
	fun put(key: String, offset: Int) {
		if (offset <= 0) offsets.remove(key) else offsets[key] = offset
	}

	@JvmStatic
	fun has(key: String) = offsets.containsKey(key)

	fun clear() = offsets.clear()

	@JvmStatic
	fun clamp(offset: Int, max: Int) = offset.coerceIn(0, maxOf(0, max))

	class Remembered(private val key: () -> String, private val settleMs: Long = 0) {
		private val opened = clock()
		private var shownKey: String? = null
		private var shown = 0

		var value: Int
			get() {
				val k = key()
				if (k != shownKey) { shownKey = k; shown = get(k) }
				return shown
			}
			set(v) {
				val k = key()

				if (v < get(k) && clock() - opened < settleMs) { shownKey = k; shown = get(k); return }
				shownKey = k; shown = v
				put(k, v)
			}

		fun scrolledTo(v: Int) { val k = key(); shownKey = k; shown = v; put(k, v) }
	}

	fun int(settle: Boolean = false, key: () -> String) = object : ReadWriteProperty<Any?, Int> {
		val r = Remembered(key, if (settle) SETTLE_MS else 0)
		override fun getValue(thisRef: Any?, property: KProperty<*>): Int = r.value
		override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { r.value = value }
	}

	fun float(settle: Boolean = false, key: () -> String) = object : ReadWriteProperty<Any?, Float> {
		val r = Remembered(key, if (settle) SETTLE_MS else 0)
		private var frac = 0f
		override fun getValue(thisRef: Any?, property: KProperty<*>): Float = r.value + frac
		override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
			val i = value.toInt(); frac = value - i; r.value = i
		}
	}
}
