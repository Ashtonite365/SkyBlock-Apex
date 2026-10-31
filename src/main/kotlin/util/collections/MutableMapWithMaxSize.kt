// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.collections

import dev.ashtonite365.apex.util.IdentityCharacteristics

private class EvictingMap<K, V>(private val maxEntries: Int) : LinkedHashMap<K, V>() {
	private val full get() = size > maxEntries

	override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = full
}

fun <K, V> lruMap(capacity: Int): MutableMap<K, V> = EvictingMap(capacity)

@PublishedApi
internal val NULL_RESULT: Any = Any()

inline fun <T, R> MutableMap<T, Any>.getOrComputeNullable(
	key: T,
	compute: () -> R,
): R {
	val held = this[key]
	if (held != null) {
		@Suppress("UNCHECKED_CAST")
		return (if (held === NULL_RESULT) null else held) as R
	}
	val answer = compute()
	this[key] = answer ?: NULL_RESULT
	return answer
}

fun <T, R> ((T) -> R).rememberRecent(capacity: Int): (T) -> R {
	val underlying = this
	val seen = lruMap<T, Any>(capacity)
	return { argument -> seen.getOrComputeNullable(argument) { underlying(argument) } }
}

fun <T, R> ((T) -> R).rememberByIdentity(capacity: Int): (T) -> R {
	val underlying = this
	val boxed = { key: IdentityCharacteristics<T> -> underlying(key.value) }.rememberRecent(capacity)
	return { argument -> boxed(IdentityCharacteristics(argument)) }
}
