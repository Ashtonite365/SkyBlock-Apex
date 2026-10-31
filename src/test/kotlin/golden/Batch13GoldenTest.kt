// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenAnitaFortune
import dev.ashtonite365.apex.features.garden.GardenCropRanking
import dev.ashtonite365.apex.features.garden.GardenCropSpeed
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch13GoldenTest {
	@Test
	fun garden() {
		val G = Batch13Garden
		val out = mutableListOf<Pair<String, String>>()
		for (c in Crop.entries) for (b in G.bpsList) for (f in G.ffList) out += "cph|$c|$b|$f" to "${GardenCropRanking.cropsPerHour(c, b, f)}"
		for ((i, pm) in G.priceMaps.withIndex()) out += "money|$i" to Golden.run { GardenCropRanking.moneyRanking(G.bpsList[i % 5], G.ffList[i % 4], pm::get).toString() }
		for ((i, lm) in G.leftMaps.withIndex()) out += "time|$i" to Golden.run { GardenCropRanking.timeRanking(G.bpsList[i % 5], G.ffList[(i / 5) % 4], lm::get).toString() }
		for ((i, h) in G.hours.withIndex()) out += "hours|$i" to Golden.run { GardenCropRanking.formatHours(h) }
		for ((i, lore) in G.anitaLores.withIndex()) for (t in listOf(i % 60, (i * 7 + 3) % 60))
			out += "anita|$i|$t" to Golden.run { GardenAnitaFortune.costToMax(lore, G.priceTables[t]).toString() }
		out += "levels" to Golden.run { GardenAnitaFortune.levelPrices().toString() }
		for ((i, bs) in G.breakSets.withIndex()) for (w in G.windows) out += "bps|$i|$w" to "${GardenCropSpeed.bps(bs.first, bs.second, w)}"
		for ((i, bs) in G.breakSets.withIndex()) out += "bpsdef|$i" to "${GardenCropSpeed.bps(bs.first, bs.second)}"
		out += "speeds" to GardenCropSpeed.optimalSpeeds.entries.joinToString()
		val n = Golden.verify("batch13-garden", out, minCases = 1800)
		assertTrue(n >= 1800)
		assertTrue(out.count { it.first.startsWith("money|") && it.second != "[]" } > 100, "money rankings")
		assertTrue(out.count { it.first.startsWith("anita|") && it.second != "null" } > 150, "anita costs")
	}
}
