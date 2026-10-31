// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenLanes
import dev.ashtonite365.apex.features.plus.hunting.PlusHunting
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.SkyBlockIsland
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch30GoldenTest {
	@Test
	fun misc() {
		val S = Batch30Misc
		val out = mutableListOf<Pair<String, String>>()
		for ((i, e) in S.ends.withIndex()) out += "lane|$i|$e" to "${GardenLanes.laneFrom(e[0], e[1], e[2], e[3])}"
		for ((i, d) in S.dist.withIndex()) {
			val lane = GardenLanes.laneFrom(d[0], 0.0, d[1], 0.0)
			for (v in listOf(0.0, -0.0, 1.0, -1.0, Double.NaN, d[2])) out += "dist|$i|$v" to "${GardenLanes.distanceToEnd(lane, d[2], v)}"
			out += "distRaw|$i" to "${GardenLanes.distanceToEnd(GardenLanes.Lane(false, d[1], d[0]), d[2], d[0])}"
		}
		for (id in S.ids) {
			out += "nobreak|$id" to "${GardenLanes.isNoBreakItem(id)}"
			out += "capsule|$id|t" to "${PlusHunting.hideCapsule(id, true)}"
			out += "capsule|$id|f" to "${PlusHunting.hideCapsule(id, false)}"
		}
		out += "island|null" to "${PlusHunting.isHuntingIsland(null)}"
		for (m in S.modes) out += "island|$m" to "${PlusHunting.isHuntingIsland(SkyBlockIsland.ofMode(m))}"
		for (c in S.chat) out += "reptile|$c" to "${PlusHunting.isPureReptile(c)}"
		out += "biomes" to "${PlusHunting.biomes}"
		val n = Golden.verify("batch30-misc", out, minCases = 3150)
		assertTrue(n >= 3150)
	}
}
