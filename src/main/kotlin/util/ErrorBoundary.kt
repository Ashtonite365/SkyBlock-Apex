package dev.ashtonite365.apex.util

fun <T> errorBoundary(block: () -> T): T? = block.invoke()
