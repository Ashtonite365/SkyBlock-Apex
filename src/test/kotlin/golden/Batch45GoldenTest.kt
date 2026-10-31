// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.mining.MiningExtras
import dev.ashtonite365.apex.features.plus.mining.MiningPlusPorts
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch45GoldenTest {
	@Test
	fun mining() {
		val S = Batch45Mining
		val P = MiningPlusPorts
		val out = mutableListOf<Pair<String, String>>()
		for ((i, l) in (S.fixed + S.randomLines).withIndex()) {
			out += "line|$i" to listOf(
				Golden.run { "${P.parseGhostMf(l)}" }, "${P.parseKillCombo(l)}", "${P.parseLootLine(l)}", "${P.corpseType(l)}", "${P.notification(l)}",
			).joinToString(" | ")
		}
		val cfg = MiningExtras.TConfig
		if (cfg.sortedOptions.any { it._actualValue == null }) cfg.explicitDefaultLoad()
		@Suppress("UNCHECKED_CAST")
		val corpseOpt = cfg.sortedOptions.single { it.propertyName == "corpse-loot" } as dev.ashtonite365.apex.gui.config.ManagedOption<Boolean>
		val before = corpseOpt._actualValue
		try {
			for (corpse in listOf(false, true)) {
				corpseOpt._actualValue = corpse
				P.nucleus.reset(); P.excavator.reset(); P.corpses.reset()
				for ((si, st) in S.streams.withIndex()) {
					for ((k, l) in st.withIndex()) {
						val res = try { P.onChat(ProcessChatEvent(Component.literal(l), false)); "ok" } catch (e: Exception) { "THROWS ${e::class.simpleName}: ${e.message}" }
						out += "chat|$corpse|$si|$k" to "$res ${P.nucleus.counters} ${P.excavator.counters} ${P.corpses.counters} ${P.maxKillCombo} ${P.mfTotal} ${P.mfDrops}"
					}
				}
			}
		} finally { corpseOpt._actualValue = before }
		val n = Golden.verify("batch45-mining", out, minCases = 9857)
		assertTrue(n >= 9857)
	}
}
