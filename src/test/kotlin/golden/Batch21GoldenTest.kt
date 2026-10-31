// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs
import dev.ashtonite365.apex.features.kit.splits.KuudraSplits
import dev.ashtonite365.apex.features.kit.terminals.DelayedClicks
import dev.ashtonite365.apex.features.kit.terminals.PickModel
import dev.ashtonite365.apex.features.kit.terminals.RubixModel
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.random.Random
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch21GoldenTest {
	@Test
	fun kit() {
		val S = Batch21Kit
		val out = mutableListOf<Pair<String, String>>()
		for (t in S.tags) {
			out += "mob|$t" to "${StarredMobs.isDungeonMobName(t)} ${StarredMobs.isLostAdventurer(t)} ${StarredMobs.isStarred(t, true)} ${StarredMobs.isStarred(t, false)}"
			for (a in listOf(true, false)) for (b in listOf(true, false)) for (c in listOf(true, false)) out += "kind|$t|$a|$b|$c" to "${StarredMobs.highlightKind(t, a, b, c)}"
		}
		out += "kinds" to "${StarredMobs.Kind.entries} ${StarredMobs.LOST_ADVENTURER}"
		for (b in S.boxes) out += "edges|${b.toList()}" to "${StarredMobs.boxEdges(AABB(b[0], b[1], b[2], b[3], b[4], b[5]))}"
		for (seed in S.seeds) {
			val r = Random(seed)
			for (size in listOf(9, 1, 2, 4)) {
				val m = RubixModel(size, r)
				val log = StringBuilder("${m.cells.toList()} ${m.done};")
				repeat(12) { val i = r.nextInt(size); val right = r.nextBoolean(); m.click(i, right); log.append("$i$right ${m.cells.toList()} ${m.done};") }
				out += "rubix|$seed|$size" to log.toString()
			}
			out += "rubix0|$seed" to Golden.run { "${RubixModel(0, r).cells.toList()}" }
			for (letter in PickModel.LETTERS + listOf("x", "Q", "")) {
				out += "starts|$seed|$letter" to Golden.run {
					val p = PickModel.startsWith(letter, 21, r)
					val log = StringBuilder("${p.labels} ${p.done};")
					repeat(8) { val i = r.nextInt(21); p.click(i); log.append("$i ${p.picked.toList()} ${p.failed} ${p.done};") }
					log.toString()
				}
			}
			out += "startsQ|$seed" to Golden.run { "${PickModel.startsWith("Q", 5, r).labels}" }
			for (colour in PickModel.COLOURS.take(4) + listOf("Nope")) {
				val p = PickModel.selectAll(colour, 28, r)
				val log = StringBuilder("${p.labels} ${p.done};")
				for (i in p.labels.indices) if (p.matches(p.labels[i])) p.click(i)
				log.append("${p.done} ${p.failed};")
				p.click(r.nextInt(28)); log.append("${p.done} ${p.failed}")
				out += "select|$seed|$colour" to log.toString()
			}
			out += "rng|$seed" to "${r.nextLong()}"
		}
		out += "lists" to "${PickModel.WORDS} ${PickModel.LETTERS} ${PickModel.COLOURS}"
		var clock = 0L
		val d = DelayedClicks<Int> { clock }
		val dl = StringBuilder()
		for (step in 0 until 40) {
			if (step % 3 == 0) d.add(step, listOf(0, 50, 120, -10, 300)[step % 5])
			clock += listOf(0L, 10L, 40L, 100L)[step % 4]
			dl.append(d.due()).append(';')
			if (step == 30) d.clear()
		}
		out += "delayed" to dl.toString()
		out += "kuudraKeys" to "${KuudraSplits.TConfig.allOptions.keys}"
		val n = Golden.verify("batch21-kit", out, minCases = 3550)
		assertTrue(n >= 3550)
	}
}
