// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.mining.MiningSolvers
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.kit.BossPhase
import dev.ashtonite365.apex.util.kit.BossPhaseTracker
import dev.ashtonite365.apex.util.kit.ServerTicks
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch3bGoldenTest {
	private fun keys(name: String) = Golden.load(name).map { it.first }
	private fun BlockPos.str() = "$x,$y,$z"

	@Test
	fun miningSolvers() {
		val out = keys("mining-solvers").map { k ->
			val kind = k.substringBefore('|')
			val arg = k.substringAfter('|')
			k to Golden.run {
				when (kind) {
					"fetchur" -> MiningSolvers.fetchurAnswer(arg) ?: "-"
					"puzzler" -> MiningSolvers.puzzlerTarget(arg).str()
					"puzzler-at" -> {
						val (arrows, at) = arg.split('|')
						val (x, y, z) = at.split(',').map { it.toInt() }
						MiningSolvers.puzzlerTarget(arrows, BlockPos(x, y, z)).str()
					}
					"puzzler-line" -> MiningSolvers.puzzlerArrows(arg) ?: "-"
					"wp" -> MiningSolvers.parseWaypoint(arg)?.str() ?: "-"
					"gems" -> MiningSolvers.gemstoneWaypoints(com.google.gson.JsonParser.parseString(
						"""{"a":{"title":"Ruby","waypoints":["1:2:3","x","4:5:6"]},"b":{"waypoints":["7:8:9"]},"c":{"title":"None"}}""").asJsonObject)
						.joinToString(";") { "${it.first}@${it.second.str()}" }
					"gems-null" -> MiningSolvers.gemstoneWaypoints(null).toString()
					"perk" -> {
						val (lore, name) = arg.split('|')
						MiningSolvers.perkLevel(if (lore.isEmpty()) emptyList() else lore.split("⏎"), name)?.toString() ?: "-"
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("mining-solvers", out, minCases = 2500)
	}

	@Test
	fun bossPhasesAndTicks() {
		val out = keys("kit-boss-ticks").map { k ->
			val arg = k.substringAfter('|')
			k to when {
				k.startsWith("phase|") -> BossPhase.match(arg)?.let { "${it.name}|${it.boss}" } ?: "-"
				k.startsWith("tracker") -> {
					BossPhaseTracker.reset()
					(arg.split("⏎").mapIndexed { t, l -> BossPhaseTracker.handle(l, t * 7L)?.let { "${it.phase}<${it.previous}@${it.serverTick}" } ?: "." } +
						"cur=${BossPhaseTracker.current} start=${BossPhaseTracker.phaseStartTick}").joinToString(" ")
				}
				k.startsWith("ticks") -> {
					ServerTicks.reset()
					(arg.split(',').map { if (ServerTicks.accept(it.toInt())) "1" else "0" } + "t=${ServerTicks.totalTicks}" +
						listOf(-1L, 0L, 3L, 100L).map { "s$it=${ServerTicks.since(it)}" }).joinToString(" ")
				}
				else -> error(k)
			}
		}
		Golden.verify("kit-boss-ticks", out, minCases = 670)
	}
}
