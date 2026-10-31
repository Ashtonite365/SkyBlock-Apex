// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.devices.ArrowAlignLogic
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignState
import dev.ashtonite365.apex.features.kit.devices.SimonSaysState
import dev.ashtonite365.apex.features.kit.splits.KitSplitDefs
import dev.ashtonite365.apex.features.kit.splits.RunSummary
import dev.ashtonite365.apex.features.kit.splits.SplitDef
import dev.ashtonite365.apex.features.kit.splits.SplitRun
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch39GoldenTest {
	@Test
	fun kit() {
		val S = Batch39Kit
		val out = mutableListOf<Pair<String, String>>()
		fun defs(l: List<SplitDef>) = l.joinToString { "${it.name}=${it.regex.pattern}" }
		out += "kuudra" to defs(KitSplitDefs.kuudra)
		for ((i, b) in KitSplitDefs.bossPhases.withIndex()) out += "boss|$i" to defs(b)
		out += "cleared" to KitSplitDefs.dungeonCleared.pattern + RunSummary.extraStatsHeader.pattern
		for (f in S.floors) out += "floor|$f" to "${KitSplitDefs.floorNumber(f)} ${KitSplitDefs.bossRun(f)?.defs?.let { defs(it) }}"
		for (t in S.ticks) out += "fmt|$t" to SplitRun.formatTicks(t)
		val runs = listOf<() -> SplitRun?>({ SplitRun(KitSplitDefs.kuudra) }, { KitSplitDefs.bossRun("F7") }, { KitSplitDefs.bossRun("M3") },
			{ KitSplitDefs.bossRun("F1") }, { KitSplitDefs.bossRun("F6") }, { SplitRun(KitSplitDefs.kuudra.take(1)) }, { SplitRun(emptyList()) })
		for ((ri, mk) in runs.withIndex()) for ((si, sc) in S.scripts.withIndex()) {
			val run = mk()!!
			for ((k, e) in sc.withIndex()) {
				out += "run|$ri|$si|$k" to Golden.run {
					val hit = run.onMessage(S.splitLines[e.line], e.now, e.tick)
					"$hit ${run.started} ${run.finished}" + if (e.rowsAt) " ${run.rows(e.now + 777, e.tick + 13)}" else ""
				}
			}
			out += "run|$ri|$si|end" to Golden.run { "${run.rows(9_999_999, 99_999)}" }
		}
		for ((si, sc) in S.summaryScripts.withIndex()) {
			val rs = RunSummary()
			for ((k, li) in sc.withIndex()) out += "sum|$si|$k" to "${rs.feed(S.summaryLines[li])} ${rs.collecting} ${rs.take()} ${rs.collecting}"
		}
		for ((si, sc) in S.simonScripts.withIndex()) {
			val s = SimonSaysState()
			for ((k, op) in sc.withIndex()) {
				val p = BlockPos(if (op.kind == 1) 110 else 111, op.y, op.z)
				val extra = when (op.kind) {
					0 -> { s.onLanternShown(p); "" }
					1 -> { val c = s.isCorrectClick(p); val f = s.roundFinishedBy(p); s.onButtonPressed(p); "$c $f" }
					2 -> { s.onStartPressed(); "" }
					3 -> { s.onTick(op.n); "" }
					else -> { s.firstPhase = op.n % 2 == 0; s.ticksSinceLantern = op.n - 3; "" }
				}
				out += "simon|$si|$k" to "$extra ${s.order.map { it.toShortString() }} ${s.clickNeeded} ${s.firstPhase} ${s.ticksSinceLantern} ${s.remaining().map { it.toShortString() }}"
			}
		}
		out += "simonc" to "${SimonSaysState.START_BUTTON} ${SimonSaysState.CENTER} ${ArrowAlignLogic.GRID_CORNER} ${ArrowAlignLogic.CENTER} ${ArrowAlignLogic.SOLUTIONS}"
		for ((x, y, z) in S.gridPositions) {
			val p = BlockPos(x, y, z)
			out += "grid|$x|$y|$z" to "${SimonSaysState.inGrid(p, 110)} ${SimonSaysState.inGrid(p, 111)} ${ArrowAlignLogic.indexOf(p)}"
		}
		for (i in -6..30) out += "posOf|$i" to ArrowAlignLogic.posOf(i).toShortString()
		for (c in -2..9) for (t in -2..9) out += "cn|$c|$t" to "${ArrowAlignLogic.clicksNeeded(c, t)}"
		for (idx in listOf(null, 0, 3, 24)) for (solved in listOf(true, false)) for (sn in listOf(true, false))
			out += "lblock|$idx|$solved|$sn" to "${ArrowAlignLogic.shouldBlock(idx, mapOf(3 to 2), solved, sn)}"
		for ((i, rot) in S.rotationSets.withIndex()) out += "sol|$i|$rot" to Golden.run { "${ArrowAlignLogic.solutionFor(rot)} ${ArrowAlignLogic.clicks(rot)}" }
		for ((si, sc) in S.alignScripts.withIndex()) {
			val a = ArrowAlignState()
			for ((k, op) in sc.withIndex()) {
				val res = Golden.run {
					when (op.kind) {
						0, 1 -> { a.update(S.rotationSets[op.server], op.now); "u" }
						2 -> "${a.shouldBlock(if (op.idx < 0) null else op.idx, op.sneak)}"
						3 -> { a.onClick(op.idx, op.now); "c" }
						else -> { if (op.sneak) a.reset(); "${a.clicks()}" }
					}
				}
				out += "align|$si|$k" to "$res ${a.rotations} ${Golden.run { "${a.clicks()}" }}"
			}
		}
		val n = Golden.verify("batch39-kit", out, minCases = 30150)
		assertTrue(n >= 30150)
	}
}
