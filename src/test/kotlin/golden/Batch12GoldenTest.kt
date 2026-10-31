// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenAngles
import dev.ashtonite365.apex.features.garden.GardenFortune
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenSensitivity
import dev.ashtonite365.apex.features.garden.GardenShops
import dev.ashtonite365.apex.features.garden.GardenVisitors
import dev.ashtonite365.apex.features.garden.PestTrailMath
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch12GoldenTest {
	@Test
	fun garden() {
		val G = Batch12Garden
		val out = mutableListOf<Pair<String, String>>()
		val crops = listOf<Crop?>(null) + Crop.entries
		for ((i, tab) in G.tabs.withIndex()) {
			for (c in crops) out += "ff|$i|$c" to Golden.run { "${GardenFortune.tabFortune(tab, c)}" }
			out += "next|$i" to Golden.run { "${GardenVisitors.parseNextVisitor(tab)}" }
		}
		for (c in Crop.entries) for (f in G.ffs) for (b in G.bps) out += "contest|$c|$f|$b" to Golden.run { "${GardenFortune.contestCrops(f, c, b)}" }
		for (c in Crop.entries) out += "contestdef|$c" to "${GardenFortune.contestCrops(123.0, c)}"
		for (s in G.sensitivities) for (d in G.divisors) out += "sens|$s|$d" to "${GardenSensitivity.reduced(s, d)}"
		for ((fi, f) in G.floats.withIndex()) for (p in G.precisions) out += "fmt|$fi|$p" to Golden.run { GardenAngles.format(f, p) }
		for ((i, yp) in G.yawPairs.withIndex()) out += "yaw|$i" to "${GardenAngles.yawDiff(yp.first, yp.second)}"
		for ((fi, f) in G.floats.withIndex()) for ((gi, g) in G.floats.withIndex()) out += "yawf|$fi|$gi" to "${GardenAngles.yawDiff(f, g)}"
		out += "optimal" to GardenAngles.optimal.entries.joinToString { "${it.key}=${it.value}" }
		out += "rare" to GardenVisitors.rareRewards.joinToString("|")
		for ((i, l) in G.lores.withIndex()) out += "reward|$i" to Golden.run { GardenVisitors.rareRewardsIn(l).toString() }
		for ((i, l) in G.costLores.withIndex()) out += "cost|$i" to Golden.run { "${GardenShops.bronzeCost(l)} ${GardenShops.copperCost(l)}" }
		for ((i, l) in G.costLores.withIndex()) out += "bronze|$i" to Golden.run { "${GardenShops.bronzeCost(l)}" }
		for ((i, l) in G.costLores.withIndex()) out += "copper|$i" to Golden.run { "${GardenShops.copperCost(l)}" }
		for ((i, t) in G.trails.withIndex()) out += "trail|$i" to Golden.run { G.v(PestTrailMath.solve(t)) }
		for (s in G.slopes) out += "slope|$s" to "${PestTrailMath.lookPitchFromSlope(s)} ${PestTrailMath.controlPointDistance(PestTrailMath.lookPitchFromSlope(s))}"
		for ((i, d) in G.dirs.withIndex()) out += "dir|$i" to "${PestTrailMath.derivativePitch(d)}"
		out += "min" to "${PestTrailMath.MIN_POINTS}"
		Golden.verify("batch12-garden", out, minCases = 6000)
		assertTrue(out.count { it.first.startsWith("trail|") && it.second != "null" } > 100, "fitted trails")
		assertTrue(out.count { it.first.startsWith("ff|") && it.second != "null" } > 1000, "fortunes")
	}
}
