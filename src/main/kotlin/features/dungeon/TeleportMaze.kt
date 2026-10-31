/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Teleport Maze puzzle, pure logic (no Minecraft types, unit-tested). Rendering lives in
 * TeleportMazeSolver.kt. Visual only.
 *
 * Pads are end portal frames, given as packed (x, y, z) Ints in [Pad]. The caller scans the WHOLE dungeon room
 * (32-block grid cell, full height) for frames when the first pad is stood on, and rescans after each teleport.
 *
 * Teleports: Hypixel drops you BESIDE the destination pad (x.5, 69.5, z.5, within ~1 block of the frame), not on it,
 * so the arrival pad is the nearest pad within [ARRIVAL_RADIUS]. Links are two-way: stepping on the arrival pad takes
 * you straight back, so one teleport A -> B records A -> B and B -> A. An arrival with no pad nearby, or outside the
 * pads' bounding box plus [EXIT_MARGIN], is an EXIT (back to the entrance / out of the maze): its source pad is
 * recorded in [Tracker.exits] and never recommended.
 *
 * Layout (third-party room-relative pad table, BSD-3-Clause; CREDITS.md, NOTICE): 30 pads in a 22x22 block
 * footprint. Seven outer cells of four pads (corners of a 6x6 square, cells on an 8-block grid) form a U, and the
 * two remaining pads sit in the middle, at the end of the entrance corridor: the START hub (where you walk in; the
 * wrong-way "exit" landings put you back in that corridor). They were mislabelled "Final room" before. The grid is
 * taken from the pads' own bounding box, so it does not depend on room rotation. The finish is not part of the pad
 * layout, so nothing is ever labelled "final room" from the layout alone.
 *
 * "Likely" pad: the usual heuristic: after every teleport
 * Hypixel turns you to face the pad that leads on; every unvisited pad whose box (inflated 0.75) is hit by a 32-block
 * ray along the arrival yaw/pitch stays a candidate, and candidates are intersected across teleports (reset when
 * empty). Other mods do the same intersection with sampled ray points; the idea only is used here, no code
 * (see CREDITS.md). Fallback inside a cell: the unvisited pad whose direction is closest to the
 * arrival yaw.
 */
package dev.ashtonite365.apex.features.dungeon

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TeleportMaze {
	data class Pad(val x: Int, val y: Int, val z: Int) {
		fun distTo(o: Pad): Double { val dx = (x - o.x).toDouble(); val dz = (z - o.z).toDouble(); return sqrt(dx * dx + dz * dz) }

		fun distTo(px: Double, pz: Double): Double { val dx = x + 0.5 - px; val dz = z + 0.5 - pz; return sqrt(dx * dx + dz * dz) }
	}

	data class Bounds(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
		fun containsWithMargin(x: Double, y: Double, z: Double, margin: Double = ROOM_MARGIN, dy: Double = ROOM_DY) =
			x >= minX - margin && x <= maxX + 1 + margin && z >= minZ - margin && z <= maxZ + 1 + margin &&
				y >= minY - dy && y <= maxY + 1 + dy

		companion object {
			fun of(pads: Collection<Pad>): Bounds? = if (pads.isEmpty()) null else
				Bounds(pads.minOf { it.x }, pads.minOf { it.y }, pads.minOf { it.z }, pads.maxOf { it.x }, pads.maxOf { it.y }, pads.maxOf { it.z })
		}
	}

	const val ROOM_MARGIN = 4.0
	const val ROOM_DY = 6.0

	const val ARRIVAL_RADIUS = 2.0

	const val EXIT_MARGIN = 1.5

	fun dungeonRoom(x: Int, z: Int): Bounds {
		fun cell(v: Int) = -200 + Math.floorDiv(v + 200, 32) * 32
		val cx = cell(x); val cz = cell(z)
		return Bounds(cx, ROOM_MIN_Y, cz, cx + 30, ROOM_MAX_Y, cz + 30)
	}
	const val ROOM_MIN_Y = 0
	const val ROOM_MAX_Y = 255

	fun nearestPad(pads: Collection<Pad>, x: Double, y: Double, z: Double, radius: Double): Pad? =
		pads.filter { abs(it.y + 1.0 - y) <= 2.5 && it.distTo(x, z) <= radius }
			.minWithOrNull(compareBy({ it.distTo(x, z) }, { it.x }, { it.z }))

	data class Teleport(val from: Pad?, val to: Pad?, val exit: Boolean)

	class Tracker {

		val visited = linkedSetOf<Pad>()

		val departed = linkedSetOf<Pad>()

		val landed = linkedSetOf<Pad>()

		val links = linkedMapOf<Pad, Pad>()

		val observed = linkedSetOf<Pair<Pad, Pad>>()

		val exits = linkedSetOf<Pad>()

		var likely: Set<Pad>? = null; private set
		var lastYaw: Float? = null; private set
		var firstPad: Pad? = null; private set
		var lastTeleport: Teleport? = null; private set

		var previous: Pad? = null; private set
		var allPads: Set<Pad> = emptySet(); private set
		var room: Bounds? = null; private set
		private var roomIsDungeonCell = false
		private var lastPad: Pad? = null
		private var lastX = Double.NaN; private var lastY = Double.NaN; private var lastZ = Double.NaN

		fun reset() {
			visited.clear(); departed.clear(); landed.clear(); links.clear(); observed.clear(); exits.clear()
			likely = null; lastYaw = null; firstPad = null; lastTeleport = null; previous = null
			allPads = emptySet(); room = null; roomIsDungeonCell = false; lastPad = null; lastX = Double.NaN
		}

		fun setRoomPads(pads: Set<Pad>, dungeonRoom: Bounds? = null) {
			allPads = allPads + pads
			if (dungeonRoom != null) { room = dungeonRoom; roomIsDungeonCell = true }
			else if (!roomIsDungeonCell) room = Bounds.of(allPads)
		}

		fun isExitLanding(x: Double, y: Double, z: Double): Boolean {
			val box = Bounds.of(allPads) ?: return false
			return !box.containsWithMargin(x, y, z, EXIT_MARGIN, ROOM_DY) || nearestPad(allPads, x, y, z, ARRIVAL_RADIUS) == null
		}

		fun tick(x: Double, y: Double, z: Double, padUnder: Pad?, teleported: Boolean, yaw: Float? = null, pitch: Float = 0f): Boolean {
			room?.let {
				val inside = if (roomIsDungeonCell) it.containsWithMargin(x, y, z, 0.0, 0.0) else it.containsWithMargin(x, y, z)
				if (!inside) { reset(); return false }
			}
			lastTeleport = null
			if (teleported) {
				val from = lastPad ?: if (lastX.isNaN()) null else nearestPad(allPads, lastX, lastY, lastZ, 1.5)
				val to = padUnder ?: nearestPad(allPads, x, y, z, ARRIVAL_RADIUS)
				val exit = allPads.isNotEmpty() && (to == null || isExitLanding(x, y, z))
				if (from != null) {
					if (exit) { exits += from; departed += from; visited += from }
					else if (to != null && to != from) {
						links[from] = to; links[to] = from; observed += from to to
						departed += from; landed += to; visited += from; visited += to
					}
				}
				previous = if (!exit && from != null && to != null && to != from) from else if (exit) null else previous
				if (!exit && to != null) { visited += to; landed += to }
				lastTeleport = Teleport(from, if (exit) null else to, exit)
				if (!exit && yaw != null) {
					lastYaw = yaw
					val base = likely?.takeIf { it.isNotEmpty() } ?: allPads
					likely = base.filter { it !in visited && rayHits(it, x, z, yaw, pitch) && it.distTo(x, z) > 1.3 }.toSet()
				}
			}
			if (padUnder != null) { visited += padUnder; if (firstPad == null) firstPad = padUnder }
			lastPad = padUnder
			lastX = x; lastY = y; lastZ = z
			return true
		}
	}

	fun rayHits(p: Pad, x: Double, z: Double, yaw: Float, pitch: Float, range: Double = 32.0): Boolean {
		val r = Math.toRadians(yaw.toDouble()); val c = cos(Math.toRadians(pitch.toDouble()))
		val dx = -sin(r) * c * range; val dz = cos(r) * c * range
		val minX = p.x - 0.75; val maxX = p.x + 1.75; val minZ = p.z - 0.75; val maxZ = p.z + 1.75
		var t0 = 0.0; var t1 = 1.0
		for ((o, d, lo, hi) in listOf(doubleArrayOf(x, dx, minX, maxX), doubleArrayOf(z, dz, minZ, maxZ)).map { it.toList() }) {
			if (abs(d) < 1e-9) { if (o < lo || o > hi) return false; continue }
			var a = (lo - o) / d; var b = (hi - o) / d
			if (a > b) { val t = a; a = b; b = t }
			t0 = maxOf(t0, a); t1 = minOf(t1, b)
			if (t0 > t1) return false
		}
		return true
	}

	fun yawDiff(p: Pad, x: Double, z: Double, yaw: Float): Double {
		return abs(wrap(yawTo(p.x + 0.5 - x, p.z + 0.5 - z) - yaw.toDouble()))
	}

	fun yawTo(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, dz))

	fun originPad(x: Double, y: Double, z: Double) = Pad(Math.floor(x).toInt(), Math.floor(y).toInt(), Math.floor(z).toInt())

	fun arrowHead(fromX: Double, fromZ: Double, toX: Double, toZ: Double): DoubleArray? {
		val dx = toX - fromX; val dz = toZ - fromZ
		val len = sqrt(dx * dx + dz * dz).takeIf { it > 1e-6 } ?: return null
		val ux = dx / len; val uz = dz / len
		val tipX = toX - ux * 0.6; val tipZ = toZ - uz * 0.6
		return doubleArrayOf(tipX, tipZ, tipX - ux * 0.7, tipZ - uz * 0.7)
	}
	private fun wrap(d: Double): Double { var v = d % 360.0; if (v >= 180.0) v -= 360.0; if (v < -180.0) v += 360.0; return v }

	const val HUB_CELL = -1

	const val CELL_LINK = 5.0

	fun gridCells(pads: Collection<Pad>): Map<Pad, Int>? {
		val b = Bounds.of(pads) ?: return null
		val sx = b.maxX - b.minX; val sz = b.maxZ - b.minZ
		if (pads.size < 20 || sx !in 20..24 || sz !in 20..24) return null
		val raw = pads.associateWith { p ->
			val col = ((p.x - b.minX) * 3 / (sx + 2)).coerceIn(0, 2); val row = ((p.z - b.minZ) * 3 / (sz + 2)).coerceIn(0, 2)
			col * 3 + row
		}
		val counts = raw.values.groupingBy { it }.eachCount()
		return raw.mapValues { (_, c) -> if (counts.getValue(c) < 4) HUB_CELL else c }
	}

	fun cells(pads: Collection<Pad>): Map<Pad, Int> = gridCells(pads) ?: unionCells(pads)

	private fun unionCells(pads: Collection<Pad>): Map<Pad, Int> {
		val list = pads.sortedWith(compareBy({ it.x }, { it.y }, { it.z }))
		val parent = IntArray(list.size) { it }
		fun find(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; return r }
		for (i in list.indices) for (j in i + 1 until list.size)
			if (list[i].distTo(list[j]) <= CELL_LINK && abs(list[i].y - list[j].y) <= 1) {
				val a = find(i); val b = find(j); if (a != b) parent[maxOf(a, b)] = minOf(a, b)
			}
		return list.indices.associate { list[it] to find(it) }
	}

	fun startPads(pads: Collection<Pad>): Set<Pad> = gridCells(pads)?.filterValues { it == HUB_CELL }?.keys ?: emptySet()

	enum class State { START, LEAVE_EXIT, BACK_START, BACK_PREVIOUS, LEAVE_DEAD_END, KNOWN, LIKELY, UNKNOWN }

	val State.isLeave get() = this == State.LEAVE_EXIT || this == State.LEAVE_DEAD_END
	val State.isBack get() = this == State.BACK_START || this == State.BACK_PREVIOUS

	fun states(
		allPads: Set<Pad>, links: Map<Pad, Pad>, exits: Set<Pad>, firstPad: Pad?, likely: Set<Pad>?, previous: Pad? = null,
	): Map<Pad, State> {
		val cell = cells(allPads)
		val hub = startPads(allPads)
		val startCells = buildSet { if (hub.isNotEmpty()) add(HUB_CELL); firstPad?.let { cell[it] }?.let(::add) }
		val prevCell = previous?.let { cell[it] }
		val curCell = previous?.let { links[it] }?.let { cell[it] }
		val dead = mutableMapOf<Pair<Int?, Int?>, Boolean>()
		return allPads.associateWith { p ->
			val to = links[p]
			when {
				p in hub -> State.START
				p in exits -> State.LEAVE_EXIT
				to != null && cell[to] in startCells && cell[p] !in startCells -> State.BACK_START
				to != null && prevCell != null && curCell != prevCell && cell[p] == curCell && cell[to] == prevCell -> State.BACK_PREVIOUS
				to != null && dead.getOrPut(cell[p] to cell[to]) { isDeadEnd(allPads, links, exits, cell, cell[p], cell[to]) } -> State.LEAVE_DEAD_END
				to != null -> State.KNOWN
				likely?.contains(p) == true -> State.LIKELY
				else -> State.UNKNOWN
			}
		}
	}

	fun isDeadEnd(allPads: Set<Pad>, links: Map<Pad, Pad>, exits: Set<Pad>, cell: Map<Pad, Int>, fromCell: Int?, intoCell: Int?): Boolean {
		if (intoCell == null || intoCell == fromCell) return false
		val byCell = allPads.groupBy { cell[it] }
		val seen = mutableSetOf(intoCell); val queue = ArrayDeque(listOf(intoCell))
		while (queue.isNotEmpty()) {
			val c = queue.removeFirst()
			for (q in byCell[c].orEmpty()) {
				if (q in exits) continue
				val dest = links[q] ?: return false
				val dc = cell[dest] ?: continue
				if (dc != fromCell && seen.add(dc)) queue.addLast(dc)
			}
		}
		return true
	}

	data class PadPair(val a: Pad, val b: Pad, val number: Int, val colour: Int, val aToB: Boolean, val bToA: Boolean) {

		val bothTravelled get() = aToB && bToA
	}

	val PALETTE = intArrayOf(
		0xFF00E5FF.toInt(), 0xFFFF40FF.toInt(), 0xFFFFD000.toInt(), 0xFF40FF40.toInt(), 0xFFFF8000.toInt(), 0xFF4080FF.toInt(),
		0xFFFF4060.toInt(), 0xFFA0FF00.toInt(), 0xFFB060FF.toInt(), 0xFF00FFA0.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF9EC0.toInt(),
	)
	fun pairColour(number: Int) = PALETTE[(number - 1).mod(PALETTE.size)]

	fun pairs(links: Map<Pad, Pad>, observed: Set<Pair<Pad, Pad>>): List<PadPair> {
		val seen = mutableSetOf<Set<Pad>>()
		val out = mutableListOf<PadPair>()
		for ((a, b) in links) {
			if (a == b || !seen.add(setOf(a, b))) continue

			val (x, y) = if ((b to a) in observed && (a to b) !in observed) b to a else a to b
			val n = out.size + 1
			out += PadPair(x, y, n, pairColour(n), (x to y) in observed, (y to x) in observed)
		}
		return out
	}

	sealed interface Guide {
		enum class Why { LIKELY, UNKNOWN, UNKNOWN_ELSEWHERE, FALLBACK }

		data class Step(val next: Pad, val target: Pad, val teleports: Int, val walk: Double, val why: Why = Why.UNKNOWN) : Guide

		data object Nothing : Guide
	}

	fun guide(
		allPads: Set<Pad>, visited: Set<Pad>, links: Map<Pad, Pad>, from: Pad,
		exits: Set<Pad> = emptySet(), likely: Set<Pad>? = null, firstPad: Pad? = null, yaw: Float? = null,
		previous: Pad? = null,
	): Guide {
		if (allPads.isEmpty()) return Guide.Nothing
		val cell = cells(allPads)
		val fx = from.x + 0.5; val fz = from.z + 0.5
		val start = allPads.minWithOrNull(compareBy<Pad>({ it.distTo(from) }, { it.x }, { it.z })) ?: return Guide.Nothing
		val startCell = cell.getValue(start)
		val st8 = states(allPads, links, exits, firstPad, likely, previous)
		data class St(val tps: Int, val entry: Pad, val first: Pad?, val entryX: Double, val entryZ: Double)
		val best = mutableMapOf(startCell to St(0, from, null, fx, fz))
		var frontier = listOf(startCell)
		val linksByCell = links.entries.filter { it.key !in exits && st8[it.key]?.isLeave != true }.groupBy { cell[it.key] }
		while (frontier.isNotEmpty()) {
			val next = mutableListOf<Int>()
			for (c in frontier) {
				val st = best.getValue(c)
				for ((pad, arrival) in (linksByCell[c] ?: emptyList()).sortedWith(compareBy({ it.key.distTo(st.entryX, st.entryZ) }, { it.key.x }, { it.key.z }))) {
					val ac = cell[arrival] ?: continue
					if (ac in best) continue
					best[ac] = St(st.tps + 1, arrival, st.first ?: pad, arrival.x + 0.5, arrival.z + 0.5)
					next += ac
				}
			}
			frontier = next
		}

		val unknown = allPads.filter { it !in visited && it !in links && it !in exits }
		val reach = unknown.mapNotNull { t -> best[cell[t]]?.let { t to it } }
		val pick = reach.minWithOrNull(compareBy<Pair<Pad, St>>(
			{ if (likely?.contains(it.first) == true) 0 else 1 },
			{ it.second.tps },
			{ it.first.distTo(it.second.entryX, it.second.entryZ) },
			{ if (it.second.tps == 0 && yaw != null) yawDiff(it.first, fx, fz, yaw) else 0.0 },
			{ it.first.x }, { it.first.y }, { it.first.z },
		))
		if (pick != null) {
			val (t, st) = pick
			val why = if (likely?.contains(t) == true) Guide.Why.LIKELY else Guide.Why.UNKNOWN
			return Guide.Step(st.first ?: t, t, st.tps, t.distTo(st.entryX, st.entryZ), why)
		}

		unknown.minWithOrNull(compareBy({ if (likely?.contains(it) == true) 0 else 1 }, { it.distTo(fx, fz) }, { it.x }, { it.z }))
			?.let { return Guide.Step(it, it, -1, it.distTo(fx, fz), if (likely?.contains(it) == true) Guide.Why.LIKELY else Guide.Why.UNKNOWN_ELSEWHERE) }

		val ok = allPads.filter { st8[it]?.isLeave != true && it !in exits }.ifEmpty { allPads.toList() }
		val f = ok.minWith(compareBy({ it.distTo(fx, fz) }, { it.x }, { it.z }))
		return Guide.Step(f, f, -1, f.distTo(fx, fz), Guide.Why.FALLBACK)
	}

	const val ROOM_NAME = "Teleport Maze"

	fun cellBounds(ix: Int, iz: Int): Bounds = dungeonRoom(-200 + ix * 32, -200 + iz * 32)

	fun roomBounds(cells: Collection<Pair<Int, Int>>): Bounds? {
		if (cells.isEmpty()) return null
		val b = cells.map { (ix, iz) -> cellBounds(ix, iz) }
		return Bounds(b.minOf { it.minX }, ROOM_MIN_Y, b.minOf { it.minZ }, b.maxOf { it.maxX }, ROOM_MAX_Y, b.maxOf { it.maxZ })
	}

	fun Bounds.contains(p: Pad) = p.x in minX..maxX && p.y in minY..maxY && p.z in minZ..maxZ

	fun padsInside(pads: Collection<Pad>, room: Bounds?): Set<Pad> =
		if (room == null) emptySet() else pads.filterTo(linkedSetOf()) { room.contains(it) }

	class Gate {
		enum class Why { OFF, NOT_IN_DUNGEON, NO_ROOM, OTHER_ROOM, OUTSIDE_ROOM, MAZE }

		data class Change(val open: Boolean, val why: Why, val roomName: String?, val room: Bounds?, val discard: Boolean, val resumed: Boolean)

		var open = false; private set
		var why = Why.NO_ROOM; private set

		var room: Bounds? = null; private set

		var parked: Bounds? = null; private set

		fun reset() { open = false; why = Why.NO_ROOM; room = null; parked = null }

		fun update(enabled: Boolean, inDungeon: Boolean, roomName: String?, cells: Collection<Pair<Int, Int>>?, x: Double, z: Double): Change? {
			val bounds = if (roomName == ROOM_NAME) cells?.let(::roomBounds) else null
			val now = when {
				!enabled -> Why.OFF
				!inDungeon -> Why.NOT_IN_DUNGEON
				roomName == null || cells.isNullOrEmpty() -> Why.NO_ROOM
				roomName != ROOM_NAME || bounds == null -> Why.OTHER_ROOM
				!bounds.containsWithMargin(x, bounds.minY.toDouble(), z, 0.0, 0.0) -> Why.OUTSIDE_ROOM
				else -> Why.MAZE
			}
			why = now
			val discardAll = now == Why.OFF || now == Why.NOT_IN_DUNGEON
			if (now != Why.MAZE) {
				if (!open) {
					if (!discardAll || parked == null) return null
					val was = parked; parked = null
					return Change(false, now, roomName, was, discard = true, resumed = false)
				}
				open = false
				val was = room
				room = null
				if (!discardAll) parked = was
				return Change(false, now, roomName, was, discardAll, false)
			}
			if (open && room == bounds) return null
			val resumed = parked == bounds && !(open && room != bounds)
			open = true; room = bounds; parked = null
			return Change(true, now, roomName, bounds, !resumed, resumed)
		}

		fun visible(pads: Collection<Pad>): Set<Pad> = if (!open) emptySet() else padsInside(pads, room)
	}
}
