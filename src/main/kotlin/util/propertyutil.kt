package dev.ashtonite365.apex.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T, V, M> ReadOnlyProperty<T, V>.map(mapper: (V) -> M): ReadOnlyProperty<T, M> {
	val source = this
	return object : ReadOnlyProperty<T, M> {
		override fun getValue(thisRef: T, property: KProperty<*>): M = mapper(source.getValue(thisRef, property))
	}
}
