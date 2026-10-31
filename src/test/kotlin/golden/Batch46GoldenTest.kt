// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenPests
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.ProfitTracker
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch46GoldenTest {
	@Test
	fun pests() {
		val S = Batch46Pests
		val P = GardenPests
		val out = mutableListOf<Pair<String, String>>()
		for ((i, l) in (S.fixed + S.randomLines).withIndex())
			out += "line|$i" to "${P.parseSpawnPlot(l)} | ${P.parseKill(l)} | ${P.parseDrop(l)}"
		for ((i, t) in S.tabs.withIndex()) out += "tab|$i" to "${P.parseInfested(t)} ${P.parseAlive(t)}"
		for (a in S.agos) out += "ago|$a" to P.formatAgo(a)
		for (b in S.prices) for (n in S.prices) out += "price|$b|$n" to "${P.sellPrice(b, n)}"
		for (items in listOf(mutableMapOf(), mutableMapOf("A" to 0L))) for (coins in listOf(0.0, -0.0, 1.0, Double.NaN)) for (ev in listOf(0L, 1L, -1L))
			out += "counted|${items.size}|$coins|$ev" to "${P.hasCounted(ProfitTracker.Data(items, coins, 0L, ev))}"
		val n = Golden.verify("batch46-pests", out, minCases = 3213)
		assertTrue(n >= 3213)
	}
}
