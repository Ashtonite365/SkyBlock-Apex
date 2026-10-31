// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenContests
import dev.ashtonite365.apex.features.plus.events.FrozenTreasureTracker
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.ProfitTracker
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch29GoldenTest {
	@Test
	fun misc() {
		val S = Batch29Misc
		val out = mutableListOf<Pair<String, String>>()
		for ((i, l) in S.lores.withIndex()) out += "brackets|$i" to Golden.run { "${GardenContests.parseBrackets(l)}" }
		for ((i, t) in S.tabs.withIndex()) out += "active|$i" to Golden.run { "${GardenContests.parseActive(t)}" }
		for ((b, m) in S.summaries) out += "summary|$b|$m" to Golden.run { GardenContests.summary(b, m) }
		for (c in S.chat) out += "parse|$c" to "${FrozenTreasureTracker.parse(c)}"
		for ((i, m) in S.itemMaps.withIndex()) out += "ice|$i" to "${FrozenTreasureTracker.estimatedIce(ProfitTracker.Data(items = m.toMutableMap()))}"
		out += "treasures" to FrozenTreasureTracker.Treasure.entries.joinToString { "${it.name}/${it.id}/${it.display}/${it.amount}/${it.ice}" }
		val n = Golden.verify("batch29-misc", out, minCases = 365)
		assertTrue(n >= 365)
	}
}
