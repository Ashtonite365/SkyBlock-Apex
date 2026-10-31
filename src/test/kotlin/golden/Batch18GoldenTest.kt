// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenPestWaypoint
import dev.ashtonite365.apex.features.plus.foraging.WhispersPerHotfPerk
import dev.ashtonite365.apex.features.plus.garden.SprayTracker
import dev.ashtonite365.apex.features.plus.hunting.PlusShardChecklist
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch18GoldenTest {
	@Test
	fun sh() {
		val S = Batch18Sh
		val out = mutableListOf<Pair<String, String>>()
		for ((ti, trail) in S.trails.withIndex()) {
			GardenPestWaypoint.reset()
			val steps = StringBuilder()
			for (a in trail) {
				val ok = Golden.run { "${GardenPestWaypoint.add(Vec3(a.p[0], a.p[1], a.p[2]), Vec3(a.player[0], a.player[1], a.player[2]), a.now, a.pitch)}" }
				steps.append(ok).append(' ').append(GardenPestWaypoint.guess).append(' ').append(GardenPestWaypoint.currentGuess()).append(';')
			}
			out += "trail|$ti" to steps.toString()
		}
		GardenPestWaypoint.reset()
		out += "trailReset" to "${GardenPestWaypoint.guess}"
		for (p in S.particles) out += "part|$p" to "${GardenPestWaypoint.isTrailParticle(p[0] as Boolean, p[1] as Int, p[2] as Float, p[3] as Boolean)}"
		for ((i, l) in S.scoreboards.withIndex()) out += "biome|$i|$l" to "${PlusShardChecklist.biomeOf(l)}"
		for (c in S.catches) out += "shard|$c" to "${PlusShardChecklist.shardOf(c)}"
		out += "table" to "${PlusShardChecklist.shards}"
		for ((i, set) in S.caughtSets.withIndex()) {
			PlusShardChecklist.caught.clear(); PlusShardChecklist.caught += set
			for (cur in listOf(null) + PlusShardChecklist.Biome.entries) for (only in listOf(true, false)) for (hide in listOf(true, false))
				out += "lines|$i|$cur|$only|$hide" to PlusShardChecklist.lines(cur, only, hide).joinToString("\n")
		}
		PlusShardChecklist.caught.clear()
		for (s in S.sprays) out += "spray|$s" to "${SprayTracker.parse(s)}"
		for (h in S.held) out += "dur|$h" to "${SprayTracker.durationMinutes(h)}"
		for (ms in S.remaining) out += "rem|$ms" to SprayTracker.formatRemaining(ms)
		out += "sprayData" to "${SprayTracker.Spray("1", "Compost", 5L)} ${SprayTracker.Spray("1", "Compost", 5L, true) == SprayTracker.Spray("1", "Compost", 5L, true)}"
		for ((i, l) in S.levelLores.withIndex()) out += "lvl|$i|$l" to Golden.run { "${WhispersPerHotfPerk.level(l)}" }
		for (n in S.names) out += "perk|$n" to "${WhispersPerHotfPerk.Perk.byName(n)}"
		for (perk in WhispersPerHotfPerk.Perk.entries) {
			out += "max|$perk" to "${perk.guiName} ${perk.maxLevel} ${perk.totalCostMax}"
			for (l in -1..perk.maxLevel + 2) out += "cost|$perk|$l" to Golden.run { "${perk.cost(l)} ${perk.totalCost(l)}" }
			for (l in S.levels) {
				out += "spent|$perk|$l" to Golden.run { WhispersPerHotfPerk.spentLine(perk, l) }
				out += "next|$perk|$l" to Golden.run { "${WhispersPerHotfPerk.next10(perk, l)}" }
			}
		}
		val n = Golden.verify("batch18-sh", out, minCases = 2850)
		assertTrue(n >= 2850)
		assertTrue(out.count { it.first.startsWith("trail|") && it.second.contains("true (") } > 60, "fitted trails")
	}
}
