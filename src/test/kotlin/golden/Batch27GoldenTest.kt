// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenMiscTrackers
import dev.ashtonite365.apex.features.plus.inventory.PlusInventorySmall
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.matches
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch27GoldenTest {
	@Test
	fun sh() {
		val S = Batch27Sh
		val out = mutableListOf<Pair<String, String>>()
		val g = GardenMiscTrackers

		fun fresh() {
			g.gardenLevel = null; g.sessionXp = 0.0; g.capsules.clear(); g.inFever = false; g.feverCount = 0
			for (t in listOf(g.feverTracker, g.rareTracker)) { t.resetSession(); t.resetTotal() }
		}
		fresh()
		try {
			for ((i, m) in S.gardenChat.withIndex()) {
				val res = Golden.run { "${g.handle(m, S.lookup)}" }
				out += "garden|$i|$m" to "$res xp=${g.sessionXp} caps=${g.capsules} fever=${g.inFever}/${g.feverCount} fItems=${g.feverTracker.current.items} rItems=${g.rareTracker.current.items} lvl=${g.gardenLevel}"
			}
		} finally { fresh() }
		out += "invKeys" to "${PlusInventorySmall.TConfig.allOptions.keys} ${PlusInventorySmall.EVOLVE_SECONDS}"
		for (m in S.outbid) out += "outbid|$m" to "${PlusInventorySmall.outbidPattern.matches(m)} ${PlusInventorySmall.outbidPattern.pattern()}"
		for (m in S.donated) out += "donated|$m" to "${PlusInventorySmall.allDonatedPattern.matches(m)} ${PlusInventorySmall.allDonatedPattern.pattern()}"
		for (id in S.ids) out += "attr|$id" to PlusInventorySmall.secondsAttribute(id)
		for (h in S.held) out += "left|$h" to "${PlusInventorySmall.timeLeft(h)}"
		val n = Golden.verify("batch27-sh", out, minCases = 415)
		assertTrue(n >= 415)
	}
}
