// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.crimson.VesuviusHelper
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.features.kit.skyblock.Ragnarock
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch22GoldenTest {
	@Test
	fun kit() {
		val S = Batch22Kit
		val out = mutableListOf<Pair<String, String>>()
		out += "slots" to "${LoadoutKeybinds.Logic.loadoutSlots.toList()} ${LoadoutKeybinds.Logic.NEXT} ${LoadoutKeybinds.Logic.PREVIOUS}"
		for (t in S.titles) out += "page|$t" to "${LoadoutKeybinds.Logic.page(t)}"
		out += "loadoutKeys" to "${LoadoutKeybinds.TConfig.allOptions.keys} ${LoadoutKeybinds.TConfig.slotKeys.size} ${LoadoutKeybinds.TConfig.allOptions.values.map { it.riskLevel }}"
		out += "ragKeys" to "${Ragnarock.TConfig.allOptions.keys} ${Ragnarock.Logic.CAST_PITCH} ${Ragnarock.Logic.cancelRegex.pattern}"
		for (c in S.chats) out += "cancel|$c" to "${Ragnarock.Logic.isCancel(c)}"
		for ((i, l) in S.strengthLores.withIndex()) out += "str|$i|$l" to "${Ragnarock.Logic.strengthFromLore(l)}"
		for (s in S.strengths) out += "gain|$s" to "${Ragnarock.Logic.gained(s)}"
		for (s in S.sounds) for (p in S.pitches) out += "sound|$s|$p" to "${Ragnarock.Logic.isCastSound(s, p)}"
		out += "vesKeys" to "${VesuviusHelper.TConfig.allOptions.keys}"
		for ((i, l) in S.chestLores.withIndex()) {
			out += "contents|$i|$l" to "${VesuviusHelper.contents(l)}"
			out += "cost|$i" to Golden.run { "${VesuviusHelper.coinCost(l)}" }
			for (n in S.chestNames) out += "claimed|$i|$n" to "${VesuviusHelper.isClaimed(n, l)}"
		}
		val n = Golden.verify("batch22-kit", out, minCases = 2030)
		assertTrue(n >= 2030)
	}
}
