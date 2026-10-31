/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: pure logic for the Nearest Locked Door highlight and the key-holder path (part of Key Highlight).
 * Written from scratch. Ideas from other dungeon mods (CREDITS.md): door positions (wither door = coal block, blood
 * door = red terracotta, rooms on a 32-block grid from -200,-200), dungeon map door slots between room cells and the
 * key/door chat lines. Ideas only, no code copied.
 *
 * fix-dungeon-doors-1018:
 *  - Door order: only the NEXT door is a target, i.e. a locked door that is exposed (one side is a room the player
 *    can walk to through open slots: normal doorways, merged-room gaps, opened doors). Never "the closest locked
 *    door anywhere" (that lit the blood door from the far side of the map). No exposed door = nothing.
 *  - Void gaps: the 1-block gap between two rooms that are NOT connected is empty air all the way down.
 *
 * fix-keypath-1018: a gap slot is connected (OPEN) whenever its centre column holds ANY block in y [COLUMN_Y]
 * (the standard dungeon-map scan rule: a column that is not all air is a doorway, the entrance gate or a merged-room
 * separator; only a void gap is not). The old rule ("air at y 69..71 AND a floor at y 68") marked merged-room
 * separators with anything in the middle of the room (pillar, ledge, pit) and the entrance gate (infested block at
 * y 69) as WALL, which cut the player's own room off from the map: no exposed door, so no box and no key path.
 *  - Opened doors are latched ([scan] `opened`): once a door was seen opening (chat line or its falling-block
 *    animation) it is never a target again, even while animation blocks are still in the slot.
 *  - The key path itself is walked by the shared block A* (BlockPathPlanner); this file only gives it the rooms on
 *    the route ([routeBox]) so the snapshot stays small.
 */
package dev.ashtonite365.apex.features.kit.dungeon

object DoorLogic {

	enum class Probe { AIR, SOLID, COAL, BLOOD, UNLOADED, PASSABLE }
	enum class DoorType { WITHER, BLOOD }

	const val ORIGIN = -200
	const val STEP = 32
	const val HALF = 15
	const val ROOMS = 6

	val DOOR_Y = 69..72
	const val FLOOR_Y = 68

	const val FEET_Y = 69

	data class Cell(val i: Int, val j: Int) {
		val x get() = ORIGIN + HALF + STEP * i
		val z get() = ORIGIN + HALF + STEP * j
	}

	data class Slot(val a: Cell, val east: Boolean) {
		val b get() = if (east) Cell(a.i + 1, a.j) else Cell(a.i, a.j + 1)
		val x get() = if (east) a.x + 16 else a.x
		val z get() = if (east) a.z else a.z + 16
	}

	data class Door(val slot: Slot, val type: DoorType)

	val SLOTS: List<Slot> = buildList {
		for (i in 0 until ROOMS) for (j in 0 until ROOMS) {
			if (i + 1 < ROOMS) add(Slot(Cell(i, j), true))
			if (j + 1 < ROOMS) add(Slot(Cell(i, j), false))
		}
	}

	enum class SlotState { OPEN, WALL, WITHER, BLOOD, UNKNOWN }

	val COLUMN_Y = 12..140

	fun isOpenDoorway(passable: (Int) -> Boolean): Boolean =
		(DOOR_Y.first until DOOR_Y.last).any { y -> passable(y) && passable(y + 1) }

	fun isWalkable(p: Probe) = p == Probe.AIR || p == Probe.PASSABLE

	const val MIN_COAL = 2

	const val MAP_WITHER = 119

	data class SlotContext(val fairyBeyond: Boolean = false, val mapPixel: Int? = null)

	@Suppress("UNUSED_PARAMETER")
	fun witherAllowed(ctx: SlotContext): Boolean = true

	fun isWitherDoor(coal: Boolean, open: Boolean, ctx: () -> SlotContext): Boolean = coal && !open && witherAllowed(ctx())

	fun classify(slot: Slot, probe: (Int, Int, Int) -> Probe, context: (Slot) -> SlotContext = { SlotContext() }): SlotState {
		val ps = DOOR_Y.map { probe(slot.x, it, slot.z) }
		return when {
			Probe.UNLOADED in ps -> SlotState.UNKNOWN
			Probe.BLOOD in ps -> SlotState.BLOOD
			isWitherDoor(ps.count { it == Probe.COAL } >= MIN_COAL, isOpenDoorway { y -> isWalkable(ps[y - DOOR_Y.first]) }) { context(slot) } -> SlotState.WITHER
			ps.any { it != Probe.AIR } -> SlotState.OPEN
			else -> {
				for (y in COLUMN_Y) {
					if (y in DOOR_Y) continue
					when (probe(slot.x, y, slot.z)) {
						Probe.AIR -> {}
						Probe.UNLOADED -> return SlotState.UNKNOWN
						else -> return SlotState.OPEN
					}
				}
				SlotState.WALL
			}
		}
	}

	fun scan(
		probe: (Int, Int, Int) -> Probe, previous: Map<Slot, SlotState> = emptyMap(), opened: Set<Slot> = emptySet(),
		context: (Slot) -> SlotContext = { SlotContext() },
	): Map<Slot, SlotState> =
		SLOTS.associateWith { s ->
			if (s in opened) SlotState.OPEN
			else classify(s, probe, context).let { if (it == SlotState.UNKNOWN) previous[s] ?: it else it }
		}

	fun lockedDoors(states: Map<Slot, SlotState>): List<Door> = states.mapNotNull { (s, st) ->
		when (st) { SlotState.WITHER -> Door(s, DoorType.WITHER); SlotState.BLOOD -> Door(s, DoorType.BLOOD); else -> null }
	}

	fun cellOf(x: Double, z: Double): Cell? {
		val i = Math.floorDiv(Math.floor(x).toInt() - ORIGIN, STEP); val j = Math.floorDiv(Math.floor(z).toInt() - ORIGIN, STEP)
		return if (i in 0 until ROOMS && j in 0 until ROOMS) Cell(i, j) else null
	}

	fun slotNear(x: Int, z: Int): Slot? = SLOTS.firstOrNull { s ->
		if (s.east) x == s.x && z in s.z - 1..s.z + 1 else z == s.z && x in s.x - 1..s.x + 1
	}

	fun reachable(states: Map<Slot, SlotState>, from: Cell): Map<Cell, Int> {
		val dist = HashMap<Cell, Int>().apply { put(from, 0) }
		val queue = ArrayDeque(listOf(from))
		while (queue.isNotEmpty()) {
			val c = queue.removeFirst()
			for ((slot, st) in states) {
				if (st != SlotState.OPEN) continue
				val n = when (c) { slot.a -> slot.b; slot.b -> slot.a; else -> continue }
				if (n !in dist) { dist[n] = dist.getValue(c) + 1; queue.addLast(n) }
			}
		}
		return dist
	}

	fun exposedDoors(states: Map<Slot, SlotState>, reach: Map<Cell, Int>): List<Door> =
		lockedDoors(states).filter { it.slot.a in reach || it.slot.b in reach }

	fun nextDoor(states: Map<Slot, SlotState>, px: Double, pz: Double, held: DoorType? = null): Door? {
		val from = cellOf(px, pz) ?: return null
		val reach = reachable(states, from)
		val exposed = exposedDoors(states, reach)
		fun rooms(d: Door) = minOf(reach[d.slot.a] ?: Int.MAX_VALUE, reach[d.slot.b] ?: Int.MAX_VALUE)
		fun dist2(d: Door) = (d.slot.x + 0.5 - px).let { it * it } + (d.slot.z + 0.5 - pz).let { it * it }
		return exposed.minWithOrNull(
			compareBy<Door>({ if (held != null && it.type == held) 0 else 1 }, { if (it.type == DoorType.WITHER) 0 else 1 }, ::rooms, ::dist2))
	}

	data class Diag(val cell: Cell?, val open: Int, val wall: Int, val unknown: Int, val locked: Int, val reach: Int, val exposed: Int, val door: Door?) {
		val reason get() = when {
			cell == null -> "player outside the room grid"
			locked == 0 -> "no locked door scanned"
			exposed == 0 -> "no exposed door (reach=$reach rooms)"
			else -> "ok"
		}
		override fun toString() = "cell=${cell?.let { "${it.i},${it.j}" }} slots open=$open wall=$wall unknown=$unknown locked=$locked " +
			"reach=$reach exposed=$exposed door=${door?.let { "${it.type}@${it.slot.x},${it.slot.z}" }} ($reason)"
	}

	fun diagnose(states: Map<Slot, SlotState>, px: Double, pz: Double, held: DoorType? = null): Diag {
		val from = cellOf(px, pz)
		val reach = from?.let { reachable(states, it) } ?: emptyMap()
		fun n(st: SlotState) = states.values.count { it == st }
		return Diag(from, n(SlotState.OPEN), n(SlotState.WALL), n(SlotState.UNKNOWN), lockedDoors(states).size, reach.size,
			if (from == null) 0 else exposedDoors(states, reach).size, nextDoor(states, px, pz, held))
	}

	fun route(states: Map<Slot, SlotState>, from: Cell, target: Door): List<Cell>? {
		val goals = setOf(target.slot.a, target.slot.b)
		val prev = HashMap<Cell, Cell?>().apply { put(from, null) }
		val queue = ArrayDeque(listOf(from))
		while (queue.isNotEmpty()) {
			val c = queue.removeFirst()
			if (c in goals) return generateSequence(c) { prev[it] }.toList().reversed()
			for ((slot, st) in states) {
				if (st != SlotState.OPEN) continue
				val n = when (c) { slot.a -> slot.b; slot.b -> slot.a; else -> continue }
				if (n !in prev) { prev[n] = c; queue.addLast(n) }
			}
		}
		return null
	}

	data class Area(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int)

	fun routeArea(states: Map<Slot, SlotState>, px: Double, pz: Double, target: Door): Area? {
		val start = cellOf(px, pz) ?: return null
		val cells = route(states, start, target) ?: return null
		fun lo(i: Int) = ORIGIN + STEP * i - 1
		fun hi(i: Int) = ORIGIN + STEP * i + STEP
		return Area(lo(cells.minOf { it.i }), lo(cells.minOf { it.j }), hi(cells.maxOf { it.i }), hi(cells.maxOf { it.j }))
	}

	fun openedByChat(line: String): DoorType? = when {
		line.endsWith(" opened a WITHER door!") -> DoorType.WITHER
		line == "The BLOOD DOOR has been opened!" -> DoorType.BLOOD
		else -> null
	}

	class KeyState {
		var wither = 0; private set
		var blood = false; private set
		fun onChat(line: String) {
			when {
				Regex("has obtained Wither Key|^A Wither Key was picked up").containsMatchIn(line) -> wither++
				Regex("has obtained Blood Key|^A Blood Key was picked up").containsMatchIn(line) -> blood = true
				openedByChat(line) == DoorType.WITHER -> wither = (wither - 1).coerceAtLeast(0)
				openedByChat(line) == DoorType.BLOOD -> blood = false
			}
		}
		fun reset() { wither = 0; blood = false }

		fun held(): DoorType? = if (blood) DoorType.BLOOD else if (wither > 0) DoorType.WITHER else null
	}

}

fun DoorLogic.mapDoors(states: Map<DoorLogic.Slot, DoorLogic.SlotState>): Map<dev.ashtonite365.apex.features.dungeon.DungeonMap.DoorKey, Int> =
	lockedDoors(states).associate { d ->
		dev.ashtonite365.apex.features.dungeon.DungeonMap.DoorKey(d.slot.a.i, d.slot.a.j, d.slot.east) to
			if (d.type == DoorLogic.DoorType.WITHER) MAP_WITHER else 18
	}
