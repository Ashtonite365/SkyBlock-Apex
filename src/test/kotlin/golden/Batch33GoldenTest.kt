// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.rift.PlusRiftMotes
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch33GoldenTest {
	@Test
	fun rift() {
		val S = Batch33Rift
		val out = mutableListOf<Pair<String, String>>()
		for ((i, l) in S.lores.withIndex()) out += "sell|$i|$l" to Golden.run { "${PlusRiftMotes.sellPriceOf(l)}" }
		for ((i, b) in S.boards.withIndex()) out += "motes|$i|$b" to Golden.run { "${PlusRiftMotes.motesOf(b)}" }
		for (t in S.times) out += "time|$t" to Golden.run { "${PlusRiftMotes.parseTime(t)}" }
		for (n in S.numbers) out += "num|$n" to "${PlusRiftMotes.parseNumber(n)}"
		val n = Golden.verify("batch33-rift", out, minCases = 315)
		assertTrue(n >= 315)
	}
}
