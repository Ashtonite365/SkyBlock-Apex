// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.misc.CompactTabList
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch47GoldenTest {
	@Test
	fun tab() {
		val S = Batch47Tab
		val T = CompactTabList
		val out = mutableListOf<Pair<String, String>>()
		out += "enums" to "${CompactTabList.PlayerSort.entries.map { it.serializedName }} ${CompactTabList.TabBackground.entries.map { it.serializedName }}"
		for ((i, l) in S.lines.withIndex()) {
			out += "line|$i" to Golden.run { "${T.parsePlayer(l)}" } + " | " +
				listOf(false, true).flatMap { a -> listOf(false, true).map { b -> T.cleanPlayerText(l, a, b) } }.joinToString(" | ")
		}
		for ((i, sec) in S.sections.withIndex()) {
			val comps = sec.map { Component.literal(it) }
			for (sort in CompactTabList.PlayerSort.entries) for (rev in listOf(false, true)) {
				out += "sort|$i|$sort|$rev" to Golden.run {
					val res = T.sortPlayers(comps, sort, rev)
					"${res === comps} ${res.map { c -> comps.indexOfFirst { it === c } }} ${res.map { it.bareText }}"
				}
			}
		}
		val n = Golden.verify("batch47-tab", out, minCases = 6691)
		assertTrue(n >= 6691)
	}
}
