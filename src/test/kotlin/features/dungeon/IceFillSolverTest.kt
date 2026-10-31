// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.IceFillPath
import dev.ashtonite365.apex.features.dungeon.IceFillPath.Cell
import dev.ashtonite365.apex.features.dungeon.IceFillPath.Edge
import dev.ashtonite365.apex.features.dungeon.IceFillPath.Ends
import dev.ashtonite365.apex.features.dungeon.IceFillPath.Result
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers

class IceFillSolverTest {

	private class Board(vararg rows: String) {
		val tiles = mutableSetOf<Cell>(); var start: Cell? = null; var exit: Cell? = null
		init {
			rows.forEachIndexed { z, row -> row.forEachIndexed { x, ch ->
				val c = Cell(x, z)
				when (ch) { '.' -> { tiles += c }; 'S' -> { tiles += c; start = c }; 'E' -> { tiles += c; exit = c }; else -> {} }
			} }
		}
	}

	private fun assertCovers(b: Board, r: Result) {
		assertTrue(r is Result.Path, "expected a path, got $r")
		val p = (r as Result.Path).cells
		assertEquals(b.tiles.size, p.size, "covers every tile")
		assertEquals(b.tiles, p.toSet(), "each tile exactly once")
		assertEquals(b.start, p.first()); assertEquals(b.exit, p.last(), "ends at the exit")
		assertTrue(IceFillPath.isValid(p, b.tiles, b.start, b.exit))
	}

	private val three = Board(
		"#S.",
		"...",
		"..#",
		" E ",
	)
	private val five = Board(
		"..S..",
		".....",
		".....",
		".....",
		".....",
		"  E  ",
	)
	private val seven = Board(
		"...S...",
		".......",
		".......",
		".#.....",
		".......",
		".....#.",
		".......",
		"   E   ",
	)

	@Test fun synthetic3x3() = assertCovers(three, IceFillPath.solve(three.tiles, three.start!!, three.exit))
	@Test fun synthetic5x5() = assertCovers(five, IceFillPath.solve(five.tiles, five.start!!, five.exit))
	@Test fun synthetic7x7() = assertCovers(seven, IceFillPath.solve(seven.tiles, seven.start!!, seven.exit))

	@Test fun syntheticIrregularWithObstacles() {
		val b = Board(
			"..S..",
			"...#.",
			".#...",
			".....",
			".....",
			"  E  ",
		)
		assertCovers(b, IceFillPath.solve(b.tiles, b.start!!, b.exit))
		val l = Board(
			"S...",
			"....",
			"..",
			"..",
			".E",
		)
		assertCovers(l, IceFillPath.solve(l.tiles, l.start!!, l.exit))
	}

	@Test fun syntheticLargeBoardIsFast() {
		val rows = (0 until 11).map { r -> if (r == 0) "E" + ".".repeat(10) else ".".repeat(10) + if (r == 10) "S" else "." }
		val b = Board(*rows.toTypedArray())
		val t0 = System.nanoTime()
		assertCovers(b, IceFillPath.solve(b.tiles, b.start!!, b.exit))
		assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000, "11x11 solved quickly")
	}

	@Test fun syntheticUnsolvableParity() {

		val b = Board("S.", ".E")
		val r = IceFillPath.solve(b.tiles, b.start!!, b.exit)
		assertTrue(r is Result.Unsolvable, "$r")

		val full = Board(".S.", "...", "...", " E ")
		assertNotNull(IceFillPath.parityProblem(full.tiles, full.start!!, full.exit))
		assertTrue(IceFillPath.solve(full.tiles, full.start!!, full.exit) is Result.Unsolvable)
	}

	@Test fun syntheticUnsolvable3x3Shape() {

		val b = Board(".S.", "...", "#.#", " E ")
		assertNull(IceFillPath.parityProblem(b.tiles, b.start!!, b.exit))
		assertTrue(IceFillPath.solve(b.tiles, b.start!!, b.exit) is Result.Unsolvable)

		val plan = IceFillPath.solveLayer(b.tiles, listOf(Ends(b.start!!, b.exit)))!!
		assertTrue(plan.guessed)
		assertTrue(IceFillPath.isValid(plan.path, b.tiles))
	}

	@Test fun syntheticUnsolvableDeadEndPassesParity() {

		val b = Board(
			".  .",
			"S..E",
		)
		assertNull(IceFillPath.parityProblem(b.tiles, b.start!!, b.exit))
		assertTrue(IceFillPath.solve(b.tiles, b.start!!, b.exit) is Result.Unsolvable)
	}

	@Test fun syntheticDisconnected() {
		val b = Board("S.#..E")
		assertTrue(IceFillPath.solve(b.tiles, b.start!!, b.exit) is Result.Unsolvable)
	}

	@Test fun syntheticEndsFromEdges() {

		val b = three
		val edge = { _: Cell, o: Cell -> when { o.z < 0 -> Edge.LEVEL; o.z > 3 -> Edge.STEP_UP; else -> Edge.WALL } }
		val ends = IceFillPath.detectEnds(b.tiles, edge)
		assertEquals(Ends(b.start!!, b.exit), ends.first())
		val plan = IceFillPath.solveLayer(b.tiles, ends)!!
		assertFalse(plan.fromBundle)
		assertTrue(IceFillPath.isValid(plan.path, b.tiles, b.start, b.exit))
	}

	@Test fun syntheticEndsIgnoreInteriorStepsAndFallBack() {
		val b = three

		val ends = IceFillPath.detectEnds(b.tiles, { _, _ -> Edge.STEP_UP }, standingOn = b.start)
		assertEquals(Ends(b.start!!, b.exit), ends.first())

		val ends2 = IceFillPath.detectEnds(b.tiles, { _, _ -> Edge.WALL }, standingOn = b.start)
		assertEquals(Ends(b.start!!, b.exit), ends2.first())

		val side = { _: Cell, o: Cell -> when { o.z < 0 || o.x < 0 -> Edge.LEVEL; o.z > 3 -> Edge.STEP_UP; else -> Edge.WALL } }
		assertEquals(Ends(b.start!!, b.exit), IceFillPath.detectEnds(b.tiles, side).first())
	}

	private fun corners(p: List<Cell>): List<Cell> =
		listOf(p.first()) + p.indices.drop(1).dropLast(1).filter { i ->
			(p[i - 1].x == p[i].x) != (p[i].x == p[i + 1].x)
		}.map { p[it] } + p.last()

	@Test fun syntheticBundledMatchVsCompute() {
		val b = three
		val ends = listOf(Ends(b.start!!, b.exit))
		val full = (IceFillPath.solve(b.tiles, b.start!!, b.exit) as Result.Path).cells

		val other = IceFillPath.expand(listOf(Cell(0, 0), Cell(5, 0)))!!

		val stored = IceFillPath.expand(corners(full.reversed()))!!
		assertEquals(full.reversed(), stored)
		val matched = IceFillPath.solveLayer(b.tiles, ends, listOf(other, stored))!!
		assertTrue(matched.fromBundle)
		assertEquals(full, matched.path)

		val partial = full.dropLast(1)
		val computed = IceFillPath.solveLayer(b.tiles, ends, listOf(other, partial))!!
		assertFalse(computed.fromBundle)
		assertTrue(IceFillPath.isValid(computed.path, b.tiles, b.start, b.exit))

		assertFalse(IceFillPath.solveLayer(b.tiles, ends)!!.fromBundle)
	}

	@Test fun syntheticExpandRejectsDiagonal() {
		assertNull(IceFillPath.expand(listOf(Cell(0, 0), Cell(1, 1))))
		assertEquals(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 2)), IceFillPath.expand(listOf(Cell(0, 0), Cell(0, 2), Cell(1, 2))))
	}

	@Test fun syntheticProgressAndReplan() {
		val b = five
		val plan = (IceFillPath.solve(b.tiles, b.start!!, b.exit) as Result.Path).cells
		assertEquals(plan, IceFillPath.remaining(plan, emptySet(), null))

		val walked = plan.take(4).toSet()
		assertEquals(plan.drop(3), IceFillPath.remaining(plan, walked, plan[3]))

		assertEquals(plan.drop(3), IceFillPath.remaining(plan, plan.take(3).toSet(), plan[3]))

		val (i, off) = (0 until plan.size - 2).firstNotNullOf { i ->
			plan[i].neighbours().firstOrNull { it in b.tiles && it !in plan.take(i + 2) }?.let { i to it }
		}
		val before = plan.take(i + 1).toSet()
		val deviated = before + off
		assertNull(IceFillPath.remaining(plan, deviated, off))
		val r = IceFillPath.replan(b.tiles, deviated, off, b.exit)
		if (r is Result.Path) {
			assertEquals(off, r.cells.first()); assertEquals(b.exit, r.cells.last())
			assertEquals(b.tiles - before, r.cells.toSet())
		} else assertTrue(r is Result.Unsolvable)
	}

	@Test fun syntheticRoomGate() {
		assertTrue(IceFillPath.looksLikeRoom(listOf(69, 70, 71)))
		assertFalse(IceFillPath.looksLikeRoom(listOf(69)))
		assertFalse(IceFillPath.looksLikeRoom(listOf(60, 69)))
	}

	@Test fun oldIceFillToggleMigrates() {
		val on = PuzzleSolvers.foldOldIceFill(JsonObject(mapOf("ice-fill" to JsonPrimitive(true), "ice-fill-bundled" to JsonPrimitive(true))))
		assertEquals(JsonObject(mapOf("ice-fill-bundled" to JsonPrimitive(true), "ice-fill-solver" to JsonPrimitive(true))), on)
		val off = PuzzleSolvers.foldOldIceFill(JsonObject(mapOf("ice-fill" to JsonPrimitive(false))))
		assertEquals(JsonObject(emptyMap()), off)
		val untouched = JsonObject(mapOf("ice-fill-colour" to JsonPrimitive(1)))
		assertEquals(untouched, PuzzleSolvers.foldOldIceFill(untouched))
	}
}
