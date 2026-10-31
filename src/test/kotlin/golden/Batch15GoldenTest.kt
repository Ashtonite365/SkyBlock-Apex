// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.fishing.FishingPlusPorts
import dev.ashtonite365.apex.features.plus.combat.PlusCombatLogic
import dev.ashtonite365.apex.features.plus.crimson.CrimsonPlusPorts
import dev.ashtonite365.apex.features.plus.crimson.SulphurSkitterBox
import dev.ashtonite365.apex.features.plus.fishing.PlusTrophyFillet
import dev.ashtonite365.apex.features.plus.hunting.PlusHuntingProfit
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.ChatPatternTest
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.PlusRepoData
import dev.ashtonite365.apex.util.plus.anyMatches
import dev.ashtonite365.apex.util.plus.findMatcher
import dev.ashtonite365.apex.util.plus.groupOrNull
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.plus.matches
import kotlinx.serialization.json.JsonElement
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch15GoldenTest {
	@Test
	fun sh() {
		val S = Batch15Sh
		val out = mutableListOf<Pair<String, String>>()
		for ((i, p) in S.pitches.withIndex()) out += "pitch|$i" to "${FishingPlusPorts.isBansheePitch(p)}"
		for (n in S.chumNames) for (o in S.owns) for (h in listOf(false, true)) out += "chum|$n|$o|$h" to "${FishingPlusPorts.isChumTag(n, o, h)}"
		for (p in listOf(BlockPos(0, 0, 0), BlockPos(-10, 64, 300), BlockPos(Int.MAX_VALUE - 2, -64, Int.MIN_VALUE + 2))) out += "area|$p" to "${SulphurSkitterBox.area(p)}"
		for (l in S.shardLines) out += "shard|$l" to Golden.run { "${PlusHuntingProfit.parseShard(l)}" }
		for (id in S.trophyIds) for ((t, tab) in S.trophyTables.withIndex()) out += "fillet|$id|$t" to "${PlusTrophyFillet.filletValue(id, tab)}"
		out += "rd|readmissing" to "${PlusRepoData.readText("does-not-exist")}"
		out += "rd|selftest" to "${PlusRepoData.readText("selftest")?.length}"
		out += "rd|loadJson" to Golden.run { "${PlusRepoData.loadJson("selftest")}" }
		out += "rd|loadJson2" to Golden.run { "${PlusRepoData.loadJson("selftest") === PlusRepoData.loadJson("selftest")}" }
		out += "rd|missing" to Golden.run { "${PlusRepoData.loadJson("nope")}" }
		out += "rd|README" to Golden.run { "${PlusRepoData.load("README", JsonElement.serializer())}" }
		out += "rd|parse" to Golden.run { "${PlusRepoData.parse("{\"a\":[1,2]}", JsonElement.serializer())}" }
		out += "rd|parsebad" to Golden.run { "${PlusRepoData.parse("{", JsonElement.serializer())}" }
		out += "rd|anita" to Golden.run { "${PlusRepoData.loadRepoJson("constants/AnitaUpgradeCosts.json")?.toString()?.length}" }
		out += "rd|repomissing" to Golden.run { "${PlusRepoData.loadRepoJson("constants/DoesNotExist.json")}" }
		for ((i, pc) in S.patternCases.withIndex()) {
			val p = PlusPatterns.compile("golden15.k$i", pc.second)
			out += "pat|$i|same" to "${PlusPatterns.compile("golden15.k$i", "zzz") === p} ${"golden15.k$i" in PlusPatterns.keys}"
			for (strip in listOf(true, false)) {
				out += "pat|$i|$strip|m" to "${p.matches(pc.first, strip)}"
				out += "pat|$i|$strip|mm" to "${p.matchMatcher(pc.first, strip) { group() + "/" + groupCount() }}"
				out += "pat|$i|$strip|fm" to "${p.findMatcher(pc.first, strip) { group() + "/" + start() + "/" + groupOrNull("x") + "/" + groupOrNull("y") + "/" + groupOrNull("q") }}"
				out += "pat|$i|$strip|any" to "${p.anyMatches(S.patternCases.map { it.first }, strip)}"
			}
			out += "pat|$i|check" to "${ChatPatternTest.check(p, S.patternCases.map { it.first }, S.patternCases.map { it.first + "!" })}"
		}
		val g = PlusPatterns.group("golden15.grp")
		val lazyP by g.pattern("k", "a+")
		out += "grp" to "${g.prefix} ${lazyP.pattern()} ${"golden15.grp.k" in PlusPatterns.keys}"
		for (t in S.bossTags) out += "boss|$t" to Golden.run { PlusCombatLogic.parseBossTag(t).let { "$it ${it?.percent}" } }
		out += "bosses" to PlusCombatLogic.bosses.joinToString()
		for (l in S.combatLines) {
			out += "node|$l" to Golden.run { "${PlusCombatLogic.parseEnderNode(l)}" }
			out += "stack|$l" to "${PlusCombatLogic.armorStack(l)}"
			out += "arachne|$l" to "${PlusCombatLogic.isArachneDown(l)}"
			out += "drop|$l" to "${PlusCombatLogic.dragonLoot(l)}"
			out += "summon|$l" to "${PlusCombatLogic.isSummonSpawn(l)} ${PlusCombatLogic.isSummonDespawn(l)}"
		}
		out += "arachneconst" to PlusCombatLogic.ARACHNE_CALLING + PlusCombatLogic.ARACHNE_CRYSTAL
		for ((i, t) in S.tabs.withIndex()) {
			out += "brood|$i" to "${PlusCombatLogic.broodmotherStage(t)}"
			out += "volcano|$i" to t.joinToString("|") { "${CrimsonPlusPorts.volcanoStatus(it)}" }
		}
		for (l in S.npcLines) out += "npc|$l" to "${CrimsonPlusPorts.pabloFlower(l)} ${CrimsonPlusPorts.isSirih(l)}"
		val n = Golden.verify("batch15-sh", out, minCases = 1140)
		assertTrue(n >= 1140)
	}
}
