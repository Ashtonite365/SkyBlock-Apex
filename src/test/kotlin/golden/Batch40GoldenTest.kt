// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonLogic
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch40GoldenTest {
	@Test
	fun dungeon() {
		val S = Batch40Dungeon
		val L = PlusDungeonLogic
		val out = mutableListOf<Pair<String, String>>()
		out += "consts" to "${L.bloodOpen} ${L.BLOOD_MOVE} ${L.TERRACOTTA_START} ${L.TERRACOTTA_END}"
		for (t in PlusDungeonLogic.FilterType.entries) out += "ft|$t" to "${t.patterns.toList()} ${t.regexes.map { it.pattern }}"
		for (line in S.lines) {
			out += "line|$line" to "${L.puzzleFailer(line)}|${L.isBossMessage(line)}|${L.floorBossOf(line)}|${L.filterType(line)}|${L.isDeath(line)}|" +
				"${L.isMilestone(line)}|${L.enteredPlayer(line)}|${L.isChestsSpawned(line)}"
			for (p in S.players) out += "copilot|$line|$p" to "${L.copilotStep(line, p)}"
		}
		for (m in S.moveMillis) {
			out += "blood|$m" to "${L.bloodMovePrediction(m.milliseconds)}"
			for (lag in S.lagMillis) out += "blood|$m|$lag" to "${L.bloodMovePrediction(m.milliseconds, lag.milliseconds)}"
		}
		for (t in S.teammates) out += "mate|$t" to L.parseTeammate(t).let { "$it ${it?.isLow}" }
		for (t in S.tabs) out += "tab|$t" to "${L.puzzleCount(t)}"
		for (b in S.boards) out += "board|$b" to "${L.floorOf(b)}"
		for (d in S.deaths) out += "death|$d" to L.deathColour(d)
		val n = Golden.verify("batch40-dungeon", out, minCases = 1481)
		assertTrue(n >= 1481)
	}
}
