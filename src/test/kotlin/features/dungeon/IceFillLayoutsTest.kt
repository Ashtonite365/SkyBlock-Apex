// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

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
import dev.ashtonite365.apex.features.kit.puzzles.IceFillFloors
import kotlin.random.Random

class IceFillLayoutsTest {

	private fun validCover(path: List<Cell>, tiles: Set<Cell>, start: Cell? = null, exit: Cell? = null): Boolean {
		if (path.size != tiles.size) return false
		val seen = HashSet<Cell>()
		for ((i, c) in path.withIndex()) {
			if (c !in tiles || !seen.add(c)) return false
			if (i > 0) { val p = path[i - 1]; if (Math.abs(p.x - c.x) + Math.abs(p.z - c.z) != 1) return false }
		}
		return (start == null || path.first() == start) && (exit == null || path.last() == exit)
	}

	private class Layout(val floor: Int, val pattern: Int, val easy: List<Cell>, val hard: List<Cell>) {
		val tiles = easy.toSet(); val start = easy.first(); val exit = easy.last()
		override fun toString() = "floor $floor pattern $pattern"
	}

	private val layouts: List<Layout> by lazy {
		val data = IceFillFloors.parse(javaClass.getResourceAsStream("/assets/apex/kit/puzzles/ice-fill-floors.json")!!.reader().readText())
		(0 until 3).flatMap { f -> data.easy[f].indices.map { p ->
			Layout(f, p, IceFillPath.expand(data.easy[f][p].map { Cell(it.x, it.z) })!!, IceFillPath.expand(data.hard[f][p].map { Cell(it.x, it.z) })!!)
		} }
	}

	private val symmetries: List<Pair<(Cell) -> Cell, (Cell) -> Cell>> = listOf(
		{ c: Cell -> c } to { c: Cell -> c },
		{ c: Cell -> Cell(-c.z, c.x) } to { c: Cell -> Cell(c.z, -c.x) },
		{ c: Cell -> Cell(-c.x, -c.z) } to { c: Cell -> Cell(-c.x, -c.z) },
		{ c: Cell -> Cell(c.z, -c.x) } to { c: Cell -> Cell(-c.z, c.x) },
		{ c: Cell -> Cell(-c.x, c.z) } to { c: Cell -> Cell(-c.x, c.z) },
		{ c: Cell -> Cell(c.x, -c.z) } to { c: Cell -> Cell(c.x, -c.z) },
		{ c: Cell -> Cell(c.z, c.x) } to { c: Cell -> Cell(c.z, c.x) },
		{ c: Cell -> Cell(-c.z, -c.x) } to { c: Cell -> Cell(-c.z, -c.x) },
	)

	private fun models(l: Layout): Map<String, (Cell, Cell) -> Edge> {
		val minZ = l.tiles.minOf { it.z }; val maxZ = l.tiles.maxOf { it.z }
		return linkedMapOf(
			"ideal" to { _, o -> when { o.z < minZ -> Edge.LEVEL; o.z > maxZ -> Edge.STEP_UP; else -> Edge.WALL } },

			"raisedLedge" to { _, o -> when { o.z < minZ || o.z > maxZ -> Edge.STEP_UP; else -> Edge.WALL } },
			"entryHole" to { _, o -> when { o.z < minZ -> Edge.NONE; o.z > maxZ -> Edge.STEP_UP; else -> Edge.WALL } },
			"walkableLedgeAround" to { _, o -> if (o.z > maxZ) Edge.STEP_UP else Edge.LEVEL },
			"everythingStep" to { _, _ -> Edge.STEP_UP },
			"everythingWall" to { _, _ -> Edge.WALL },
			"stepUnseen" to { _, o -> if (o.z < minZ) Edge.LEVEL else Edge.WALL },
		)
	}

	@Test
	fun everyBundledLayoutSolvesInEveryOrientationAndRoomReading() {
		assertEquals(15, layouts.size)
		var checked = 0
		for (l in layouts) for ((fwd, inv) in symmetries) for ((name, m) in models(l)) {
			val tiles = l.tiles.mapTo(HashSet(), fwd)
			val s = fwd(l.start); val e = fwd(l.exit)
			val upperRaw = Cell(l.exit.x, l.tiles.maxOf { it.z } + 5)
			val up = fwd(upperRaw).let { it.x + 0.5 to it.z + 0.5 }
			val ends = IceFillPath.detectEnds(tiles, { t, o -> m(inv(t), inv(o)) }, null, up)
			val plan = IceFillPath.solveLayer(tiles, ends)
			assertNotNull(plan, "$l $name")
			assertTrue(validCover(plan!!.path, tiles, s, e), "$l $name: ${plan.path}")
			assertFalse(plan.guessed, "$l $name")

			val withBundle = IceFillPath.solveLayer(tiles, ends, listOf(l.hard.map(fwd), l.easy.map(fwd)))!!
			assertTrue(withBundle.fromBundle && validCover(withBundle.path, tiles, s, e), "$l $name bundled")
			val hardOnly = IceFillPath.solveLayer(tiles, ends, listOf(l.hard.map(fwd)))!!
			assertTrue(validCover(hardOnly.path, tiles, s, e), "$l $name hard only")
			assertEquals(validCover(l.hard, l.tiles), hardOnly.fromBundle, "$l $name hard verified")
			checked++
		}
		assertEquals(15 * 8 * 7, checked)
	}

	@Test
	fun raisedLedgeLayoutsThatUsedToFail() {
		val failedBefore = layouts.filter { !validCover(it.hard, it.tiles) }
		assertEquals(listOf("floor 1 pattern 1", "floor 1 pattern 5", "floor 2 pattern 0", "floor 2 pattern 2"), failedBefore.map { it.toString() })
		for (l in failedBefore) {
			val minZ = l.tiles.minOf { it.z }; val maxZ = l.tiles.maxOf { it.z }
			val edge = { _: Cell, o: Cell -> if (o.z < minZ || o.z > maxZ) Edge.STEP_UP else Edge.WALL }
			val ends = IceFillPath.detectEnds(l.tiles, edge)
			assertTrue(ends.isNotEmpty(), "$l: start/exit candidates")
			assertEquals(Ends(l.start, l.exit), ends.first(), "$l")
			val plan = IceFillPath.solveLayer(l.tiles, ends, listOf(l.hard))!!
			assertFalse(plan.fromBundle)
			assertTrue(validCover(plan.path, l.tiles, l.start, l.exit), "$l")

			val g = IceFillPath.guide(plan, emptySet(), null) { error("no replan when off the ice") }
			assertEquals(plan.path, g.route); assertFalse(g.fromPlayer); assertNull(g.hint)
		}
	}

	@Test
	fun noEdgesAtAllStillGivesACoveringPath() {
		for (l in layouts) {
			val plan = IceFillPath.solveLayer(l.tiles, emptyList())
			assertNotNull(plan, "$l")
			assertTrue(validCover(plan!!.path, l.tiles), "$l")
			assertTrue(plan.guessed)
		}
	}

	@Test
	fun guideFromHereFallsBackToTheStart() {
		val l = layouts.first { it.floor == 1 && it.pattern == 0 }
		val plan = IceFillPath.solveLayer(l.tiles, listOf(Ends(l.start, l.exit)))!!
		val p = plan.path
		fun replanner(walked: Set<Cell>, at: Cell) = { (IceFillPath.replan(l.tiles, walked, at, l.exit) as? Result.Path)?.cells }

		val w = p.take(5).toSet()
		assertEquals(p.drop(4), IceFillPath.guide(plan, w, p[4], replanner(w, p[4])).route)

		for (t in l.tiles) {
			val g = IceFillPath.guide(plan, emptySet(), t, replanner(emptySet(), t))
			if (t == l.start) { assertEquals(p, g.route); continue }
			val direct = IceFillPath.solve(l.tiles, t, l.exit)
			if (direct is Result.Path) {
				assertTrue(g.fromPlayer && validCover(g.route, l.tiles, t, l.exit), "from $t")
				assertNull(g.hint)
			} else {
				assertTrue(direct is Result.Unsolvable, "complete search from $t: $direct")
				assertEquals(p, g.route); assertFalse(g.fromPlayer); assertEquals(IceFillPath.HINT_GO_BACK, g.hint)
			}
		}

		val (i, off) = (1 until p.size - 2).firstNotNullOf { i -> p[i].neighbours().firstOrNull { it in l.tiles && it !in p.take(i + 2) }?.let { i to it } }
		val deviated = p.take(i + 1).toSet() + off
		val g = IceFillPath.guide(plan, deviated, null) { error("off the ice") }
		assertEquals(p, g.route); assertEquals(IceFillPath.HINT_RESET, g.hint)
	}

	private fun randomBoard(rnd: Random, w: Int, h: Int, from: Cell? = null): List<Cell> {
		val start = from ?: Cell(rnd.nextInt(w), rnd.nextInt(h))
		val walk = mutableListOf(start); val used = hashSetOf(start)
		fun free(c: Cell) = c.x in 0 until w && c.z in 0 until h && c !in used
		while (true) {
			val opts = walk.last().neighbours().filter(::free)
			if (opts.isEmpty()) break

			val next = if (rnd.nextInt(4) == 0) opts.random(rnd) else opts.minBy { c -> c.neighbours().count(::free) * 10 + rnd.nextInt(10) }
			walk += next; used += next
		}
		return walk
	}

	@Test
	fun randomSolvableBoardsOfEveryLayerSize() {
		val rnd = Random(20261025)
		var boards = 0
		val t0 = System.nanoTime()
		for (n in 3..7) {
			var made = 0
			while (made < 150) {
				val walk = randomBoard(rnd, n, n)
				if (walk.size < maxOf(4, n * n * 6 / 10)) continue
				val tiles = walk.toSet(); val s = walk.first(); val e = walk.last()
				val r = IceFillPath.solve(tiles, s, e)
				assertTrue(r is Result.Path, "n=$n ${IceFillPath.describe(tiles, emptySet(), Ends(s, e))}: $r")
				assertTrue(validCover((r as Result.Path).cells, tiles, s, e))

				val open = IceFillPath.solveLayer(tiles, emptyList())
				assertNotNull(open); assertTrue(validCover(open!!.path, tiles))
				made++; boards++
			}
		}

		for (n in listOf(3, 5, 7)) {
			var made = 0; var tries = 0
			while (made < 40 && tries < 200_000) {
				tries++

				val full = randomBoard(rnd, n, n, Cell(n / 2, 0))
				val cut = full.indexOf(Cell(n / 2, n - 1))
				if (cut < 0 || cut + 1 < n * n / 2) continue
				val walk = full.subList(0, cut + 1)
				val e = Cell(n / 2, n)
				val tiles = walk.toSet() + e
				val edge = { _: Cell, o: Cell -> when { o.z < 0 -> Edge.STEP_UP; o.z > n -> Edge.STEP_UP; else -> Edge.WALL } }
				val ends = IceFillPath.detectEnds(tiles, edge, null, n / 2 + 0.5 to n + 5.0)
				val plan = IceFillPath.solveLayer(tiles, ends)
				assertNotNull(plan, "n=$n ${IceFillPath.describe(tiles)}")
				assertTrue(validCover(plan!!.path, tiles, exit = e), "n=$n ${IceFillPath.describe(tiles)}")
				made++; boards++
			}
			assertEquals(40, made, "generated Hypixel-shaped $n x $n boards")
		}
		val ms = (System.nanoTime() - t0) / 1_000_000
		assertTrue(ms < 60_000, "$boards boards in $ms ms")
	}

	private fun bruteHas(tiles: Set<Cell>, start: Cell?, exit: Cell?): Boolean {
		val used = HashSet<Cell>()
		fun go(c: Cell): Boolean {
			used += c
			if (used.size == tiles.size) { val ok = exit == null || c == exit; used -= c; return ok }
			for (nb in c.neighbours()) if (nb in tiles && nb !in used && go(nb)) { used -= c; return true }
			used -= c; return false
		}
		return (if (start != null) listOf(start) else tiles.toList()).any { go(it) }
	}

	@Test
	fun noPathOnlyWhenTrulyImpossible() {
		val rnd = Random(7)
		var solvable = 0; var impossible = 0
		repeat(600) {
			val w = 2 + rnd.nextInt(3); val h = 2 + rnd.nextInt(3)
			val tiles = HashSet<Cell>()
			for (x in 0 until w) for (z in 0 until h) if (rnd.nextInt(5) != 0) tiles += Cell(x, z)
			if (rnd.nextBoolean()) tiles += Cell(w / 2, h)
			if (tiles.isEmpty()) return@repeat
			val list = tiles.toList()
			val s = list.random(rnd); val e = list.random(rnd).takeIf { it != s }
			val want = bruteHas(tiles, s, e)
			val r = IceFillPath.solve(tiles, s, e)
			assertFalse(r is Result.GaveUp)
			assertEquals(want, r is Result.Path, "${IceFillPath.describe(tiles, emptySet(), Ends(s, e))}: $r")
			if (r is Result.Path) assertTrue(validCover(r.cells, tiles, s, e))
			val anyWant = bruteHas(tiles, null, null)
			val plan = IceFillPath.solveLayer(tiles, listOf(Ends(s, e)))
			assertEquals(anyWant, plan != null, IceFillPath.describe(tiles))
			if (plan != null) assertTrue(validCover(plan.path, tiles))
			if (anyWant) solvable++ else impossible++
		}
		assertTrue(solvable > 100 && impossible > 50, "both kinds covered: $solvable / $impossible")
	}

	@Test
	fun cancelledSearchStops() {
		val tiles = (0 until 12).flatMap { x -> (0 until 12).map { z -> Cell(x, z) } }.toSet()
		assertEquals(Result.GaveUp, IceFillPath.solve(tiles, Cell(0, 0), Cell(11, 0), budget = 10))
		assertNull(IceFillPath.solveLayer(tiles, listOf(Ends(Cell(0, 0), Cell(11, 0))), cancelled = { true }))
	}
}
