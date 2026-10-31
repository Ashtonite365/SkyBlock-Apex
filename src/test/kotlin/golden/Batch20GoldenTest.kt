// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.dungeon.DungeonExtras
import dev.ashtonite365.apex.features.kit.kuudra.PearlLineups
import dev.ashtonite365.apex.features.kit.render.KitCamera
import dev.ashtonite365.apex.features.kit.terminals.KitTerminalLogic
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch20GoldenTest {
	@Test
	fun kit() {
		val S = Batch20Kit
		KitCamera.TConfig.explicitDefaultLoad()
		DungeonExtras.TConfig.explicitDefaultLoad()
		val out = mutableListOf<Pair<String, String>>()
		out += "order" to "${KitTerminalLogic.RUBIX_ORDER} ${KitTerminalLogic.RubixMode.entries}"
		for ((i, p) in S.panes.withIndex()) for (mode in KitTerminalLogic.RubixMode.entries) {
			out += "solve|$i|$p|$mode" to Golden.run { "${KitTerminalLogic.rubixSolve(p, mode)}" }
			for (goal in -1..5) out += "for|$i|$goal|$mode" to Golden.run { "${KitTerminalLogic.rubixClicksFor(goal, p, mode)}" }
		}
		for (a in S.melody) for (b in S.melody) out += "melody|$a|$b" to "${KitTerminalLogic.melodyReady(a, b)}"
		for ((o, n, p) in S.clicks) out += "first|$o|$n|$p" to "${KitTerminalLogic.firstClickBlocked(o, n, p)}"
		out += "vanilla" to "${KitCamera.Logic.VANILLA_DISTANCE}"
		for (d in S.distances) for (e in listOf(true, false)) for (n in listOf(1, 4, 7, 32, 0, -3))
			out += "cam|$d|$e|$n" to "${KitCamera.Logic.scaledDistance(d, e, n)}"
		out += "camKeys" to "${KitCamera.TConfig.allOptions.keys} ${KitCamera.TConfig.allOptions.values.map { it.get() }}"
		out += "dxKeys" to "${DungeonExtras.TConfig.allOptions.keys}"
		out += "dxVals" to "${DungeonExtras.TConfig.allOptions.values.map { o -> o.get().let { v -> if (v is io.github.notenoughupdates.moulconfig.ChromaColour) "rgb" + v.getEffectiveColourRGB() else "$v" } }}"
		out += "dxRisk" to "${DungeonExtras.TConfig.allOptions.values.map { it.riskLevel }}"
		out += "clear" to DungeonExtras.ClearMode.entries.joinToString { "${it.name}/${it.id}/${it.serializedName}" }
		for ((i, j) in S.jsons.withIndex()) out += "parse|$i" to Golden.run { "${PearlLineups.parse(j)}" }
		out += "table" to "${PearlLineups.table.size} ${PearlLineups.table.firstOrNull()}"
		val synth = S.synthLineups.mapIndexed { i, s -> PearlLineups.Lineup("s$i", S.colours[i % S.colours.size], s.map { (x, y, z) -> PearlLineups.Pos(x, y, z) }, emptyList()) }
		for ((x, y, z) in S.players) {
			out += "ord|$x|$y|$z" to PearlLineups.ordered(synth, x, y, z).joinToString { it.supply }
			for (c in listOf(true, false)) out += "vis|$x|$y|$z|$c" to PearlLineups.visible(synth, x, y, z, c).joinToString { it.supply }
			out += "tord|$x|$y|$z" to PearlLineups.ordered(PearlLineups.table, x, y, z).joinToString { it.supply + "/" + it.colour }
			out += "dist|$x|$y|$z" to "${PearlLineups.Pos(-100, 80, -100).distSq(x, y, z)}"
		}
		for (c in S.colours) out += "rgb|$c" to "${PearlLineups.rgb(c)}"
		val n = Golden.verify("batch20-kit", out, minCases = 5300)
		assertTrue(n >= 5300)
	}
}
