// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.PuzzleRoom
import dev.ashtonite365.apex.features.kit.puzzles.IceFillFloors
import net.minecraft.core.BlockPos
import kotlin.random.Random

object Batch23Kit {
	val deviceLines = listOf("Ashton completed a device! (1/7)", "  Ashton completed a device! (7/7)  ", "Ashtonite365ABCDE completed a device! (1/7)", "A completed a device! (12/7)",
		"[MVP+] Ashton completed a device! (1/7)", "Ashton completed a device! (1/7) extra", "Ashton activated a terminal! (1/7)", "", " completed a device! (1/7)", "x completed a device! (a/7)",
		"Ash ton completed a device! (3/4)", "Ashton completed a device!  (1/7)")
	val petTitles = listOf("Pets", "(1/3) Pets", "(2/2)Pets", "(3/2) Pets", "(0/0) Pets", "(1/3)  Pets", "Pets (1/3)", "(99999999999/3) Pets", "(1/99999999999) Pets", "(a/3) Pets", "pets", "Pets ", "", "(1/3) Pets: Kat")
	val grids = listOf(DungeonMapGrid(5, 5, 16), DungeonMapGrid(13, 13, 18), DungeonMapGrid(0, 0, 0), DungeonMapGrid(-4, 3, 16), DungeonMapGrid(5, 5, -4), DungeonMapGrid(5, 5, -6))
	val markers: List<Pair<Int, Int>> by lazy {
		val r = Random(2301)
		listOf(-128 to -128, 127 to 127, -118 to -118, 0 to 0, -129 to -129, 200 to -300) + List(120) { r.nextInt(-130, 130) to r.nextInt(-130, 130) }
	}
	val actionBars = listOf("§b-1200 Mana (Gravity Storm)", "-1200 Mana (Gravity Storm)", "1500/1500✎ Mana     -1200 Mana (Gravity Storm)   ", "-1200 Mana (Gravity Storm", "Mana (Gravity Storm)",
		"line1\n-12 Mana (Gravity Storm)\nline3", "- 12 Mana (Gravity Storm)", "", "-0 Mana (Gravity Storm)")
	val castTimes = listOf(0L, 1L, 1000L, -5L, Long.MAX_VALUE, Long.MIN_VALUE)
	val nows = listOf(0L, 1000L, 30_000L, 30_999L, 31_000L, 31_001L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)
	val ringArgs = listOf(Triple(0.0, 10.0, 64), Triple(5.5, 3.0, 8), Triple(-100.25, 10.0, 1), Triple(1.0, 0.0, 0), Triple(1.0, 2.0, -1), Triple(1.0, Double.NaN, 4), Triple(1e9, 1e-9, 3))

	val iceCases: List<Triple<BlockPos, Boolean, Set<BlockPos>>> by lazy {
		val d = IceFillFloors.data!!
		val r = Random(2302)
		List(160) {
			val corner = BlockPos(r.nextInt(-200, 200), r.nextInt(60, 80), r.nextInt(-200, 200))
			val rot = r.nextInt(4)
			val air = HashSet<BlockPos>()
			for (floor in d.identifier) {
				val p = r.nextInt(floor.size)
				if (r.nextInt(10) > 0) air.add(PuzzleRoom.toReal(corner, floor[p][0], rot))
				if (r.nextInt(6) == 0) air.add(PuzzleRoom.toReal(corner, floor[p][1], rot))
				if (r.nextInt(4) == 0) { val q = r.nextInt(floor.size); air.add(PuzzleRoom.toReal(corner, floor[q][0], r.nextInt(4))) }
			}
			Triple(corner, r.nextBoolean(), air)
		}
	}
	val badJson = listOf("{}", "{\"identifier\":[],\"easy\":[],\"hard\":[]}", "{\"identifier\":[[[{\"x\":1,\"y\":2,\"z\":3}]]],\"easy\":[],\"hard\":[]}",
		"{\"identifier\":[[[{\"x\":1,\"y\":2}]]],\"easy\":[],\"hard\":[]}", "{\"identifier\":[[[{\"x\":1.5,\"y\":2,\"z\":3}]]],\"easy\":[],\"hard\":[]}", "[]", "",
		"{\"identifier\":[],\"easy\":[]}", "{\"identifier\":{},\"easy\":[],\"hard\":[]}", "{\"identifier\":[[[1]]],\"easy\":[],\"hard\":[]}")
}

typealias DungeonMapGrid = dev.ashtonite365.apex.features.dungeon.DungeonMap.Grid
