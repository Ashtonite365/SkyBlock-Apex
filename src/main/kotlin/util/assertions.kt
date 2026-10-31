package dev.ashtonite365.apex.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> nonNullOr(value: T?, note: String? = null, fallback: () -> T): T {
	contract { callsInPlace(fallback, AT_MOST_ONCE) }
	if (value != null) return value
	if (note == null) assert(false) else assert(false) { note }
	return fallback()
}

@OptIn(ExperimentalContracts::class)
inline fun trueOr(condition: Boolean, onFalse: () -> Unit) {
	contract { callsInPlace(onFalse, AT_MOST_ONCE) }
	if (condition) return
	assert(false)
	onFalse()
}
