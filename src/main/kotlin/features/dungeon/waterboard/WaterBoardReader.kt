/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Water Board reader. Written from scratch from the room as seen in game; pure (reads blocks through
 * [BlockView], so tests and saved dumps use the same code as the live game).
 *
 * Finding the board (no fixed coordinates, any rotation):
 * 1. The door row: purple/orange/blue/lime/red wool in one straight horizontal line at the foot of the board wall
 *    (wool with glass right above it is the floor pattern, not the row).
 * 2. The board side: the side of that line whose wall layers hold the most channel cells and gate blocks.
 * 3. The channel layer: the wall layer (counted from the row) with the most open cells that are closed in front
 *    (glass) and behind (back wall), plus gate blocks in it.
 * 4. The board: the connected open/gate-block region of that layer around the row. Its highest cell is the inlet.
 * 5. Gate blocks: a gate material in the channel cell = closed; the same material just behind (or in front of) an
 *    open channel cell = open. Flipping a gate lever swaps every block of that material.
 * 6. Levers: a lever on a gate material block is that gate's lever; the other lever (furthest from the wall) is the
 *    water lever. Lever "powered" = water on (cross-checked live against the inlet, see [LeverPolarity]).
 * 7. Doors: the row wool's column. Water in the board's lowest cells of that column (or next to wool inside the
 *    layer) reaches the door. Other wool of the five colours in the room (under the glass floor, the pit) is the
 *    gate state shown in the room; it changing marks a door done.
 */
package dev.ashtonite365.apex.features.dungeon.waterboard

interface BlockView {
	fun id(x: Int, y: Int, z: Int): String
	fun prop(x: Int, y: Int, z: Int, key: String): String?
}

data class P(val x: Int, val y: Int, val z: Int) {
	operator fun plus(o: P) = P(x + o.x, y + o.y, z + o.z)
	fun triple() = Triple(x + 0.5, y + 0.5, z + 0.5)
	override fun toString() = "$x $y $z"
}

data class BoardFrame(val origin: P, val ux: Int, val uz: Int, val nx: Int, val nz: Int) {
	fun world(u: Int, v: Int, depth: Int = 0) = P(origin.x + ux * u + nx * depth, origin.y + v, origin.z + uz * u + nz * depth)

	fun columnOf(p: P): Int = (p.x - origin.x) * ux + (p.z - origin.z) * uz

	fun depthOf(p: P): Int = (p.x - origin.x) * nx + (p.z - origin.z) * nz
}

class BoardLayout(
	val board: Board,
	val frame: BoardFrame,

	val toggleOpenPos: List<P?>,
	val gateLevers: Map<Gate, P>,
	val waterLever: P?,
	val doorWool: Map<Door, List<P>>,
	val gateWool: Map<Door, List<P>>,
	val notes: List<String>,
) {
	fun cellPos(i: Int) = frame.world(board.u(i), board.v(i))
}

object WaterBoardReader {
	val PASSABLE = setOf("air", "cave_air", "void_air", "water", "bubble_column", "light")
	fun passable(id: String) = id in PASSABLE
	fun isGlass(id: String) = id.endsWith("glass") || id.endsWith("glass_pane")

	const val SOURCE_LEVEL = 9
	const val FALLING_LEVEL = 8
	private const val REGION_SIDE = 12
	private const val REGION_UP = 34
	private const val MAX_DEPTH = 12

	data class Box(val min: P, val max: P) {
		inline fun forEach(f: (Int, Int, Int) -> Unit) {
			for (y in min.y..max.y) for (x in min.x..max.x) for (z in min.z..max.z) f(x, y, z)
		}
		fun contains(p: P) = p.x in min.x..max.x && p.y in min.y..max.y && p.z in min.z..max.z
		companion object {
			fun around(c: P, r: Int, down: Int, up: Int) = Box(P(c.x - r, c.y - down, c.z - r), P(c.x + r, c.y + up, c.z + r))
		}
	}

	const val SCAN_RADIUS = 24

	const val SCAN_DOWN = 40
	const val SCAN_UP = 30

	fun scanBoxFor(player: P) = Box.around(player, SCAN_RADIUS, SCAN_DOWN, SCAN_UP)

	fun locate(view: BlockView, box: Box, why: MutableList<String> = mutableListOf()): BoardLayout? {
		val wool = mutableListOf<Pair<P, Door>>()
		val levers = mutableListOf<P>()
		box.forEach { x, y, z ->
			val id = view.id(x, y, z)
			if (id.endsWith("_wool")) Door.ofWool(id)?.let { wool += P(x, y, z) to it }
			else if (id == "lever") levers += P(x, y, z)
		}
		val underGlass = { p: P -> (1..3).any { isGlass(view.id(p.x, p.y + it, p.z)) } }
		val rowCandidates = wool.filter { !underGlass(it.first) }

		data class Line(val alongX: Boolean, val y: Int, val fixed: Int, val items: List<Pair<P, Door>>)
		val lines = rowCandidates.groupBy { Triple(true, it.first.y, it.first.z) }.map { (k, v) -> Line(true, k.second, k.third, v) } +
			rowCandidates.groupBy { Triple(false, it.first.y, it.first.x) }.map { (k, v) -> Line(false, k.second, k.third, v) }
		val row = lines.filter { l ->
			val span = l.items.maxOf { if (l.alongX) it.first.x else it.first.z } - l.items.minOf { if (l.alongX) it.first.x else it.first.z }
			l.items.map { it.second }.toSet().size >= 4 && span <= 28
		}.maxWithOrNull(compareBy<Line> { l -> l.items.map { it.second }.toSet().size }.thenBy { it.items.size })
		if (row == null) { why += "no row of 4+ coloured wool doors found (wool seen: ${wool.size})"; return null }
		val y0 = row.y
		val along = row.items.map { if (row.alongX) it.first.x else it.first.z }
		val aMin = along.min() - REGION_SIDE
		val aMax = along.max() + REGION_SIDE

		data class Cand(val sign: Int, val depth: Int, val score: Int)
		fun at(a: Int, y: Int, sign: Int, d: Int): P = if (row.alongX) P(a, y, row.fixed + sign * d) else P(row.fixed + sign * d, y, a)
		fun solidAt(p: P) = !passable(view.id(p.x, p.y, p.z))
		fun scoreLayer(sign: Int, d: Int, needFront: Boolean): Int {
			var s = 0
			for (a in aMin..aMax) for (y in y0 - 1..y0 + REGION_UP) {
				val p = at(a, y, sign, d)
				val id = view.id(p.x, p.y, p.z)
				if (Gate.ofBlock(id) != null) { s += 2; continue }
				if (!passable(id)) continue
				val back = at(a, y, sign, d + 1)
				val front = at(a, y, sign, d - 1)
				val backId = view.id(back.x, back.y, back.z)
				if (passable(backId) || isGlass(backId)) continue
				if (needFront && !solidAt(front)) continue
				s++
			}
			return s
		}
		var best: Cand? = null
		for (needFront in listOf(true, false)) {
			for (sign in intArrayOf(1, -1)) for (d in 0..MAX_DEPTH) {
				val sc = scoreLayer(sign, d, needFront)
				if (best == null || sc > best.score) best = Cand(sign, d, sc)
			}
			if ((best?.score ?: 0) >= 20) break
			if (needFront) why += "no glass-fronted channel layer, trying without the glass check"
		}
		val layer = best?.takeIf { it.score > 0 } ?: run { why += "no channel layer found"; return null }
		val sign = layer.sign
		val nx = if (row.alongX) 0 else sign
		val nz = if (row.alongX) sign else 0

		val ux = -nz
		val uz = nx
		val layerFixed = row.fixed + sign * layer.depth

		fun worldOf(a: Int, y: Int) = if (row.alongX) P(a, y, layerFixed) else P(layerFixed, y, a)
		fun inRegion(a: Int, y: Int) = a in aMin..aMax && y in y0 - 1..y0 + REGION_UP
		fun member(a: Int, y: Int): Boolean {
			val p = worldOf(a, y)
			val id = view.id(p.x, p.y, p.z)
			return passable(id) || Gate.ofBlock(id) != null || id == "moving_piston"
		}
		val seen = HashSet<Long>()
		fun key(a: Int, y: Int) = (a.toLong() shl 32) or (y.toLong() and 0xffffffffL)
		var bestComp: List<Pair<Int, Int>> = emptyList()
		var bestMat = -1
		for (a in aMin..aMax) for (y in y0 - 1..y0 + REGION_UP) {
			if (key(a, y) in seen || !member(a, y)) continue
			val comp = mutableListOf<Pair<Int, Int>>()
			val stack = ArrayDeque<Pair<Int, Int>>()
			stack += a to y; seen += key(a, y)
			var mats = 0
			while (stack.isNotEmpty()) {
				val (ca, cy) = stack.removeLast()
				comp += ca to cy
				val p = worldOf(ca, cy)
				if (Gate.ofBlock(view.id(p.x, p.y, p.z)) != null) mats++
				else if (layerBehindOrFrontMaterial(view, p, nx, nz) != null) mats++
				for ((da, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
					val na = ca + da; val ny = cy + dy
					if (inRegion(na, ny) && key(na, ny) !in seen && member(na, ny)) { seen += key(na, ny); stack += na to ny }
				}
			}
			if (mats > bestMat || (mats == bestMat && comp.size > bestComp.size)) { bestMat = mats; bestComp = comp }
		}
		if (bestComp.isEmpty() || bestMat <= 0) { why += "channel layer has no gate blocks"; return null }

		val dir = if (row.alongX) ux else uz
		val us = bestComp.map { (a, _) -> a * dir }
		val uMin = us.min(); val uMax = us.max()
		val vMinY = bestComp.minOf { it.second } - 1
		val vMaxY = bestComp.maxOf { it.second }
		val width = uMax - uMin + 1
		val height = vMaxY - vMinY + 1
		val originA = uMin * dir
		val origin = worldOf(originA, vMinY)
		val frame = BoardFrame(origin, ux, uz, nx, nz)
		val compSet = bestComp.map { (a, y) -> (a * dir - uMin) to (y - vMinY) }.toHashSet()
		val solid = BooleanArray(width * height) { true }
		val sink = BooleanArray(width * height)
		val toggles = mutableListOf<Board.Toggle>()
		val openPos = mutableListOf<P?>()
		val notes = mutableListOf<String>()
		var leaks = 0
		for (v in 0 until height) for (u in 0 until width) {
			val i = v * width + u
			val p = frame.world(u, v)
			if ((u to v) !in compSet) {

				if (v == 0 && passable(view.id(p.x, p.y, p.z)) && (u to 1) in compSet) { solid[i] = false; sink[i] = true }
				continue
			}
			solid[i] = false
			val id = view.id(p.x, p.y, p.z)
			val gate = Gate.ofBlock(id)
			if (gate != null) {
				toggles += Board.Toggle(gate, i, closedAtBase = true)
				openPos += layerBehindOrFrontMaterial(view, p, nx, nz)?.takeIf { it.second == gate }?.first
				continue
			}
			layerBehindOrFrontMaterial(view, p, nx, nz)?.let { (pos, g) -> toggles += Board.Toggle(g, i, closedAtBase = false); openPos += pos }
			val front = frame.world(u, v, -1)

			if (passable(view.id(front.x, front.y, front.z)) && v > 2) leaks++
		}
		if (leaks > 0) notes += "$leaks channel cells are open towards the room (glass missing?); treated as closed in front"
		val inlet = (0 until width * height).filter { !solid[it] && !sink[it] && toggles.none { t -> t.index == it } }
			.maxWithOrNull(compareBy<Int> { it / width }.thenBy { -Math.abs(it % width - width / 2) })
			?: run { why += "no inlet"; return null }

		val source = (0 until width * height).filter { !solid[it] }.filter {
			val p = frame.world(it % width, it / width)
			view.id(p.x, p.y, p.z) == "water" && view.prop(p.x, p.y, p.z, "level") == "0"
		}.maxByOrNull { it / width }
		val entry = source ?: inlet

		val doorWool = row.items.groupBy({ it.second }, { it.first })
		val triggers = HashMap<Door, IntArray>()
		for ((door, ps) in doorWool) {
			val cells = LinkedHashSet<Int>()
			for (p in ps) {
				val u = frame.columnOf(p)
				val inLayer = frame.depthOf(p) == 0
				val v = p.y - origin.y
				if (inLayer) {

					for ((du, dv) in listOf(-1 to 0, 1 to 0)) {
						val uu = u + du; val vv = v + dv
						if (uu in 0 until width && vv in 0 until height && !solid[vv * width + uu]) cells += vv * width + uu
					}
				}
				if (inLayer && cells.isNotEmpty()) continue
				for (du in listOf(0, -1, 1)) {
					if (cells.isNotEmpty() && du != 0) break
					val uu = u + du
					if (uu !in 0 until width) continue
					val col = (0 until height).map { it * width + uu }.filter { !solid[it] && toggles.none { t -> t.index == it } }
					val low = col.minByOrNull { it / width } ?: continue
					if (low / width > 3) continue
					cells += col.filter { sink[it] }
					cells += low
					if (low + width < width * height && !solid[low + width]) cells += low + width
				}
			}
			if (cells.isEmpty()) notes += "${door.label} door: no board outlet found above its wool"
			triggers[door] = cells.toIntArray()
		}
		val board = Board(width, height, solid, toggles, sink, entry, triggers)

		val gateLevers = HashMap<Gate, P>()
		val others = mutableListOf<P>()
		for (l in levers) {
			val support = leverSupport(view, l)
			val g = support?.let { Gate.ofBlock(view.id(it.x, it.y, it.z)) }
				?: listOf(P(1, 0, 0), P(-1, 0, 0), P(0, 1, 0), P(0, -1, 0), P(0, 0, 1), P(0, 0, -1)).firstNotNullOfOrNull { d ->
					val q = l + d; Gate.ofBlock(view.id(q.x, q.y, q.z))
				}.takeIf { support == null }
			if (g != null && frame.depthOf(l) < -1) { if (g !in gateLevers) gateLevers[g] = l } else if (frame.depthOf(l) < -1) others += l
		}
		val waterLever = others.minByOrNull { frame.depthOf(it) }
		if (waterLever == null) notes += "water lever not found (water on/off is read from the inlet)"
		val missing = Gate.of(board.usedGates).filter { it !in gateLevers }
		if (missing.isNotEmpty()) notes += "no lever found for " + missing.joinToString { it.label }
		val rowSet = row.items.map { it.first }.toSet()
		val gateWool = wool.filter { it.first !in rowSet }.groupBy({ it.second }, { it.first })
		notes += why
		return BoardLayout(board, frame, openPos, gateLevers, waterLever, doorWool, gateWool, notes)
	}

	private fun layerBehindOrFrontMaterial(view: BlockView, p: P, nx: Int, nz: Int): Pair<P, Gate>? {
		if (!passable(view.id(p.x, p.y, p.z))) return null
		for (d in intArrayOf(1, -1)) {
			val q = P(p.x + nx * d, p.y, p.z + nz * d)
			Gate.ofBlock(view.id(q.x, q.y, q.z))?.let { return q to it }
		}
		return null
	}

	fun leverSupport(view: BlockView, l: P): P? {
		val face = view.prop(l.x, l.y, l.z, "face") ?: return null
		return when (face) {
			"floor" -> P(l.x, l.y - 1, l.z)
			"ceiling" -> P(l.x, l.y + 1, l.z)
			else -> when (view.prop(l.x, l.y, l.z, "facing")) {
				"north" -> P(l.x, l.y, l.z + 1)
				"south" -> P(l.x, l.y, l.z - 1)
				"west" -> P(l.x + 1, l.y, l.z)
				"east" -> P(l.x - 1, l.y, l.z)
				else -> null
			}
		}
	}

	class Seen(

		val config: Int?,
		val leverPowered: Boolean?,
		val inletWet: Boolean,
		val water: IntArray,
		val touching: Int,
		val gateWoolCount: Map<Door, Int>,
		val rowWoolCount: Map<Door, Int>,
	)

	fun observe(view: BlockView, layout: BoardLayout): Seen {
		val b = layout.board
		var config = 0
		var consistent = true
		val perGate = HashMap<Gate, Boolean>()
		for (t in b.toggles) {
			val p = layout.cellPos(t.index)
			val id = view.id(p.x, p.y, p.z)
			val closedNow = when {
				id == t.gate.blockId -> true
				passable(id) -> false
				else -> null
			}
			if (closedNow == null) { consistent = false; continue }
			val flipped = closedNow != t.closedAtBase
			val prev = perGate.put(t.gate, flipped)
			if (prev != null && prev != flipped) consistent = false
		}
		for ((g, f) in perGate) if (f) config = config or g.bit
		val water = IntArray(b.size)
		for (i in 0 until b.size) {
			if (b.isSolidWall(i) && !b.sink[i]) continue
			val p = layout.cellPos(i)
			if (view.id(p.x, p.y, p.z) != "water") continue
			val level = view.prop(p.x, p.y, p.z, "level")?.toIntOrNull() ?: 0
			water[i] = when {
				level == 0 -> SOURCE_LEVEL
				level >= 8 -> FALLING_LEVEL
				else -> 8 - level
			}
		}
		val touching = b.doorTriggers.entries.fold(0) { m, (d, cells) -> if (cells.any { water[it] > 0 }) m or d.bit else m }
		val powered = layout.waterLever?.let { view.prop(it.x, it.y, it.z, "powered")?.let { s -> s == "true" } }
		val inletWet = water[b.entry] > 0 || b.entry + b.width < b.size && water[b.entry + b.width] > 0
		fun count(m: Map<Door, List<P>>) = m.mapValues { (d, ps) -> ps.count { view.id(it.x, it.y, it.z) == d.woolId } }
		return Seen(if (consistent) config else null, powered, inletWet, water, touching, count(layout.gateWool), count(layout.doorWool))
	}

	fun dump(view: BlockView, box: Box, header: List<String>, propsOf: (Int, Int, Int) -> String): String = buildString {
		for (h in header) append("# ").append(h).append('\n')
		append("box ").append(box.min).append(' ').append(box.max).append('\n')
		box.forEach { x, y, z ->
			val id = view.id(x, y, z)
			if (id == "air" || id == "cave_air" || id == "void_air") return@forEach
			append(x).append(' ').append(y).append(' ').append(z).append(' ').append(id)
			val props = propsOf(x, y, z)
			if (props.isNotEmpty()) append('[').append(props).append(']')
			append('\n')
		}
	}

	fun parseDump(text: String): Pair<MapView, Box?> {
		val view = MapView()
		var box: Box? = null
		for (line in text.lineSequence()) {
			val l = line.trim()
			if (l.isEmpty() || l.startsWith("#")) continue
			val parts = l.split(' ')
			if (parts[0] == "box" && parts.size >= 7) {
				val n = parts.drop(1).map { it.toInt() }
				box = Box(P(n[0], n[1], n[2]), P(n[3], n[4], n[5])); continue
			}
			if (parts.size < 4) continue
			val (x, y, z) = parts.take(3).map { it.toInt() }
			val spec = parts.drop(3).joinToString(" ")
			val id = spec.substringBefore('[')
			val props = if ('[' in spec) spec.substringAfter('[').removeSuffix("]").split(',').filter { '=' in it }
				.associate { it.substringBefore('=') to it.substringAfter('=') } else emptyMap()
			view.set(P(x, y, z), id, props)
		}
		return view to box
	}

	class MapView : BlockView {
		private val ids = HashMap<P, String>()
		private val props = HashMap<P, Map<String, String>>()
		fun set(p: P, id: String, prop: Map<String, String> = emptyMap()) {
			if (id == "air") { ids.remove(p); props.remove(p) } else { ids[p] = id; if (prop.isEmpty()) props.remove(p) else props[p] = prop }
		}
		fun props(p: P): Map<String, String> = props[p] ?: emptyMap()
		override fun id(x: Int, y: Int, z: Int) = ids[P(x, y, z)] ?: "air"
		override fun prop(x: Int, y: Int, z: Int, key: String) = props[P(x, y, z)]?.get(key)
	}
}

class LeverPolarity {
	var inverted = false
		private set
	private var disagree = 0
	fun waterOn(powered: Boolean?, inletWet: Boolean): Boolean {
		if (powered == null) return inletWet
		val on = powered != inverted
		if (on != inletWet) { if (++disagree >= SWITCH_AFTER) { inverted = !inverted; disagree = 0 } } else disagree = 0
		return powered != inverted
	}
	companion object { const val SWITCH_AFTER = 60 }
}

class DoorTracker(private val boardDoors: Int, initialGate: Map<Door, Int>, initialRow: Map<Door, Int>) {
	private val gate0 = initialGate.toMap()
	private val row0 = initialRow.toMap()
	val gateInfo = gate0.values.any { it > 0 }

	val wanted: Int = if (gateInfo) {
		val top = Door.entries.maxOf { gate0[it] ?: 0 }
		Door.entries.filter { (gate0[it] ?: 0) == top }.fold(0) { m, d -> m or d.bit } and boardDoors
	} else boardDoors
	var touched = 0
		private set

	fun noteTouching(doors: Int, waterOn: Boolean) { if (waterOn) touched = touched or doors }

	fun done(gateNow: Map<Door, Int>, rowNow: Map<Door, Int>): Int {
		var m = touched
		for (d in Door.entries) {
			if ((gate0[d] ?: 0) > 0 && (gateNow[d] ?: 0) < (gate0[d] ?: 0)) m = m or d.bit
			if ((row0[d] ?: 0) > 0 && (rowNow[d] ?: 0) < (row0[d] ?: 0)) m = m or d.bit
		}
		return m
	}

	fun remaining(gateNow: Map<Door, Int>, rowNow: Map<Door, Int>) = wanted and done(gateNow, rowNow).inv()
}
