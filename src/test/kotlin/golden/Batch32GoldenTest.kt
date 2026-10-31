// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.garden.GardenPlots
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.bareText
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch32GoldenTest {
	@Test
	fun util() {
		val S = Batch32Util
		val out = mutableListOf<Pair<String, String>>()
		for (x in S.coords) for (z in S.coords) out += "plot|$x|$z" to "${GardenPlots.plotIdAt(x, z)}"
		for (id in S.ids) out += "corner|$id" to "${GardenPlots.plotCorner(id)}|${GardenPlots.plotMiddle(id)}|${GardenPlots.plotMiddle(id, -1.5)}"
		for ((si, script) in S.scripts.withIndex()) {
			var now = 1_000_000L
			var providerCalls = 0
			var changes = 0
			val totalData = ProfitTracker.Data()
			val t = ProfitTracker("T$si", totalProvider = { providerCalls++; totalData }, priceOf = S.price, nowMillis = { now }, onChange = { changes++ })
			for ((oi, op) in script.withIndex()) {
				val (kind, arg, step) = op
				now += step
				when (kind) {
					0 -> t.addItem(S.itemIds[Math.floorMod(arg, S.itemIds.size)], arg.toLong())
					1 -> t.addCoins(arg / 4.0)
					2 -> t.addEvent()
					3 -> t.reset()
					4 -> t.mode = if (t.mode == ProfitTracker.Mode.SESSION) ProfitTracker.Mode.TOTAL else ProfitTracker.Mode.SESSION
					5 -> t.resetSession()
					6 -> t.idleTimeoutMillis = arg * 10_000L
				}
				out += "pt|$si|$oi|$op" to "s=${t.session} t=$totalData calls=$providerCalls ch=$changes cur=${t.current} p=${t.profit()} ph=${t.profitPerHour()} ps=${t.profit(t.session)}"
				if (oi % 7 == 0) out += "ptl|$si|$oi" to t.lines({ "<$it>" }, 3).joinToString(" / ") { it.bareText } + " / " + t.lines().size
			}
		}
		val n = Golden.verify("batch32-util", out, minCases = 2425)
		assertTrue(n >= 2425)
	}
}
