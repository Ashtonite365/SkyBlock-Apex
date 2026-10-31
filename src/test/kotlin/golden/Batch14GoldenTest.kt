// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.fishing.PlusFishingMerges
import dev.ashtonite365.apex.features.plus.fishing.PlusInventoryGains
import dev.ashtonite365.apex.features.plus.garden.GardenWarps
import dev.ashtonite365.apex.features.plus.gui.TrevorPlusPorts
import dev.ashtonite365.apex.features.plus.mining.NucleusBarriersBox
import dev.ashtonite365.apex.features.plus.pets.PetNametag
import dev.ashtonite365.apex.features.plus.pets.PetPlusPorts
import dev.ashtonite365.apex.features.plus.reminders.PlusMiscLogic
import dev.ashtonite365.apex.features.plus.stranded.PlusStranded
import dev.ashtonite365.apex.features.plus.visualwords.PlusText
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.buildPlusLines
import dev.ashtonite365.apex.util.plus.toPlusComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch14GoldenTest {
	@Test
	fun misc() {
		val M = Batch14Misc
		val out = mutableListOf<Pair<String, String>>()
		val ints = listOf(-1, 0, 1, 2, 5, 30, 100)
		for (a in ints) for (o in listOf(-1L, 0L, 1L, 29L, 30L, 31L, 1000L)) for (l in ints) out += "time|$a|$o|$l" to "${PlusFishingMerges.timeAlert(a, o, l)}"
		val baits = listOf(null, "", "Fish Bait", "Spiked Bait")
		for (b in baits) for (a in baits) out += "bait|$b|$a" to "${PlusFishingMerges.baitChanged(b, a)}"
		for (t in listOf("BRONZE", "SILVER", "GOLD", "DIAMOND", "bronze", "")) for (h in ints) for (bz in listOf(0, 1, 5)) for (sv in listOf(0, 2, 100))
			out += "trophy|$t|$h|$bz|$sv" to "${PlusFishingMerges.hideTrophy(t, h, bz, sv)}"
		for ((i, tag) in M.petTags.withIndex()) for (hl in listOf(false, true)) for (hm in listOf(false, true))
			out += "pet|$i|$hl|$hm" to Golden.run { "${PetNametag.rewrite(tag, hl, hm)}" }
		for ((i, l) in M.candyLores.withIndex()) out += "candy|$i" to Golden.run { "${PetPlusPorts.candyUsed(l)}" }
		for (k in M.kickLines) out += "kick|$k" to "${PlusMiscLogic.isKick(k)}"
		for ((i, sb) in M.scoreboards.withIndex()) {
			out += "restart|$i" to Golden.run { "${PlusMiscLogic.restartSeconds(sb)}" }
			out += "date|$i" to Golden.run { "${PlusMiscLogic.dateLine(sb)}" }
		}
		for ((i, mv) in M.moves.withIndex()) out += "speed|$i" to "${PlusMiscLogic.speed(mv.first, mv.second, mv.third)}"
		out += "speeddef" to "${PlusMiscLogic.speed(0.25, 0.5)}"
		for ((i, h) in M.histories.withIndex()) out += "recent|$i" to "${PlusMiscLogic.recentServers(h.first, h.second, h.third)}"
		for ((i, g) in M.gainPairs.withIndex()) out += "gains|$i" to PlusInventoryGains.gains(g.first, g.second).let { "$it ${it.javaClass.simpleName.isNotEmpty()}" }
		for (h in listOf(false, true)) for (s in listOf(false, true)) for (b in listOf(false, true)) out += "warp|$h|$s|$b" to "${GardenWarps.commandFor(h, s, b)}"
		for ((x, y, z) in listOf(Triple(0, 0, 0), Triple(474, 111, 524), Triple(-5, -64, 3), Triple(Int.MAX_VALUE, 0, Int.MIN_VALUE))) out += "nbox|$x|$y|$z" to "${NucleusBarriersBox.box(x, y, z)}"
		for (c in NucleusBarriersBox.Crystal.entries) out += "crystal|$c" to "${c.box} ${c.colour}"
		for ((i, l) in M.npcLores.withIndex()) out += "npc|$i" to Golden.run { "${PlusStranded.isPlaceable(l)}" }
		for (p in M.peltLines) out += "pelt|$p" to Golden.run { "${TrevorPlusPorts.parsePelts(p)}" }
		for ((i, c) in M.texts.withIndex()) {
			for ((a, b) in M.replacePairs) out += "repl|$i|$a|$b" to PlusText.replaceFirst(c, a, b).let { "${it === c} ${it.string} $it" }
			out += "map|$i" to PlusText.mapSegments(c) { t, s -> if (t.length % 2 == 0) t.uppercase() to s.withUnderlined(true) else t to s }.let { "${it === c} $it" }
			out += "mapid|$i" to PlusText.mapSegments(c) { t, s -> t to s }.let { "${it === c}" }
		}
		out += "lines" to buildPlusLines {
			add("§eTitle"); add(Component.literal("x")); addAll(listOf(Component.literal("a"), Component.literal("b"))); addIf(false) { "no" }; addIf(true) { "yes" }
			pair("Kills", 3); pair("Null", null); pair("", 1.5)
		}.joinToString("|")
		out += "lines0" to buildPlusLines { }.toString()
		out += "shc" to listOf("§aA", "", "b").toPlusComponents().joinToString("|")
		out += "style" to Style.EMPTY.toString()
		val n = Golden.verify("batch14-misc", out, minCases = 4800)
		assertTrue(n >= 4800)
	}
}
