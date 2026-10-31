// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch30Misc {
	private val specials = listOf(0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.0, -1.0, 5.5)
	val ends: List<List<Double>> by lazy {
		val r = Random(3001)
		List(300) { List(4) { if (r.nextInt(8) == 0) specials[r.nextInt(specials.size)] else r.nextInt(-50, 50) / 2.0 } }
	}
	val dist: List<List<Double>> by lazy {
		val r = Random(3002)
		List(400) { List(3) { if (r.nextInt(6) == 0) specials[r.nextInt(specials.size)] else r.nextInt(-40, 40) / 4.0 } }
	}
	val ids = listOf(null, "", "FISHING_ROD", "ROD_OF_LEGENDS", "SHREDDER_ROD", "_ROD", "ROD_", "SPRAYONATOR", "SPRAYONATOR_X", "sprayonator", "fishing_rod", "ROD", "HOE", "CRITTER_CAPSULE", "MASTERFUL_CRITTER_CAPSULE")
	val modes = listOf("foraging_2", "foraging_1", "foraging", "hub", "garden", "the_safari", "safari", "torrhus_x", "xtorrhus", "Foraging_1", "", "dynamic")
	val chat = listOf("PURE REPTILE", "PURE REPTILE! x", " PURE REPTILE", "§bPURE REPTILE", "pure reptile", "PURE REPTIL", "", "xx PURE REPTILE")
}
