// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.combat.AbilityCooldowns
import dev.ashtonite365.apex.features.kit.dungeon.MageBeam
import dev.ashtonite365.apex.features.kit.terminals.TerminalPbs
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch24GoldenTest {
	@Test
	fun kit() {
		val S = Batch24Kit
		val out = mutableListOf<Pair<String, String>>()
		for (t in S.titles) out += "type|$t" to "${TerminalPbs.typeOf(t)}"
		val best = mutableMapOf<String, Long>()
		for ((i, c) in S.recordSeq.withIndex()) out += "rec|$i|${c.first}|${c.second}" to "${TerminalPbs.record(best, c.first, c.second)} $best"
		out += "pbData" to "${TerminalPbs.Data()} ${TerminalPbs.Data(mutableMapOf("Panes" to 5L))}"
		out += "abilityKeys" to "${AbilityCooldowns.TConfig.allOptions.keys} ${AbilityCooldowns.TConfig.allOptions.values.map { it.riskLevel }}"
		out += "secondsLeft" to "${AbilityCooldowns.secondsLeft(emptyList())} ${AbilityCooldowns.secondsLeft(listOf("Nope", "Instant Transmission"))}"
		val t0 = MageBeam.Tracker()
		for ((i, c) in S.dirCases.withIndex()) out += "dir|$i|${c.first}|${c.second}" to "${t0.inDirection(c.first, c.second)}"
		for ((si, script) in S.scripts.withIndex()) {
			val t = MageBeam.Tracker()
			val log = StringBuilder()
			for (op in script) when (op) {
				is Batch24Kit.Add -> t.add(op.p)
				is Batch24Kit.Adv -> t.advance(op.duration)
				is Batch24Kit.Seg -> log.append(t.segments(op.from, op.minPoints)).append('#')
			}
			out += "beam|$si" to "$log ${t.tick} ${t.beams.map { "${it.startTick}/${it.lastTick}/${it.points}" }}"
		}
		val n = Golden.verify("batch24-kit", out, minCases = 490)
		assertTrue(n >= 490)
	}
}
