// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch32Util {
	val coords: List<Double> by lazy {
		val r = Random(3201)
		listOf(-240.0, 240.0, -240.0001, 240.0001, -0.0, 0.0, Double.NaN, -144.0, -48.0, 48.0, 144.0, 143.999, Double.NEGATIVE_INFINITY) + List(25) { r.nextDouble(-260.0, 260.0) }
	}
	val ids = (-2..30).toList()

	val scripts: List<List<Triple<Int, Int, Long>>> by lazy {
		val r = Random(3202)
		List(25) { List(r.nextInt(5, 60)) { Triple(r.nextInt(14).let { if (it > 6) it % 3 else it }, r.nextInt(-3, 50), listOf(0L, 1L, 1000L, 119_999L, 120_000L, 120_001L, 3_600_000L, -5L)[r.nextInt(8)]) } }
	}
	val itemIds = listOf("A", "B", "C", "NONE", "NAN", "NEG")
	val price: (String) -> Double? = { when (it) { "A" -> 10.0; "B" -> 2.5; "C" -> 1e9; "NAN" -> Double.NaN; "NEG" -> -3.0; else -> null } }
}
