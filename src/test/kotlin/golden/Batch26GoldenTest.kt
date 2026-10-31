// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.puzzles.PuzzleHud
import dev.ashtonite365.apex.features.timers.SlayerTimerState
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(AutoBootstrapExtension::class)
class Batch26GoldenTest {
	@Test
	fun misc() {
		val S = Batch26Misc
		val out = mutableListOf<Pair<String, String>>()
		out += "status" to "${PuzzleHud.Status.entries.map { "${it.name}/${it.icon}/${it.colour}" }}"
		for ((i, l) in S.tabLists.withIndex()) {
			val st = Golden.run { "${PuzzleHud.parse(l)}" }
			out += "parse|$i|$l" to st
			val parsed = runCatching { PuzzleHud.parse(l) }.getOrNull()
			if (parsed != null) {
				out += "render|$i|t" to "${PuzzleHud.render(parsed, true)}"
				out += "render|$i|f" to "${PuzzleHud.render(parsed, false)}"
			}
		}
		for (b in S.bossLines) out += "boss|$b" to "${PuzzleHud.isBossLine(b)}"
		for ((si, script) in S.slayerScripts.withIndex()) {
			val r = Random(9000 + si)
			var now = 1_000_000L
			var calls = 0
			val st = SlayerTimerState { calls++; now += r.nextLong(0, 40_000); now }
			val log = StringBuilder()
			for (op in script) {
				when (op[0]) {
					"sb" -> st.onScoreboard(op[1], op[2]) { n -> if (n.startsWith("Rev")) 45.seconds else null }
					"chat" -> log.append("K=").append(st.onChat(op[1]!!)).append(' ')
					else -> log.append("Q=").append(st.questElapsed()).append('/').append(st.bossElapsed()).append('/').append(st.recentKill()).append('/').append(st.recentKill(5.seconds)).append(' ')
				}
				log.append("[${st.questName},${st.questStartedAt},${st.bossSpawnedAt},${st.pbAtSpawn},${st.lastKill?.pbDelta}] ")
			}
			out += "slayer|$si" to "$log calls=$calls"
		}
		for (d in listOf(0.milliseconds, 1.milliseconds, (-1).milliseconds, 3.seconds, (-2500).milliseconds, kotlin.time.Duration.INFINITE, -kotlin.time.Duration.INFINITE))
			out += "delta|$d" to SlayerTimerState.formatDelta(d) { "<$it>" }
		val n = Golden.verify("batch26-misc", out, minCases = 615)
		assertTrue(n >= 615)
	}
}
