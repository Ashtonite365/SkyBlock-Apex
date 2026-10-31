// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch19Sh {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]
	val exprs: List<String> by lazy {
		val r = Random(1901)
		val fixed = listOf("", "1", "1+2", "2x3", "2*3+4", "2+3*4", "(2+3)*4", "10/4", "10/0", "-10/0", "0/0", "-3", "--3", "2--3", "2-+3", "(1", "1)", "()", "1.5*2", "1.2.3",
			".5", "5.", ".", "1 + 2 x 3", "((((7))))", "-(2+3)", "2*-3", "1/3*3", "99999999999*99999999999", "9e3", "1e", "abc", "1+", "+1", "x", "3x", "(2)(3)", "7 / 2", "-7/2",
			"1e308*10", "(" .repeat(200) + "1" + ")".repeat(200), "(" .repeat(50) + "1", "2*(3+(4-5)*6)/7", "100-99-1", "8/2/2", "0.1+0.2")
		val atoms = listOf("1", "2", "3", "7", "12", "0", "4.5", "(", ")", "+", "-", "x", "*", "/", " ")
		fixed + List(400) { buildString { repeat(r.nextInt(1, 12)) { append(pick(r, atoms)) } } } +
			List(200) { buildString { append(r.nextInt(0, 100)); repeat(r.nextInt(0, 4)) { append(pick(r, listOf("+", "-", "x", "*", "/"))); append(r.nextInt(0, 100)) } } }
	}
	val elapsedMs = listOf(Long.MIN_VALUE / 4, -1L, 0L, 1L, 59_999L, 300_000L, 359_999L, 360_000L, 360_001L, 3_600_000L)
	val costLores: List<List<String>> by lazy {
		val r = Random(1902)
		val pool = listOf("Cost", " Cost ", "cost", "Agatha's Coupon x5", "Agatha's Coupon x1,000", "Enchanted Oak Wood x64", "Coins x", "Fig Log x99999999999999999999", "Fig Log x,", "Something",
			"", "  ", "Click to trade!", "Name  x3 ", "a x1 x2", "Cost x2", "Miria's Coupon")
		List(300) { List(r.nextInt(0, 8)) { pick(r, pool) } }
	}
	val profitCases: List<Pair<Double, List<Triple<Boolean, Long, Double?>>>> by lazy {
		val r = Random(1903)
		val prices = listOf(null, 0.0, 1.5, 100.0, 1e9, Double.NaN, -5.0)
		listOf(0.0 to emptyList<Triple<Boolean, Long, Double?>>(), 10.0 to listOf(Triple(true, 0L, null)), 10.0 to listOf(Triple(true, -1L, null), Triple(true, 2L, 3.0))) +
			List(200) { pick(r, listOf(0.0, 1000.0, 1e7, -1.0, Double.NaN)) to List(r.nextInt(0, 5)) { Triple(r.nextBoolean(), pick(r, listOf(0L, 1L, 3L, 64L, 1000L, -2L)), pick(r, prices)) } }
	}
	val romans = listOf("", "I", "II", "IV", "V", "IX", "X", "XL", "L", "XLIX", "IL", "IIII", "VX", "LL", "IIV", "M", "i", "XIV", "XXXIX", "IXI", "VIV", "XLX", "C")
	val itemNames = listOf("[Lvl 100] Golden Dragon", "[Lvl 5]", "[Lvl x] Pet", "Snow Minion XI", "Snow Minion IIII", "Wheat Minion I ", "Speed Potion III", "Healing V Potion", "Potion", "Potion IV",
		"Strength Potion", "Mana II Potion", "Master Skull - Tier 5", "Master Skull - Tier IV", "Master Skull - Tier", "New Year Cake (Year 400)", "New Year Cake (Year )", "Kuudra Key",
		"Infernal Kuudra Key", "Kuudra Key X", "Farming 50", "Mining XL", "Cooking 5", "Taming 0", "Hyperion ✪✪✪✪✪", "Hyperion ✪✪✪✪✪➌", "Hyperion ➎", "Hyperion ➋➎", "✪", "", "Stone",
		"[Lvl 7] Minion II", "Combat XIIV", "Hunting IIII", "Speed Potion", "Potion XL Potion")
	val optionKeys = listOf("stars", "minion-tier", "pet-level", "potion-level", "master-skull-tier", "new-year-cake-year", "kuudra-key-tier", "skill-level")
}
