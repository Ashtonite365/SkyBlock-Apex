// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.inventory.StackSizeNumbers
import dev.ashtonite365.apex.features.plus.events.GreatSpook
import dev.ashtonite365.apex.features.plus.foraging.StarlynCouponProfit
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch19GoldenTest {
	@Suppress("UNCHECKED_CAST")
	private fun opt(key: String) = StackSizeNumbers.TConfig.allOptions[key] as ManagedOption<Boolean>

	@Test
	fun sh() {
		val S = Batch19Sh
		StackSizeNumbers.TConfig.explicitDefaultLoad()
		val out = mutableListOf<Pair<String, String>>()
		for (e in S.exprs) out += "eval|$e" to Golden.run { "${GreatSpook.evaluate(e)}" }
		for (ms in S.elapsedMs) out += "rem|$ms" to "${GreatSpook.remaining(ms.milliseconds)}"
		out += "remInf" to "${GreatSpook.remaining(Duration.INFINITE)} ${GreatSpook.remaining(-Duration.INFINITE)}"
		out += "shops" to "${StarlynCouponProfit.SHOPS}"
		for (s in -2..56) out += "slot|$s" to "${StarlynCouponProfit.isShopSlot(s)}"
		for ((i, l) in S.costLores.withIndex()) out += "costs|$i|$l" to Golden.run { "${StarlynCouponProfit.readCosts(l)}" }
		for ((i, c) in S.profitCases.withIndex()) out += "profit|$i|$c" to Golden.run { "${StarlynCouponProfit.profitPerCoupon(c.first, c.second)}" }
		out += "row" to "${StarlynCouponProfit.Row("a", 1.0)}"
		for (r in S.romans) out += "roman|$r" to "${StackSizeNumbers.romanToInt(r)}"
		val saved = S.optionKeys.associateWith { opt(it).get() }
		try {
			for (mask in listOf(-1, 0) + S.optionKeys.indices.map { 1 shl it } + listOf(0b11110000, 0b00001111, 0b10101010)) {
				for ((bit, key) in S.optionKeys.withIndex()) opt(key).set(mask == -1 && saved.getValue(key) || mask != -1 && (mask shr bit) and 1 == 1)
				for (n in S.itemNames) out += "num|$mask|$n" to Golden.run { "${StackSizeNumbers.numberFor(n)}" }
			}
		} finally {
			for (key in S.optionKeys) opt(key).set(saved.getValue(key))
		}
		val n = Golden.verify("batch19-sh", out, minCases = 1700)
		assertTrue(n >= 1700)
	}
}
