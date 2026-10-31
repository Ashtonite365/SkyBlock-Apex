// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.enchanting.SuperpairsFlipDetector
import dev.ashtonite365.apex.features.plus.commands.PlusCommands
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch36GoldenTest {
	@Test
	fun superpairs() {
		val S = Batch36Superpairs
		val out = mutableListOf<Pair<String, String>>()
		for (t in S.titles) out += "grid|$t" to "${SuperpairsFlipDetector.gridSlots(t)}"
		for (n in S.names) out += "hidden|$n" to "${SuperpairsFlipDetector.isHiddenName(n)}"
		for (l in S.lores) out += "book|$l" to "${SuperpairsFlipDetector.ultraRareBook(l)}"
		for ((si, script) in S.scripts.withIndex()) {
			val d = SuperpairsFlipDetector()
			for ((i, step) in script.withIndex()) {
				if (i == 15) d.reset()
				out += "flip|$si|$i|${step.first}|${step.second}" to "${d.update(step.first, step.second)}"
			}
		}
		val r = kotlin.random.Random(3602)
		PlusCommands.handleChatLine("Zed has invited you to join their party!")
		out += "inv|start" to "${PlusCommands.lastInviter}"
		for (i in 0 until 200) {
			val line = S.chat[r.nextInt(S.chat.size)]
			PlusCommands.handleChatLine(line)
			out += "inv|$i|$line" to "${PlusCommands.lastInviter}"
		}
		for (c in S.commands) {
			out += "accept|$c" to "${PlusCommands.isBareAccept(c)}"
			for (pi in listOf(true, false)) out += "warp|$c|$pi" to "${PlusCommands.shortWarpFor(c, S.warps, pi)}"
		}
		val n = Golden.verify("batch36-superpairs", out, minCases = 1284)
		assertTrue(n >= 1284)
	}
}
