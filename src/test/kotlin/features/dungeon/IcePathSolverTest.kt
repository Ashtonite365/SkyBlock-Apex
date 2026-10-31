// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.IcePathRoute
import dev.ashtonite365.apex.features.dungeon.IcePathRoute.Board
import dev.ashtonite365.apex.features.dungeon.IcePathRoute.Cell
import dev.ashtonite365.apex.features.dungeon.IcePathRoute.Dir

class IcePathSolverTest {
	@Test fun syntheticSimpleSlide() {
		val b = Board.parse(
			".....",
			".....",
			"..#..",
		)
		assertEquals(Cell(4, 0), IcePathRoute.slide(b, Cell(0, 0), Dir.RIGHT))
		assertEquals(Cell(2, 1), IcePathRoute.slide(b, Cell(2, 0), Dir.DOWN))
		assertEquals(Cell(0, 2), IcePathRoute.slide(b, Cell(0, 0), Dir.DOWN))
	}

	@Test fun syntheticBlockedMove() {
		val b = Board.parse(
			".#.",
			"...",
		)

		assertEquals(Cell(0, 0), IcePathRoute.slide(b, Cell(0, 0), Dir.RIGHT))
		assertEquals(Cell(0, 0), IcePathRoute.slide(b, Cell(0, 0), Dir.UP))
		assertEquals(Cell(0, 0), IcePathRoute.slide(b, Cell(0, 0), Dir.LEFT))

		val r = IcePathRoute.solve(b, Cell(0, 0), setOf(Cell(2, 0)))!!
		assertEquals(listOf(Dir.DOWN, Dir.RIGHT, Dir.UP), r.hits)
		r.stops.zipWithNext().forEach { (a, c) -> assertTrue(a != c) }
	}

	private val maze = Board.parse(
		"......",
		"......",
		"...#..",
		"#.#...",
		"......",
		"....#.",
	)
	private val mazeExit = setOf(Cell(2, 0))

	@Test fun syntheticShortestRoute() {
		val r = IcePathRoute.solve(maze, Cell(0, 5), mazeExit)!!
		assertEquals(Cell(2, 0), r.stops.last())
		assertEquals(r.hits.size, r.stops.size - 1)
		assertEquals(7, r.hits.size)
		assertEquals(listOf(Dir.UP, Dir.RIGHT, Dir.UP, Dir.LEFT, Dir.DOWN, Dir.RIGHT, Dir.UP), r.hits)

		assertEquals(bruteForceMin(maze, Cell(0, 5), mazeExit, 8), r.hits.size)

		var c = Cell(0, 5); r.hits.forEach { c = IcePathRoute.slide(maze, c, it) }
		assertEquals(Cell(2, 0), c)
	}

	private fun bruteForceMin(b: Board, s: Cell, exits: Set<Cell>, max: Int): Int? {
		var frontier = setOf(s)
		for (d in 0..max) {
			if (frontier.any { it in exits }) return d
			frontier = frontier.flatMap { f -> Dir.entries.map { IcePathRoute.slide(b, f, it) } }.toSet()
		}
		return null
	}

	@Test fun syntheticUnreachableExit() {
		val b = Board.parse(
			"..#..",
			"..#..",
			"..#..",
		)
		assertNull(IcePathRoute.solve(b, Cell(0, 0), setOf(Cell(4, 0))))

		val pass = Board.parse(".....")
		assertNull(IcePathRoute.solve(pass, Cell(0, 0), setOf(Cell(2, 0))))

		assertEquals(0, IcePathRoute.solve(pass, Cell(2, 0), setOf(Cell(2, 0)))!!.hits.size)
	}

	@Test fun syntheticRecomputeAfterMove() {
		val t = IcePathRoute.Tracker()
		assertTrue(t.update(maze, Cell(0, 5), mazeExit))
		val first = t.route!!
		assertFalse(t.update(maze, Cell(0, 5), mazeExit))

		assertTrue(t.update(maze, first.stops[1], mazeExit))
		assertEquals(first.hits.size - 1, t.route!!.hits.size)
		assertEquals(2, t.solves)

		val elsewhere = IcePathRoute.slide(maze, Cell(0, 5), Dir.RIGHT)
		assertEquals(Cell(3, 5), elsewhere)
		assertTrue(t.update(maze, elsewhere, mazeExit))
		assertEquals(elsewhere, t.route!!.stops.first())
		assertEquals(Cell(2, 0), t.route!!.stops.last())

		val changed = Board.parse(".....#", "......", "...#..", "#.#...", "......", "....#.")
		assertTrue(t.update(changed, elsewhere, mazeExit))
	}

	@Test fun syntheticTrailCoversEveryCell() {
		val b = Board.parse(
			"....",
			"#...",
		)
		val r = IcePathRoute.solve(b, Cell(0, 0), setOf(Cell(3, 1)))!!
		val trail = IcePathRoute.trail(r)
		assertEquals(Cell(0, 0), trail.first()); assertEquals(Cell(3, 1), trail.last())
		trail.zipWithNext().forEach { (a, c) -> assertEquals(1, Math.abs(a.col - c.col) + Math.abs(a.row - c.row)) }
	}

	@Test fun syntheticLayoutRoundTrip() {

		val lay = IcePathRoute.Layout(100, 50, 0, -1)
		assertEquals(100 to 54, lay.toWorld(Cell(IcePathRoute.CHEST_COL, 0)))
		for (f in listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)) {
			val l = IcePathRoute.Layout(-7, 13, f.first, f.second)
			for (c in listOf(Cell(0, 0), Cell(16, 0), Cell(3, 9), Cell(16, 16))) {
				val (x, z) = l.toWorld(c)
				assertEquals(c, l.toCell(x, z))
			}
			assertNull(l.toCell(-7, 13))

			val (dx, dz) = l.worldStep(Dir.UP)
			assertEquals(f.first to f.second, dx to dz)
		}
	}

	@Test fun syntheticExitCells() {
		val rows = (0 until IcePathRoute.SIZE).map { r -> if (r == 0) "#######.#.#######" else ".".repeat(IcePathRoute.SIZE) }
		val b = Board.parse(*rows.toTypedArray())
		assertEquals(setOf(Cell(7, 0), Cell(9, 0)), IcePathRoute.exitCells(b))
		assertNotNull(IcePathRoute.solve(b, Cell(7, 16), IcePathRoute.exitCells(b)))
	}
}
