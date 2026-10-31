// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.chat.PlusChatExtras
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingProfit
import dev.ashtonite365.apex.features.plus.fishing.PlusLavaReplacement
import dev.ashtonite365.apex.features.plus.foraging.PlusForagingProfit
import dev.ashtonite365.apex.features.plus.minion.MinionPlusPorts
import dev.ashtonite365.apex.features.plus.misc.MiscPlusPorts
import dev.ashtonite365.apex.features.plus.slayer.PlusSlayerLogic
import dev.ashtonite365.apex.features.plus.visualwords.VisualWords
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.SkyBlockIsland
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch16GoldenTest {
	@Test
	fun sh() {
		val S = Batch16Sh
		val out = mutableListOf<Pair<String, String>>()
		for (id in S.toolIds) out += "tool|$id" to "${PlusForagingProfit.isAxe(id)} ${PlusFishingProfit.isRod(id)}"
		for (id in listOf("DIRT", "NOT_A_REAL_ITEM_XYZ", "")) out += "name|$id" to Golden.run { PlusFishingProfit.nameOf(id) }
		for (s in S.specs) {
			val w = VisualWords.parse(s)
			out += "vw|$s" to "$w"
			for (t in S.texts) for (cs in listOf(true, false)) out += "vwa|$s|$t|$cs" to Golden.run { VisualWords.apply(t, w, cs) }
		}
		for (s in S.shorts) out += "short|$s" to "${PlusSlayerLogic.parseShort(s)}"
		for (p in S.progress) out += "prog|$p" to "${PlusSlayerLogic.parseProgress(p)}"
		for ((c, t, x) in S.kills) out += "kills|$c|$t|$x" to Golden.run { "${PlusSlayerLogic.remainingKills(c, t, x)}" }
		for (l in S.rng) out += "rng|$l" to "${PlusSlayerLogic.rngMeterChat.matches(l)}"
		for ((i, l) in S.lores.withIndex()) {
			out += "spawn|$i" to "${PlusSlayerLogic.isNotSpawnable(l)}"
			out += "need|$i" to Golden.run { "${MinionPlusPorts.upgradeNeed(l)}" }
		}
		for (t in S.mobTags) out += "mob|$t" to "${PlusSlayerLogic.isIrrelevantMobTag(t)}"
		for (t in S.pillars) out += "pillar|$t" to "${PlusSlayerLogic.firePillarSeconds(t)}"
		for (t in S.shields) out += "shield|$t" to "${PlusSlayerLogic.HellionShield.inName(t)}"
		out += "shields" to PlusSlayerLogic.HellionShield.entries.joinToString { "${it.name}/${it.label}/${it.colour}/${it.dagger}" }
		for (t in S.twin) out += "twin|$t" to "${PlusSlayerLogic.isTwinclaws(t)}"
		for (t in S.titles) out += "title|$t" to "${MinionPlusPorts.isInfernoTitle(t)}"
		for (f in S.fuels) out += "fuel|$f" to "${MinionPlusPorts.isInfernoFuel(f)}"
		out += "slots" to "${MinionPlusPorts.FUEL_SLOT} ${MinionPlusPorts.PICKUP_SLOT}"
		for (p in S.piggy) out += "piggy|$p" to "${MiscPlusPorts.isPiggyBreak(p)}"
		for ((i, b) in S.bottleSets.withIndex()) out += "bottle|$i" to "${MiscPlusPorts.chargeBottleMessage(b)}"
		val islands = listOf(null, SkyBlockIsland.CRIMSON_ISLE, SkyBlockIsland.KUUDRA, SkyBlockIsland.DUNGEON, SkyBlockIsland.GARDEN, SkyBlockIsland.ofMode("crimson_isle"))
		val bools = listOf(false, true)
		for (e in bools) for (o in bools) for ((ii, isl) in islands.withIndex()) for (ev in bools) for (k in bools) for (d in bools)
			out += "lava|$e|$o|$ii|$ev|$k|$d" to "${PlusLavaReplacement.shouldReplace(e, o, isl, ev, k, d)}"
		for (d in S.deaths) out += "death|$d" to "${PlusChatExtras.deathName(d)}"
		for (a in S.actionBars) out += "xp|$a" to "${PlusChatExtras.xpSegment(a)}"
		val n = Golden.verify("batch16-sh", out, minCases = 1060)
		assertTrue(n >= 1060)
	}
}
