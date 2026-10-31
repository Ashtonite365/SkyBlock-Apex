// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object Batch24Kit {
	val titles = listOf("Correct all the panes!", "Change all to same color!", "Click in order!", "Click the button on time!", "What starts with: 'A'?", "What starts with: 'AB'?", "What starts with: ''?",
		"What starts with: ' '?", "Select all the Light Blue items!", "Select all the RED items!", "Select all the  items!", "Select all the items!", "Select all the red-ish items!", "correct all the panes!",
		" Click in order!", "", "What starts with: 'A'? ", "Select all the _1 items!")
	val recordSeq: List<Pair<String, Long>> by lazy {
		val r = Random(2401)
		val kinds = listOf("Panes", "Rubix", "Numbers", "Melody", "Select All")
		List(200) { kinds[r.nextInt(kinds.size)] to listOf(r.nextLong(0, 20000), 0L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 5000L)[if (r.nextInt(4) == 0) r.nextInt(1, 6) else 0] }
	}

	sealed interface Op
	data class Add(val p: Vec3) : Op
	data class Adv(val duration: Int) : Op
	data class Seg(val from: Vec3, val minPoints: Int) : Op

	val scripts: List<List<Op>> by lazy {
		val r = Random(2402)
		List(120) { s ->
			val ops = ArrayList<Op>()
			var base = Vec3(r.nextDouble(-50.0, 50.0), r.nextDouble(60.0, 120.0), r.nextDouble(-50.0, 50.0))
			var dir = Vec3(r.nextDouble(-1.0, 1.0), r.nextDouble(-0.3, 0.3), r.nextDouble(-1.0, 1.0))
			repeat(r.nextInt(5, 40)) {
				when (r.nextInt(12)) {
					in 0..5 -> { base = base.add(dir.scale(r.nextDouble(0.1, 1.0))); ops += Add(base) }
					6 -> { base = Vec3(r.nextDouble(-50.0, 50.0), 70.0, r.nextDouble(-50.0, 50.0)); ops += Add(base) }
					7 -> { dir = Vec3(r.nextDouble(-1.0, 1.0), 0.0, r.nextDouble(-1.0, 1.0)); ops += Add(base.add(dir)) }
					8 -> ops += Add(listOf(base, Vec3(Double.NaN, 1.0, 2.0), Vec3(-0.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0), base.add(1e-7, 0.0, 0.0))[r.nextInt(5)])
					9, 10 -> ops += Adv(listOf(1, 2, 3, 40, 0, -1)[r.nextInt(6)])
					else -> ops += Seg(listOf(Vec3.ZERO, base, Vec3(Double.NaN, 0.0, 0.0), Vec3(-0.0, 0.0, -0.0))[r.nextInt(4)], r.nextInt(-1, 5))
				}
			}
			ops += Seg(Vec3(1.0, 2.0, 3.0), 2)
			ops
		}
	}
	val dirCases: List<Pair<List<Vec3>, Vec3>> by lazy {
		val r = Random(2403)
		fun v() = Vec3(r.nextDouble(-3.0, 3.0), r.nextDouble(-3.0, 3.0), r.nextDouble(-3.0, 3.0))
		List(150) { List(r.nextInt(0, 4)) { v() } to v() } + listOf(
			listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0)) to Vec3(2.0, 0.0, 0.0), listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0)) to Vec3(2.0, 0.1, 0.0),
			listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0)) to Vec3(2.0, 0.2, 0.0), listOf(Vec3.ZERO, Vec3.ZERO) to Vec3(1.0, 0.0, 0.0),
			listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0)) to Vec3(1.0, 0.0, 0.0), listOf(Vec3.ZERO, Vec3(Double.NaN, 0.0, 0.0)) to Vec3(1.0, 0.0, 0.0))
	}
}
