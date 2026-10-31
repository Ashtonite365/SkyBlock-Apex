/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Ice Path (silverfish) puzzle, pure logic, written from scratch and unit-tested on synthetic boards.
 * Hitting the silverfish sends it sliding in one direction until the next cell is a block or the board edge. A
 * breadth-first search over those slide moves gives the fewest hits from the silverfish's cell to an exit cell.
 * Room layout facts (17x17 board at the reward chest's height, the chest beside the board's row-0 edge, the exit
 * cells on row 0 next to the chest's column) were learned from other dungeon
 * mods (see CREDITS.md); no code copied.
 */
package dev.ashtonite365.apex.features.dungeon

object IcePathRoute {
	data class Cell(val col: Int, val row: Int)

	enum class Dir(val dc: Int, val dr: Int) { UP(0, -1), RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0) }

	class Board(val width: Int, val height: Int, private val blocked: BooleanArray) {
		init { require(blocked.size == width * height) }
		fun inside(c: Cell) = c.col in 0 until width && c.row in 0 until height
		fun isBlocked(c: Cell) = !inside(c) || blocked[c.row * width + c.col]
		override fun equals(other: Any?) = other is Board && other.width == width && other.height == height && other.blocked.contentEquals(blocked)
		override fun hashCode() = blocked.contentHashCode() * 31 + width

		companion object {
			fun of(width: Int, height: Int, blocked: (Cell) -> Boolean) =
				Board(width, height, BooleanArray(width * height) { blocked(Cell(it % width, it / width)) })

			fun parse(vararg rows: String): Board {
				val w = rows.maxOf { it.length }
				return of(w, rows.size) { c -> rows[c.row].getOrElse(c.col) { '#' } == '#' }
			}
		}
	}

	fun slide(board: Board, from: Cell, dir: Dir): Cell {
		var c = from
		while (true) {
			val n = Cell(c.col + dir.dc, c.row + dir.dr)
			if (board.isBlocked(n)) return c
			c = n
		}
	}

	data class Route(val stops: List<Cell>, val hits: List<Dir>) {
		val next: Dir? get() = hits.firstOrNull()
	}

	fun solve(board: Board, start: Cell, exits: Set<Cell>): Route? {
		if (!board.inside(start) || board.isBlocked(start)) return null
		if (start in exits) return Route(listOf(start), emptyList())
		val prev = HashMap<Cell, Pair<Cell, Dir>>()
		val queue = ArrayDeque<Cell>().apply { add(start) }
		val seen = hashSetOf(start)
		while (queue.isNotEmpty()) {
			val c = queue.removeFirst()
			for (d in Dir.entries) {
				val n = slide(board, c, d)
				if (n == c || !seen.add(n)) continue
				prev[n] = c to d
				if (n in exits) return backtrack(start, n, prev)
				queue.addLast(n)
			}
		}
		return null
	}

	private fun backtrack(start: Cell, end: Cell, prev: Map<Cell, Pair<Cell, Dir>>): Route {
		val stops = ArrayDeque<Cell>(); val hits = ArrayDeque<Dir>()
		var c = end
		while (c != start) { val (p, d) = prev.getValue(c); stops.addFirst(c); hits.addFirst(d); c = p }
		stops.addFirst(start)
		return Route(stops.toList(), hits.toList())
	}

	fun trail(route: Route): List<Cell> {
		val out = mutableListOf(route.stops.first())
		for ((i, d) in route.hits.withIndex()) {
			var c = route.stops[i]
			while (c != route.stops[i + 1]) { c = Cell(c.col + d.dc, c.row + d.dr); out += c }
		}
		return out
	}

	class Tracker {
		var route: Route? = null
			private set
		private var lastAt: Cell? = null
		private var lastBoard: Board? = null
		var solves = 0
			private set

		fun update(board: Board, at: Cell, exits: Set<Cell>): Boolean {
			if (at == lastAt && board == lastBoard) return false
			lastAt = at; lastBoard = board
			route = solve(board, at, exits); solves++
			return true
		}

		fun reset() { route = null; lastAt = null; lastBoard = null }
	}

	data class Layout(val chestX: Int, val chestZ: Int, val fx: Int, val fz: Int) {
		init { require(Math.abs(fx) + Math.abs(fz) == 1) }

		private val cx get() = -fz
		private val cz get() = fx
		private val originX get() = chestX - fx * ROW0_OFFSET - cx * CHEST_COL
		private val originZ get() = chestZ - fz * ROW0_OFFSET - cz * CHEST_COL

		fun toWorld(c: Cell): Pair<Int, Int> = (originX + cx * c.col - fx * c.row) to (originZ + cz * c.col - fz * c.row)

		fun toCell(x: Int, z: Int): Cell? {
			val dx = x - originX; val dz = z - originZ
			val c = Cell(dx * cx + dz * cz, -(dx * fx + dz * fz))
			return c.takeIf { it.col in 0 until SIZE && it.row in 0 until SIZE }
		}

		fun worldStep(d: Dir): Pair<Int, Int> = (cx * d.dc - fx * d.dr) to (cz * d.dc - fz * d.dr)
	}

	const val SIZE = 17
	const val ROW0_OFFSET = 4
	const val CHEST_COL = 8

	fun exitCells(board: Board): Set<Cell> =
		(CHEST_COL - 1..CHEST_COL + 1).map { Cell(it, 0) }.filter { !board.isBlocked(it) }.toSet()
}
