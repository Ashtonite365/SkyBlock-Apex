// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.collections

fun <T, R> List<T>.lastNotNullOfOrNull(func: (T) -> R?): R? =
	asReversed().firstNotNullOfOrNull(func)
