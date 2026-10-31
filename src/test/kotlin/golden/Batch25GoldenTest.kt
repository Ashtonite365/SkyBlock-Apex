// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.chat.PartyCommands
import dev.ashtonite365.apex.features.dungeon.Boulder
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch25GoldenTest {
	@Test
	fun misc() {
		val S = Batch25Misc
		val out = mutableListOf<Pair<String, String>>()
		out += "table" to "${Boulder.table.size} ${Boulder.table.entries.joinToString(";") { "${it.key}=${it.value}" }}"
		out += "minBoulders" to "${Boulder.MIN_BOULDERS}"
		out += "key0" to Boulder.boardKey { (it.x + it.z) % 2 == 0 }
		val calls = ArrayList<String>()
		out += "keyOrder" to Boulder.boardKey { calls.add("${it.x},${it.y},${it.z}"); false } + " " + calls
		for ((i, c) in S.boulderCases.withIndex()) {
			val (corner, air, boulders) = c
			val log = StringBuilder()
			val isAir = { p: BlockPos -> log.append('a'); p in air }
			val isBoulder = { p: BlockPos -> log.append('b'); p in boulders }
			out += "solve|$i" to "${Boulder.solve(corner, Boulder.table, isAir)} ${log.length} ${log.toString().hashCode()}"
			log.setLength(0)
			out += "scan|$i" to "${Boulder.scan(corner, Boulder.table, isAir, isBoulder)} ${log.length} ${log.toString().hashCode()}"
		}
		out += "solveEmpty" to "${Boulder.solve(BlockPos.ZERO, emptyMap()) { true }} ${Boulder.scan(BlockPos.ZERO, emptyMap(), { false }, { true })} ${Boulder.scan(BlockPos.ZERO, emptyMap(), { true }, { true })}"
		out += "partyKeys" to "${PartyCommands.TConfig.allOptions.keys} ${PartyCommands.Channel.entries}"
		for (l in S.chatLines) out += "parse|$l" to "${PartyCommands.parse(l)}"
		for (m in S.emoteMsgs) out += "emote|$m" to "${PartyCommands.replaceEmotes(m)}"
		val n = Golden.verify("batch25-misc", out, minCases = 450)
		assertTrue(n >= 450)
	}
}
