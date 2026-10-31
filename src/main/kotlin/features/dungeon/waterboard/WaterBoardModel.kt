/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Water Board puzzle, board model. Written from scratch for SkyBlock Apex from the vanilla
 * water rules and the room as seen in game; no code or data from any other mod. Pure (no Minecraft classes).
 *
 * The board is the wall behind the room's stained-glass floor: one block deep channels (the "channel layer") with
 * stone walls, coloured toggle blocks and a water inlet at the top. Water inside a one block deep channel can only move
 * down and sideways in the plane of the wall, so the board is modelled as a 2D grid: u = column (0 = leftmost as seen
 * from the room), v = row (0 = bottom).
 */
package dev.ashtonite365.apex.features.dungeon.waterboard

enum class Gate(val blockId: String, val label: String, val code: String, val letter: Char) {
	QUARTZ("quartz_block", "Quartz", "§f", 'Q'),
	GOLD("gold_block", "Gold", "§6", 'G'),
	COAL("coal_block", "Coal", "§8", 'C'),
	DIAMOND("diamond_block", "Diamond", "§b", 'D'),
	EMERALD("emerald_block", "Emerald", "§a", 'E'),
	TERRACOTTA("terracotta", "Terracotta", "§c", 'T');

	val bit: Int get() = 1 shl ordinal

	companion object {
		const val ALL = 0b111111
		fun ofBlock(id: String): Gate? = entries.firstOrNull { it.blockId == id }
		fun ofLetter(c: Char): Gate? = entries.firstOrNull { it.letter == c.uppercaseChar() }
		fun of(mask: Int): List<Gate> = entries.filter { mask and it.bit != 0 }
	}
}

enum class Door(val woolId: String, val label: String, val code: String, val letter: Char) {
	PURPLE("purple_wool", "Purple", "§5", 'P'),
	ORANGE("orange_wool", "Orange", "§6", 'O'),
	BLUE("blue_wool", "Blue", "§9", 'B'),
	LIME("lime_wool", "Lime", "§a", 'L'),
	RED("red_wool", "Red", "§c", 'R');

	val bit: Int get() = 1 shl ordinal

	companion object {
		const val ALL = 0b11111
		fun ofWool(id: String): Door? = entries.firstOrNull { it.woolId == id }
		fun ofLetter(c: Char): Door? = entries.firstOrNull { it.letter == c }
		fun of(mask: Int): List<Door> = entries.filter { mask and it.bit != 0 }
		fun names(mask: Int): String = of(mask).joinToString(", ") { it.code + it.label + "§r" }
	}
}

class Board(
	val width: Int,
	val height: Int,
	private val solid: BooleanArray,
	val toggles: List<Toggle>,
	val sink: BooleanArray,
	val entry: Int,
	val doorTriggers: Map<Door, IntArray>,
) {
	data class Toggle(val gate: Gate, val index: Int, val closedAtBase: Boolean)

	init {
		require(solid.size == width * height && sink.size == width * height) { "grid size" }
		require(entry in 0 until width * height) { "entry outside the grid" }
	}

	val size: Int get() = width * height
	val doors: Int get() = doorTriggers.filterValues { it.isNotEmpty() }.keys.fold(0) { m, d -> m or d.bit }

	val usedGates: Int get() = toggles.fold(0) { m, t -> m or t.gate.bit }

	fun index(u: Int, v: Int) = v * width + u
	fun u(i: Int) = i % width
	fun v(i: Int) = i / width
	fun isSolidWall(i: Int) = solid[i]

	fun passable(config: Int): BooleanArray {
		val p = BooleanArray(size) { !solid[it] }
		for (t in toggles) p[t.index] = !(t.closedAtBase xor (config and t.gate.bit != 0))
		return p
	}

	fun grid(config: Int = 0, waterOn: Boolean = false, water: ((Int) -> Boolean)? = null): WaterGrid {
		val pass = passable(config)
		val rows = (height - 1 downTo 0).map { v ->
			String(CharArray(width) { u ->
				val i = index(u, v)
				val t = toggleAt[i]
				when {
					t != null -> if (pass[i]) t.gate.letter.lowercaseChar() else t.gate.letter
					!pass[i] -> '0'
					else -> '1'
				}
			})
		}
		var g = WaterGrid.parse(rows)
		val wet = (0 until size).filter { pass[it] && (water?.invoke(it) == true || (waterOn && it == entry)) }
		if (wet.isNotEmpty()) g = g.withWater(*wet.toIntArray())
		return g
	}

	private val toggleAt: Map<Int, Toggle> by lazy { toggles.associateBy { it.index } }

	fun doorsWet(g: WaterGrid): Int = doorTriggers.entries.fold(0) { m, (d, cells) -> if (cells.any { g.wet(it) }) m or d.bit else m }

	val signature: String by lazy {
		buildString {
			append(width).append('x').append(height).append(':').append(entry).append(':')
			for (i in 0 until size) append(if (solid[i]) '#' else if (sink[i]) '~' else '.')
			for (t in toggles.sortedBy { it.index }) append(t.gate.letter).append(t.index).append(if (t.closedAtBase) '+' else '-')
			for ((d, c) in doorTriggers.toSortedMap()) append(d.letter).append(c.joinToString(","))
		}
	}

	fun render(config: Int = 0, wet: ((Int) -> Boolean)? = null): String {
		val pass = passable(config)
		val tog = toggles.associateBy { it.index }
		val trig = HashMap<Int, Door>()
		for ((d, cells) in doorTriggers) for (c in cells) trig.putIfAbsent(c, d)
		return buildString {
			for (v in height - 1 downTo 0) {
				for (u in 0 until width) {
					val i = index(u, v)
					val t = tog[i]
					append(
						when {
							i == entry -> 'S'
							t != null -> if (pass[i]) (if (wet?.invoke(i) == true) '~' else t.gate.letter.lowercaseChar()) else t.gate.letter
							solid[i] -> '#'
							wet?.invoke(i) == true -> '~'
							trig[i] != null -> trig[i]!!.letter.lowercaseChar().let { if (it == 'o') '0' else it }
							sink[i] -> '_'
							else -> '.'
						}
					)
				}
				append('\n')
			}
		}
	}

	companion object {

		fun parse(rows: List<String>): Board {
			val h = rows.size
			val w = rows.maxOf { it.length }
			val solid = BooleanArray(w * h)
			val sink = BooleanArray(w * h)
			val toggles = mutableListOf<Toggle>()
			val wool = mutableMapOf<Door, MutableList<Int>>()
			var entry = -1
			for ((r, row) in rows.withIndex()) {
				val v = h - 1 - r
				for (u in 0 until w) {
					val i = v * w + u
					val ch = row.getOrElse(u) { '#' }
					val gate = Gate.ofLetter(ch)
					val door = Door.ofLetter(ch)
					when {
						ch == '#' -> solid[i] = true
						ch == 'S' -> entry = i
						ch == '_' -> sink[i] = true
						ch == '.' -> {}
						door != null -> { solid[i] = true; wool.getOrPut(door) { mutableListOf() } += i }
						gate != null -> toggles += Toggle(gate, i, ch.isUpperCase())
						else -> error("unknown board char '$ch'")
					}
				}
			}
			require(entry >= 0) { "no entry S" }
			val triggers = wool.mapValues { (_, cells) ->
				cells.flatMap { i ->
					val u = i % w; val v = i / w
					listOf(u - 1 to v, u + 1 to v, u to v + 1, u to v - 1)
						.filter { (a, b) -> a in 0 until w && b in 0 until h && !solid[b * w + a] }
						.map { (a, b) -> b * w + a }
				}.distinct().toIntArray()
			}
			return Board(w, h, solid, toggles, sink, entry, triggers)
		}
	}
}
