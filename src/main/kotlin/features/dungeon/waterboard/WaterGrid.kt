/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Water Board grid and steady-state water flow. Written from scratch for SkyBlock Apex from the
 * vanilla water rules; no code or data from any other mod. Pure (no Minecraft classes).
 *
 * The whole board is a 2D array (u = column from the left as seen from the room, v = row from the bottom):
 * - value layer: 0 = water can't pass, 1 = water can pass (empty), 2 = water is there now;
 * - letter layer: the switchable (gate) blocks, one letter per block type. Lowercase = open (water passes, value 1
 *   or 2), uppercase = closed (value 0). Flipping a gate's lever swaps the case of every cell with its letter.
 *
 * Water rules (vanilla Java/Bedrock water, checked against the 30 Oct dump where flowing water shows levels 1..6 one
 * per block away from a falling column):
 * - Water that has an open cell below it falls into it; falling water lands with full strength again.
 * - Only water that cannot fall spreads sideways (a source on the inlet also spreads while above a drop, as vanilla).
 * - Sideways it loses one level per block: [FLOW_LIMIT] = 7 blocks out from the landing cell (8 wide with it).
 * - It only spreads towards the nearest drop: each side is searched up to [SLOPE_DISTANCE] = 4 blocks for an open
 *   cell with an open cell below; only the side(s) with the shortest distance get water; no drop on either side = both.
 * Flow starts from every value-2 cell and marks each cell it reaches 2. Below the bottom row water falls off the board.
 */
package dev.ashtonite365.apex.features.dungeon.waterboard

class WaterGrid(val width: Int, val height: Int, private val value: IntArray, private val letter: CharArray) {
	init { require(value.size == width * height && letter.size == width * height) { "grid size" } }

	val size get() = width * height
	fun index(u: Int, v: Int) = v * width + u
	fun value(u: Int, v: Int) = value[index(u, v)]
	fun value(i: Int) = value[i]

	fun letter(i: Int): Char? = letter[i].takeIf { it != NONE }
	fun passable(i: Int) = value[i] != 0
	fun wet(i: Int) = value[i] == 2
	fun copy() = WaterGrid(width, height, value.copyOf(), letter.copyOf())

	fun toggled(gate: Gate): WaterGrid {
		val v = value.copyOf(); val l = letter.copyOf()
		for (i in 0 until size) if (l[i].uppercaseChar() == gate.letter) {
			val open = l[i].isUpperCase()
			l[i] = if (open) gate.letter.lowercaseChar() else gate.letter
			v[i] = if (open) 1 else 0
		}
		return WaterGrid(width, height, v, l)
	}

	fun toggled(mask: Int): WaterGrid = Gate.of(mask).fold(this) { g, gate -> g.toggled(gate) }

	fun withWater(vararg cells: Int): WaterGrid {
		val v = value.copyOf()
		for (c in cells) if (v[c] != 0) v[c] = 2
		return WaterGrid(width, height, v, letter.copyOf())
	}

	fun gates(): Int = (0 until size).mapNotNull { letter(it) }.mapNotNull { Gate.ofLetter(it) }.fold(0) { m, g -> m or g.bit }

	fun render(): String = buildString {
		for (v in height - 1 downTo 0) {
			for (u in 0 until width) { val i = index(u, v); append(letter(i) ?: ('0' + value[i])) }
			if (v > 0) append('\n')
		}
	}

	fun renderWater(): String = buildString {
		for (v in height - 1 downTo 0) {
			for (u in 0 until width) { val i = index(u, v); append(if (value[i] == 2) '2' else letter(i) ?: ('0' + value[i])) }
			if (v > 0) append('\n')
		}
	}

	fun flow(limit: Int = FLOW_LIMIT): WaterGrid {
		val full = limit + 1
		val strength = IntArray(size)
		val queue = ArrayDeque<Int>()
		for (i in 0 until size) if (value[i] == 2) { strength[i] = full; queue += i }
		val sources = BooleanArray(size) { value[it] == 2 }
		fun open(u: Int, v: Int) = u in 0 until width && v in 0 until height && value[index(u, v)] != 0
		fun drop(u: Int, v: Int) = v == 0 || open(u, v - 1)
		fun reach(i: Int, s: Int) { if (s > strength[i]) { strength[i] = s; queue += i } }
		while (queue.isNotEmpty()) {
			val i = queue.removeFirst()
			val u = i % width; val v = i / width; val s = strength[i]
			val falls = drop(u, v)
			if (falls && v > 0) reach(i - width, full)
			if (falls && !sources[i]) continue
			if (s <= 1) continue

			val dist = IntArray(2) { NO_DROP }
			for ((k, du) in intArrayOf(-1, 1).withIndex()) {
				var d = 1
				while (d <= SLOPE_DISTANCE && open(u + du * d, v)) {
					if (drop(u + du * d, v)) { dist[k] = d; break }
					d++
				}
			}
			val best = minOf(dist[0], dist[1])
			for ((k, du) in intArrayOf(-1, 1).withIndex()) {
				if (!open(u + du, v)) continue
				if (best != NO_DROP && dist[k] != best) continue
				reach(index(u + du, v), s - 1)
			}
		}
		val out = value.copyOf()
		for (i in 0 until size) if (strength[i] > 0) out[i] = 2
		return WaterGrid(width, height, out, letter.copyOf())
	}

	override fun toString() = render()

	companion object {

		const val FLOW_LIMIT = 7
		const val SLOPE_DISTANCE = 4
		private const val NO_DROP = Int.MAX_VALUE
		private const val NONE = '\u0000'

		fun parse(rows: List<String>): WaterGrid {
			val h = rows.size
			val w = rows.maxOf { it.length }
			val value = IntArray(w * h)
			val letter = CharArray(w * h)
			for ((r, row) in rows.withIndex()) for (u in 0 until w) {
				val i = (h - 1 - r) * w + u
				val c = row.getOrElse(u) { '0' }
				when {
					c in '0'..'2' -> value[i] = c - '0'
					Gate.ofLetter(c) != null -> { letter[i] = c; value[i] = if (c.isLowerCase()) 1 else 0 }
					else -> error("unknown grid char '$c'")
				}
			}
			return WaterGrid(w, h, value, letter)
		}

		fun parse(text: String) = parse(text.trimEnd('\n').lines())
	}
}

object WaterPreview {
	const val MAX_DISTANCE = 40.0

	fun targetCell(frame: BoardFrame, width: Int, height: Int, eye: Triple<Double, Double, Double>, dir: Triple<Double, Double, Double>): Int? {
		val nx = frame.nx.toDouble(); val nz = frame.nz.toDouble()
		val denom = dir.first * nx + dir.third * nz
		if (Math.abs(denom) < 1e-6) return null

		val cx = frame.origin.x + 0.5; val cz = frame.origin.z + 0.5
		val t = ((cx - eye.first) * nx + (cz - eye.third) * nz) / denom
		if (t <= 0 || t > MAX_DISTANCE) return null
		val hx = eye.first + dir.first * t; val hy = eye.second + dir.second * t; val hz = eye.third + dir.third * t
		val p = P(Math.floor(hx).toInt(), Math.floor(hy).toInt(), Math.floor(hz).toInt())
		val u = frame.columnOf(p); val v = p.y - frame.origin.y
		if (u !in 0 until width || v !in 0 until height) return null
		return v * width + u
	}

	fun gateFor(board: Board, cell: Int?): Gate? = cell?.let { c -> board.toggles.firstOrNull { it.index == c }?.gate }

	fun previewConfig(current: Int, gate: Gate?, planned: Int?): Int? = if (gate != null) current xor gate.bit else planned
}
