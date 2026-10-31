// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.collections

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import dev.ashtonite365.apex.features.debug.DebugLogger

open class WeakKeyCache<K, X, V>(val name: String) {
	private val entries = HashMap<KeyRef, V>()
	private val stale = ReferenceQueue<K>()

	init {
		allInstances.add(this)
	}

	open inner class KeyRef(key: K, val extra: X) : WeakReference<K>(key, stale) {
		val hashCode = System.identityHashCode(key) * 31 + extra.hashCode()

		open fun dropOnCollect(): Boolean = true

		override fun hashCode(): Int = hashCode

		override fun equals(other: Any?): Boolean {
			if (other === this) return true
			if (other !is WeakKeyCache<*, *, *>.KeyRef) return false
			if (other.hashCode != hashCode || other.extra != extra) return false
			val mine = get() ?: return false
			return mine === other.get()
		}
	}

	open fun refFor(key: K, extra: X): KeyRef = KeyRef(key, extra)

	fun sweep() {
		var polled = 0
		var dropped = 0
		while (true) {
			@Suppress("UNCHECKED_CAST")
			val ref = stale.poll() as WeakKeyCache<K, X, V>.KeyRef? ?: break
			polled++
			if (ref.dropOnCollect() && entries.remove(ref) != null) dropped++
		}
		if (polled > 0) log.log { "Cleared $dropped/$polled references from queue" }
	}

	val size: Int
		get() {
			sweep()
			return entries.size
		}

	operator fun get(key: K, extra: X): V? {
		sweep()
		return entries[refFor(key, extra)]
	}

	fun put(key: K, extra: X, value: V) {
		sweep()
		entries[refFor(key, extra)] = value
	}

	fun getOrPut(key: K, extra: X, compute: (K, X) -> V): V {
		sweep()
		val ref = refFor(key, extra)
		@Suppress("UNCHECKED_CAST")
		if (entries.containsKey(ref)) return entries[ref] as V
		val made = compute(key, extra)
		entries[ref] = made
		return made
	}

	fun clear() {
		sweep()
		entries.clear()
	}

	interface CachedLookup {
		val cache: WeakKeyCache<*, *, *>

		data class Single<K, V>(override val cache: WeakKeyCache<K, Unit, V>, val compute: (K) -> V) : CachedLookup, (K) -> V {
			override fun invoke(key: K): V = cache.getOrPut(key, Unit) { k, _ -> compute(k) }
		}

		data class Paired<K, X, V>(override val cache: WeakKeyCache<K, X, V>, val compute: (K, X) -> V) :
			CachedLookup, (K, X) -> V {
			override fun invoke(key: K, extra: X): V = cache.getOrPut(key, extra, compute)
		}
	}

	companion object Registry {
		private val log = DebugLogger("WeakCache")
		val allInstances = InstanceList<WeakKeyCache<*, *, *>>("WeakCaches")

		fun <K, V> perKey(name: String, compute: (K) -> V): CachedLookup.Single<K, V> =
			CachedLookup.Single(WeakKeyCache(name), compute)

		fun <K, X, V> perKey(name: String, compute: (K, X) -> V): CachedLookup.Paired<K, X, V> =
			CachedLookup.Paired(WeakKeyCache(name), compute)

		fun <K, X, V> passThrough(name: String, compute: (K, X) -> V): (K, X) -> V = { key, extra -> compute(key, extra) }
	}
}
