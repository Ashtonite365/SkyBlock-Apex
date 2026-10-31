package dev.ashtonite365.apex.test.features.dungeon.waterboard

import dev.ashtonite365.apex.features.dungeon.waterboard.Board
import dev.ashtonite365.apex.features.dungeon.waterboard.Gate
import dev.ashtonite365.apex.features.dungeon.waterboard.P
import dev.ashtonite365.apex.features.dungeon.waterboard.WaterBoardReader

object WaterBoardFixtures {

	val REFERENCE = listOf(
		"#######S#######",
		"#.Q.GDE.#######",
		"#.#####c#######",
		"#.#####.#######",
		"#.#####d#######",
		"#.##.c..#######",
		"#.##.##E#######",
		"#.##....#######",
		"#.#####.#######",
		"#P#####B#######",
	)

	val COMB = listOf(
		"####S####",
		"#.......#",
		"##Q#G#C##",
		"##.#.#.##",
		"##.#.#.##",
		"##.#.#.##",
		"##P#B#R##",
	)

	fun board(rows: List<String>) = Board.parse(rows)

	const val Y0 = 64
	const val X0 = 20

	fun world(rows: List<String>, extraRowWool: Map<Char, Int> = mapOf('O' to 10, 'L' to 12, 'R' to 14), waterOn: Boolean = false): WaterBoardReader.MapView {
		val w = WaterBoardReader.MapView()
		val h = rows.size
		val width = rows.maxOf { it.length }

		for (x in X0 - width - 14..X0 + 14) for (y in Y0 - 2..Y0 + 40) for (z in 1..6) w.set(P(x, y, z), "stone_bricks")
		for (x in X0 - width + 1..X0) for (y in Y0..Y0 + h) w.set(P(x, y, 1), "glass")

		for (x in X0 - width - 14..X0 + 14) for (z in -12..0) w.set(P(x, Y0 - 1, z), "stone")
		val rowWool = mutableMapOf<Char, Int>()
		for ((r, row) in rows.withIndex()) {
			val y = Y0 + (h - 1 - r) + 1
			for ((c, ch) in row.withIndex()) {
				val x = X0 - c
				val gate = Gate.ofLetter(ch)
				when {
					ch == '#' -> {}
					ch in "POBLR" -> { rowWool[ch] = c; w.set(P(x, y - 1, 2), "air"); w.set(P(x, y, 2), "air") }
					gate != null && ch.isUpperCase() -> w.set(P(x, y, 2), gate.blockId)
					gate != null -> { w.set(P(x, y, 2), "air"); w.set(P(x, y, 3), gate.blockId) }
					else -> w.set(P(x, y, 2), "air")
				}
			}
		}

		val woolId = mapOf('P' to "purple_wool", 'O' to "orange_wool", 'B' to "blue_wool", 'L' to "lime_wool", 'R' to "red_wool")
		for ((ch, c) in rowWool + extraRowWool.filterKeys { it !in rowWool }) w.set(P(X0 - c, Y0, 0), woolId.getValue(ch))

		for ((i, ch) in rowWool.keys.withIndex()) { w.set(P(X0 - 2 * i, Y0 - 1, -3), woolId.getValue(ch)); w.set(P(X0 - 2 * i, Y0, -3), "glass") }

		for ((i, g) in Gate.entries.withIndex()) {
			w.set(P(X0 - 2 * i, Y0 - 1, -5), g.blockId)
			w.set(P(X0 - 2 * i, Y0, -5), "lever", mapOf("face" to "floor", "facing" to "north", "powered" to "false"))
		}
		w.set(P(X0 - 5, Y0, -8), "lever", mapOf("face" to "floor", "facing" to "north", "powered" to waterOn.toString()))
		return w
	}

	fun flip(w: WaterBoardReader.MapView, gate: Gate, xs: IntRange, ys: IntRange) {
		for (x in xs) for (y in ys) {
			val inLayer = w.id(x, y, 2) == gate.blockId
			val behind = w.id(x, y, 3) == gate.blockId
			if (inLayer) { w.set(P(x, y, 2), "air"); w.set(P(x, y, 3), gate.blockId) }
			else if (behind && w.id(x, y, 2) == "air") { w.set(P(x, y, 3), "stone_bricks"); w.set(P(x, y, 2), gate.blockId) }
		}
	}
}
