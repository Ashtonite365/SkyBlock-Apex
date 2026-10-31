package dev.ashtonite365.apex.util

class IdentityCharacteristics<T>(val value: T) {
	override fun hashCode(): Int = System.identityHashCode(value)

	override fun equals(other: Any?): Boolean =
		other is IdentityCharacteristics<*> && other.value === value
}
