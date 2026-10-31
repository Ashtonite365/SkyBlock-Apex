// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.misc.DiscordStatus
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch43GoldenTest {
	@Test
	fun discord() {
		val S = Batch43Discord
		val out = mutableListOf<Pair<String, String>>()
		out += "win" to DiscordStatus.candidatePaths({ error("no env on windows") }, true).toString()
		for ((i, env) in S.envs.withIndex()) {
			val asked = mutableListOf<String>()
			val paths = DiscordStatus.candidatePaths({ asked += it; env[it] }, false)
			out += "paths|$i" to "${asked} ${paths.size} ${paths.hashCode()} ${paths.take(12)} ${paths.filterIndexed { k, _ -> k % 10 == 0 }}"
		}
		for (op in S.opcodes) for ((j, s) in S.jsons.withIndex()) out += "frame|$op|$j" to DiscordStatus.frame(op, s).joinToString(",")
		for (pid in S.pids) for (d in S.texts) for (st in S.texts) for (start in S.starts)
			out += "act|$pid|$d|$st|$start" to DiscordStatus.activityJson(pid, d, st, start, "n-1").toString()
		val n = Golden.verify("batch43-discord", out, minCases = 2075)
		assertTrue(n >= 2075)
	}
}
