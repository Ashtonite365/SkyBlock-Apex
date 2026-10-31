/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Ice Fill puzzle, pure logic (no Minecraft world access, unit-tested on synthetic boards).
 * Written from scratch from the puzzle's rules: a layer is a set of floor tiles; you step on every tile exactly once
 * and leave on the exit tile, so the answer is a Hamiltonian path start -> exit on the 4-connected tile grid.
 *
 * [solve] is a complete depth-first search (it either returns a path, proves none exists, or reports that its
 * safety budget ran out). Pruning, all sound:
 *  - parity: the grid is bipartite, so the colour counts of the tiles still to cover (and the colours of the head
 *    and the exit) must agree; checked at every node, not only once;
 *  - connectivity: every unvisited tile must be reachable from the head through unvisited tiles;
 *  - dead ends: an unvisited tile with one free neighbour must be an end of the remaining path, so it is entered
 *    next (when beside the head) or is the exit; two such tiles, or a tile with no free neighbour, is fatal;
 *  - the exit is entered only as the very last tile;
 *  - memo: a (visited set, head) state already proven hopeless is never searched again;
 *  - Warnsdorff ordering (fewest onward moves first), so a solution is usually found without backtracking.
 * Bundled patterns are used through [matchBundled] only after they are verified as such a path on the live layer.
 * [solveLayer] never gives up on a solvable layer: after the detected start/exit pairs it falls back to every
 * possible start with an open end, which finds a path whenever any Hamiltonian path of the layer exists.
 */
package dev.ashtonite365.apex.features.dungeon

object IceFillPath {
	data class Cell(val x: Int, val z: Int) {
		fun neighbours() = listOf(Cell(x, z - 1), Cell(x + 1, z), Cell(x, z + 1), Cell(x - 1, z))
		fun isNextTo(o: Cell) = Math.abs(x - o.x) + Math.abs(z - o.z) == 1
	}

	sealed interface Result {

		data class Path(val cells: List<Cell>) : Result
		data class Unsolvable(val reason: String) : Result

		data object GaveUp : Result
	}

	const val DEFAULT_BUDGET = 5_000_000
	private const val MEMO_LIMIT = 1_000_000

	private fun colour(c: Cell) = Math.floorMod(c.x + c.z, 2)

	fun solve(tiles: Set<Cell>, start: Cell, exit: Cell?, budget: Int = DEFAULT_BUDGET, cancelled: () -> Boolean = { false }): Result {
		if (start !in tiles) return Result.Unsolvable("start is not a tile")
		if (exit != null && exit !in tiles) return Result.Unsolvable("exit is not a tile")
		if (tiles.size == 1) return if (exit == null || exit == start) Result.Path(listOf(start)) else Result.Unsolvable("single tile")
		if (exit == start) return Result.Unsolvable("start equals exit")
		val cells = tiles.sortedWith(compareBy({ it.z }, { it.x }))
		val index = HashMap<Cell, Int>(cells.size * 2).apply { cells.forEachIndexed { i, c -> put(c, i) } }
		val n = cells.size
		val adj = Array(n) { i -> cells[i].neighbours().mapNotNull { index[it] }.toIntArray() }
		val col = IntArray(n) { colour(cells[it]) }
		val s = index.getValue(start)
		val e = exit?.let { index.getValue(it) } ?: -1
		parityProblem(cells, start, exit)?.let { return Result.Unsolvable(it) }
		val visited = BooleanArray(n)
		val words = (n + 63) / 64
		val bits = LongArray(words)
		val path = IntArray(n)
		val left = IntArray(2)
		for (i in 0 until n) left[col[i]]++
		fun mark(v: Int, on: Boolean) {
			visited[v] = on; left[col[v]] += if (on) -1 else 1
			if (on) bits[v shr 6] = bits[v shr 6] or (1L shl (v and 63)) else bits[v shr 6] = bits[v shr 6] and (1L shl (v and 63)).inv()
		}
		mark(s, true); path[0] = s
		if (!connectedFrom(s, adj, visited, n - 1)) return Result.Unsolvable("layer is not connected")
		var budgetLeft = budget
		val queue = IntArray(n)
		val seen = BooleanArray(n)
		val dead = HashSet<State>()

		fun check(head: Int, count: Int): Int {

			val hc = col[head]
			val same = left[hc]; val other = left[1 - hc]

			if (other != same && other != same + 1) return -2
			if (e >= 0) {

				val lastColour = if ((same + other) % 2 == 1) 1 - hc else hc
				if (col[e] != lastColour) return -2
			}
			var forced = -1
			var looseEnds = 0
			for (u in 0 until n) {
				if (visited[u]) continue
				var deg = 0
				var nearHead = false
				for (v in adj[u]) if (!visited[v]) deg++ else if (v == head) { deg++; nearHead = true }
				if (deg == 0) return -2
				if (deg == 1 && u != e && count + 1 < n) {
					if (e < 0) {

						if (nearHead) { if (forced >= 0) return -2; forced = u }
						else if (++looseEnds > 1) return -2
					} else if (nearHead) { if (forced >= 0) return -2; forced = u }
					else return -2
				}
			}

			java.util.Arrays.fill(seen, false)
			var qh = 0; var qt = 0; var reached = 0
			seen[head] = true; queue[qt++] = head
			while (qh < qt) {
				val c = queue[qh++]
				for (v in adj[c]) if (!visited[v] && !seen[v]) { seen[v] = true; reached++; queue[qt++] = v }
			}
			if (reached != n - count) return -2
			return forced
		}

		var stopped = false
		fun dfs(head: Int, count: Int): Boolean {
			if (count == n) return e < 0 || head == e
			if (--budgetLeft < 0) { stopped = true; return false }
			if ((budgetLeft and 4095) == 0 && cancelled()) { stopped = true; return false }
			val forced = check(head, count)
			if (forced == -2) return false
			val key = State(bits.copyOf(), head)
			if (key in dead) return false
			val options = if (forced >= 0) intArrayOf(forced) else adj[head].filter { !visited[it] }
				.sortedWith(compareBy({ if (it == e) 1 else 0 }, { v -> adj[v].count { !visited[it] } }, { it })).toIntArray()
			for (v in options) {
				if (visited[v]) continue
				if (v == e && count + 1 < n) continue
				mark(v, true); path[count] = v
				if (dfs(v, count + 1)) return true
				mark(v, false)
				if (stopped) return false
			}
			if (dead.size < MEMO_LIMIT) dead += key
			return false
		}
		if (dfs(s, 1)) return Result.Path(path.map { cells[it] })
		return if (stopped) Result.GaveUp else Result.Unsolvable("no path covers every tile" + if (exit != null) " and ends at the exit" else "")
	}

	private class State(val bits: LongArray, val head: Int) {
		private val hash = bits.contentHashCode() * 31 + head
		override fun hashCode() = hash
		override fun equals(other: Any?) = other is State && other.head == head && other.bits.contentEquals(bits)
	}

	fun parityProblem(tiles: Collection<Cell>, start: Cell, exit: Cell?): String? {
		val black = tiles.count { colour(it) == 0 }
		val white = tiles.size - black
		val sc = colour(start)
		if (tiles.size % 2 == 0) {
			if (black != white) return "colour counts differ ($black/$white)"
			if (exit != null && colour(exit) == sc) return "start and exit have the same colour on an even layer"
		} else {
			val major = if (black > white) 0 else 1
			if (Math.abs(black - white) != 1) return "colour counts differ ($black/$white)"
			if (sc != major) return "start is on the minority colour"
			if (exit != null && colour(exit) != major) return "exit is on the minority colour"
		}
		return null
	}

	private fun connectedFrom(s: Int, adj: Array<IntArray>, blocked: BooleanArray, expected: Int): Boolean {
		val seen = BooleanArray(adj.size); seen[s] = true
		val stack = ArrayDeque<Int>().apply { add(s) }
		var reached = 0
		while (stack.isNotEmpty()) {
			val c = stack.removeLast()
			for (v in adj[c]) if (!seen[v] && !blocked[v]) { seen[v] = true; reached++; stack.add(v) }
		}
		return reached == expected
	}

	fun looksLikeRoom(layerHeights: Collection<Int>): Boolean {
		val ys = layerHeights.toSet()
		return ys.any { it + 1 in ys }
	}

	fun isValid(path: List<Cell>, tiles: Set<Cell>, start: Cell? = null, exit: Cell? = null): Boolean {
		if (path.size != tiles.size || path.toSet() != tiles) return false
		if (start != null && path.first() != start) return false
		if (exit != null && path.last() != exit) return false
		return path.zipWithNext().all { (a, b) -> a.isNextTo(b) }
	}

	fun expand(corners: List<Cell>): List<Cell>? {
		if (corners.isEmpty()) return emptyList()
		val out = mutableListOf(corners.first())
		for ((a, b) in corners.zipWithNext()) {
			if (a.x != b.x && a.z != b.z) return null
			val dx = Integer.signum(b.x - a.x); val dz = Integer.signum(b.z - a.z)
			var c = a
			while (c != b) { c = Cell(c.x + dx, c.z + dz); out += c }
		}
		return out
	}

	fun matchBundled(tiles: Set<Cell>, candidates: List<List<Cell>>, start: Cell?, exit: Cell?): List<Cell>? {
		for (c in candidates) for (p in listOf(c, c.asReversed())) {
			if (isValid(p, tiles, start, exit)) return p.toList()
		}
		return null
	}

	data class Ends(val start: Cell, val exit: Cell?)

	enum class Edge { LEVEL, STEP_UP, WALL, NONE }

	fun outsideCells(tiles: Set<Cell>): Set<Cell> {
		if (tiles.isEmpty()) return emptySet()
		val minX = tiles.minOf { it.x } - 1; val maxX = tiles.maxOf { it.x } + 1
		val minZ = tiles.minOf { it.z } - 1; val maxZ = tiles.maxOf { it.z } + 1
		val out = HashSet<Cell>()
		val stack = ArrayDeque<Cell>()
		for (x in minX..maxX) { stack += Cell(x, minZ); stack += Cell(x, maxZ) }
		for (z in minZ..maxZ) { stack += Cell(minX, z); stack += Cell(maxX, z) }
		while (stack.isNotEmpty()) {
			val c = stack.removeLast()
			if (c in tiles || c in out || c.x < minX || c.x > maxX || c.z < minZ || c.z > maxZ) continue
			out += c
			stack += c.neighbours()
		}
		return out
	}

	fun spurs(tiles: Set<Cell>): List<Cell> =
		tiles.filter { t -> t.neighbours().count { it in tiles } == 1 }.sortedWith(compareBy({ it.z }, { it.x }))

	fun detectEnds(
		tiles: Set<Cell>, edge: (from: Cell, outside: Cell) -> Edge, standingOn: Cell? = null,
		upper: Pair<Double, Double>? = null,
	): List<Ends> {
		if (tiles.isEmpty()) return emptyList()
		val outside = outsideCells(tiles)
		val entries = mutableSetOf<Cell>()
		val steps = mutableSetOf<Cell>()
		val rim = mutableSetOf<Cell>()
		for (t in tiles) for (o in t.neighbours()) {
			if (o !in outside) continue
			rim += t
			when (edge(t, o)) {
				Edge.LEVEL -> entries += t
				Edge.STEP_UP -> steps += t
				else -> {}
			}
		}
		val spur = spurs(tiles).filter { it in rim }
		fun dist(a: Cell, b: Cell) = Math.abs(a.x - b.x) + Math.abs(a.z - b.z)
		fun toUpper(c: Cell) = upper?.let { Math.abs(c.x + 0.5 - it.first) + Math.abs(c.z + 0.5 - it.second) } ?: 0.0
		val order = compareBy<Cell>({ it.z }, { it.x })
		val exits = buildList {
			addAll(spur.filter { it in steps }.sortedWith(compareBy<Cell> { toUpper(it) }.then(order)))
			addAll(spur.sortedWith(compareBy<Cell> { toUpper(it) }.then(order)))
			addAll(steps.sortedWith(compareBy<Cell> { toUpper(it) }.then(order)))
		}.distinct().take(MAX_EXITS)
		val starts0 = buildList {
			standingOn?.takeIf { it in tiles }?.let(::add)
			addAll(spur)
			addAll(entries.sortedWith(order))
			addAll(rim.sortedWith(order))
		}.distinct()
		val out = mutableListOf<Ends>()
		for (e in exits) {
			starts0.filter { it != e }
				.sortedWith(compareBy(
					{ if (it == standingOn) 0 else 1 }, { if (it in spur) 0 else 1 }, { if (it in entries) 0 else 1 },
					{ if (it.x == e.x || it.z == e.z) 0 else 1 }, { -dist(it, e) }, { it.z }, { it.x },
				))
				.forEach { out += Ends(it, e) }
		}
		if (out.isEmpty()) starts0.forEach { out += Ends(it, null) }
		return out.distinct()
	}

	private const val MAX_EXITS = 4

	data class Plan(val path: List<Cell>, val ends: Ends, val fromBundle: Boolean, val guessed: Boolean = false)

	fun solveLayer(
		tiles: Set<Cell>, ends: List<Ends>, bundled: List<List<Cell>> = emptyList(), budget: Int = DEFAULT_BUDGET,
		cancelled: () -> Boolean = { false },
	): Plan? {
		if (tiles.isEmpty()) return null
		if (bundled.isNotEmpty()) {
			for (end in ends) matchBundled(tiles, bundled, end.start, end.exit)?.let { return Plan(it, end, true) }
			if (ends.isEmpty()) matchBundled(tiles, bundled, null, null)?.let { return Plan(it, Ends(it.first(), it.last()), true) }
		}
		val tried = HashSet<Ends>()
		for (end in ends) {
			if (!tried.add(end)) continue
			if (end.start !in tiles || (end.exit != null && end.exit !in tiles)) continue
			if (parityProblem(tiles, end.start, end.exit) != null) continue
			if (cancelled()) return null
			val r = solve(tiles, end.start, end.exit, budget, cancelled)
			if (r is Result.Path) return Plan(r.cells, end, false)
		}

		val sp = spurs(tiles)
		val firstEnds = ends.map { it.start }.distinct()
		val startOrder = (firstEnds + sp + tiles.sortedWith(compareBy({ it.z }, { it.x }))).distinct()
		if (sp.size > 2) return null
		for (st in startOrder) {
			if (sp.isNotEmpty() && st !in sp) {

				continue
			}
			if (cancelled()) return null
			val r = solve(tiles, st, null, budget, cancelled)
			if (r is Result.Path) return Plan(r.cells, Ends(r.cells.first(), r.cells.last()), false, guessed = true)
		}
		return null
	}

	fun remaining(plan: List<Cell>, walked: Set<Cell>, at: Cell?): List<Cell>? {
		if (walked.isEmpty() && (at == null || at == plan.first())) return plan
		val i = if (at != null) plan.indexOf(at) else walked.size - 1
		if (i < 0 || i >= plan.size) return null
		val prefix = plan.subList(0, i + 1)

		val walkedOk = walked.all { it in prefix } && plan.subList(0, i).all { it in walked }
		return if (walkedOk) plan.subList(i, plan.size) else null
	}

	fun replan(tiles: Set<Cell>, walked: Set<Cell>, at: Cell, exit: Cell?, budget: Int = DEFAULT_BUDGET, cancelled: () -> Boolean = { false }): Result {
		val left = tiles.filter { it !in walked || it == at }.toSet()
		return solve(left, at, exit, budget, cancelled)
	}

	data class Guide(val route: List<Cell>, val fromPlayer: Boolean, val hint: String?)

	const val HINT_GO_BACK = "no route from this tile - go back to the start"
	const val HINT_RESET = "tiles walked off the route - go back to the start"

	fun guide(plan: Plan, walked: Set<Cell>, at: Cell?, replanned: (() -> List<Cell>?)): Guide {
		val rest = remaining(plan.path, walked, at)
		if (rest != null) return Guide(rest, at != null, null)
		if (at == null) return Guide(plan.path, false, HINT_RESET)
		val again = replanned()
		if (again != null) return Guide(again, true, null)
		return Guide(plan.path, false, HINT_GO_BACK)
	}

	fun describe(tiles: Set<Cell>, walked: Set<Cell> = emptySet(), ends: Ends? = null): String {
		if (tiles.isEmpty()) return "(empty)"
		val minX = tiles.minOf { it.x }; val maxX = tiles.maxOf { it.x }
		val minZ = tiles.minOf { it.z }; val maxZ = tiles.maxOf { it.z }
		return (minZ..maxZ).joinToString("/") { z ->
			(minX..maxX).joinToString("") { x ->
				val c = Cell(x, z)
				when {
					c !in tiles -> "#"
					c == ends?.start -> "S"
					c == ends?.exit -> "E"
					c in walked -> "o"
					else -> "."
				}
			}
		} + " @($minX,$minZ)"
	}
}
