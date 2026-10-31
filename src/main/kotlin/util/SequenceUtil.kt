package dev.ashtonite365.apex.util

fun <T : Any> T.iterate(iterator: (T) -> T?): Sequence<T> = generateSequence(this, iterator)
