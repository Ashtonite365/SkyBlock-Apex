// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.boss.DragonState
import dev.ashtonite365.apex.features.kit.boss.KingRelic
import dev.ashtonite365.apex.features.kit.boss.SpiritBearState
import dev.ashtonite365.apex.features.kit.boss.TerracottaState
import dev.ashtonite365.apex.features.kit.boss.TickTimerState
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch41GoldenTest {
	@Test
	fun boss() {
		val S = Batch41Boss
		val out = mutableListOf<Pair<String, String>>()
		out += "re" to listOf(TickTimerState.goldorRe, TickTimerState.coreRe, TickTimerState.stormEndRe, TickTimerState.stormStartRe,
			TickTimerState.stormPyRe, TickTimerState.fireFreezeRe).joinToString("|") { it.pattern }
		for ((si, sc) in S.timerScripts.withIndex()) {
			val t = TickTimerState()
			for ((k, op) in sc.withIndex()) {
				if (op.reset) t.reset()
				if (op.chat != null) t.onChat(S.chat[op.chat])
				t.onServerTick(op.goldorLoop, op.padLoop)
				out += "tt|$si|$k" to "${t.goldorTick} ${t.goldorStart} ${t.pad} ${t.lightning} ${t.py} ${t.stormTick} ${t.fireFreeze}"
			}
		}
		for (time in S.formatTimes) for (max in S.formatMax) for (ticks in listOf(true, false)) for (sym in listOf(true, false)) for (pre in listOf(true, false))
			out += "fmt|$time|$max|$ticks|$sym|$pre" to TickTimerState.format(time, max, "Pad:", ticks, sym, pre)
		out += "bearsets" to "${SpiritBearState.f4.sortedWith(compareBy({ it.first }, { it.third }))} ${SpiritBearState.m4.sortedWith(compareBy({ it.first }, { it.third }))} ${SpiritBearState.f4.size} ${SpiritBearState.m4.size}"
		for ((si, sc) in S.bearScripts.withIndex()) {
			val b = SpiritBearState()
			for ((k, op) in sc.withIndex()) {
				if (op.reset) b.reset()
				b.onBlock(op.x, op.y, op.z, op.old, op.new, op.master)
				repeat(op.tick) { b.onServerTick() }
				out += "bear|$si|$k" to "${b.kills} ${b.timer} ${b.display(true)} ${b.display(false)}"
			}
		}
		for ((si, sc) in S.terraScripts.withIndex()) {
			val t = TerracottaState()
			for ((k, op) in sc.withIndex()) {
				op.pot?.let { t.onFlowerPot(it.first, it.second, it.third, op.master) }
				repeat(op.ticks) { t.onServerTick() }
				out += "terra|$si|$k" to "${t.spawning}"
			}
		}
		for (f in S.colourFloats) out += "tcol|$f" to TerracottaState.colour(f)
		for (d in DragonState.Dragon.entries) {
			out += "drag|$d" to "${d.colour} ${d.sx} ${d.sy} ${d.sz} ${d.xr} ${d.zr} ${d.box.toList()} ${d.statue} ${d.ordinal}"
			for (p in S.boxPoints) out += "dbox|$d|$p" to "${d.contains(p.first, p.second, p.third)}"
		}
		for ((si, sc) in S.dragonScripts.withIndex()) {
			val ds = DragonState()
			for ((k, op) in sc.withIndex()) {
				val res = when (op.kind) {
					0 -> "${ds.onParticle(op.flame, op.count, op.x, op.y, op.z, op.dx, op.dy, op.dz)}"
					1 -> "${ds.onServerTick()}"
					2 -> { val s = S.statues[op.n]; "${ds.onStatueAir(s.first, s.second, s.third)}" }
					3 -> "${ds.kill(DragonState.Dragon.entries[op.n % 5])}"
					else -> { if (op.n == 0) ds.reset(); "r" }
				}
				out += "dragon|$si|$k" to "$res ${ds.state} ${ds.timeToSpawn} ${ds.spawnOrder} ${ds.aliveSince} ${ds.tick} ${ds.priority}"
			}
		}
		for (t in S.timerTicks) for (st in -1..3) for (sym in listOf(true, false)) out += "dtimer|$t|$st|$sym" to DragonState.timer(t, st, sym)
		for (h in S.healths) out += "hp|$h" to DragonState.health(h)
		for (k in KingRelic.entries) out += "relic|$k" to "${k.id} ${k.colour} ${k.rgb} ${k.x} ${k.y} ${k.z} ${k.ordinal}"
		for (id in S.relicIds) out += "relicof|$id" to "${KingRelic.of(id)}"
		val n = Golden.verify("batch41-boss", out, minCases = 36110)
		assertTrue(n >= 36110)
	}
}
