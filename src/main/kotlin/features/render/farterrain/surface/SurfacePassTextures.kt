/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.render.farterrain.surface

class SurfacePassTextures<K, V : Any>(private val keys: List<K>) {
	private val views = arrayOfNulls<Any>(keys.size)
	private var ready = false

	fun prepare(lookup: (K) -> V?): Boolean {
		ready = false
		for (i in keys.indices) views[i] = lookup(keys[i]) ?: return false
		ready = true
		return true
	}

	@Suppress("UNCHECKED_CAST")
	fun inPass(key: K): V? {
		if (!ready) return null
		val i = keys.indexOf(key)
		return if (i < 0) null else views[i] as V?
	}

	fun clear() { ready = false; views.fill(null) }
}

class SurfaceErrorLog(private val max: Int = 64) {
	private val seen = HashSet<String>()

	fun causeKey(t: Throwable): String {
		val sb = StringBuilder()
		var c: Throwable? = t
		var depth = 0
		while (c != null && depth < 8) {
			val top = c.stackTrace.firstOrNull()
			sb.append(c.javaClass.name).append(':').append(c.message).append('@').append(top?.className).append('.').append(top?.methodName).append(':').append(top?.lineNumber).append('|')
			c = if (c.cause === c) null else c.cause
			depth++
		}
		return sb.toString()
	}

	@Synchronized
	fun firstTime(t: Throwable): Boolean {
		if (seen.size >= max) return false
		return seen.add(causeKey(t))
	}
}
