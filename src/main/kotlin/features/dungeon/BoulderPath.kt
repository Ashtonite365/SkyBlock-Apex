/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Boulder puzzle walking path (new, written from scratch for Apex). Visual only: draws a floor line
 * from the player to the next box to push and, once every button is pressed, to the reward chest. It never clicks,
 * moves or sends packets. Pure (no world access): the caller passes a [Walkable] view of the room, so it is unit-tested.
 *
 * The path never climbs to the button: it ends on the face of the box block directly behind the button, at walking
 * level just above the floor ([LIFT]), because that box is what the player walks into. The solver's steps
 * ([Boulder.Step]) are read as-is; this file never changes the solution.
 *
 * 25 Oct 2026 (F7, path not drawn): the floor level used to be the constant [FEET_Y] = 65. In Ashton's F7 Boulder
 * room (latest.log 14:22:03-14:22:20 AEDT) the solver solved the board ("solution len=1") but the player stood at
 * feet y=64 ("[ApexDoorPath] ... feet=-175, 64, -180 cell=0,0", again "-189, 64, -176"): the floor top is one block
 * lower than assumed, so every tile failed [standable] at y=65 (its "floor" block was the air the player stands in),
 * [startTile] found nothing and no route was ever drawn. The feet level is now read from the room: the bottom block
 * of a boulder of the solved board ([feetLevel]; boulders stand on the floor), else the player's own feet while on
 * the ground ([resolveFeetY]), else [FEET_Y]. Every function takes that level as `feetY`.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sign

object BoulderPath {

	const val FEET_Y = 65

	const val BOARD_Y = 66

	const val MIN_FEET_Y = 60

	const val LIFT = 0.1

	const val MAX_NODES = 4096

	fun interface Walkable { fun at(x: Int, z: Int): Boolean }

	data class Target(val box: BlockPos, val stand: BlockPos)

	data class Route(val target: Target, val toChest: Boolean, val tiles: List<BlockPos>, val points: List<Vec3>)

	fun feetLevel(columns: Collection<Pair<Int, Int>>, isBoulder: (BlockPos) -> Boolean): Int? =
		columns.mapNotNull { (x, z) ->
			if (!isBoulder(BlockPos(x, BOARD_Y, z))) return@mapNotNull null
			var y = BOARD_Y
			while (y > MIN_FEET_Y && isBoulder(BlockPos(x, y - 1, z))) y--
			y
		}.minOrNull()

	fun resolveFeetY(fromBoulders: Int?, playerFeetY: Int?, onGround: Boolean): Int =
		fromBoulders ?: playerFeetY?.takeIf { onGround && it in MIN_FEET_Y..BOARD_Y } ?: FEET_Y

	fun standable(x: Int, z: Int, feetY: Int, solid: (BlockPos) -> Boolean): Boolean =
		solid(BlockPos(x, feetY - 1, z)) && !solid(BlockPos(x, feetY, z)) && !solid(BlockPos(x, feetY + 1, z))

	fun enabled(solverOn: Boolean, pathOn: Boolean): Boolean = solverOn && pathOn

	fun targetFor(step: Boulder.Step, feetY: Int = FEET_Y): Target {
		val c = step.click
		val dx = (step.render.x - c.x).sign
		val dz = (step.render.z - c.z).sign

		val (sx, sz) = if (abs(step.render.x - c.x) >= abs(step.render.z - c.z)) dx to 0 else 0 to dz
		val stand = BlockPos(c.x, feetY, c.z)
		return Target(BlockPos(c.x + sx, feetY, c.z + sz), stand)
	}

	fun chestTargets(chest: BlockPos, walkable: Walkable, feetY: Int = FEET_Y): List<Target> =
		listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
			.filter { (dx, dz) -> walkable.at(chest.x + dx, chest.z + dz) }
			.map { (dx, dz) -> Target(BlockPos(chest.x, feetY, chest.z), BlockPos(chest.x + dx, feetY, chest.z + dz)) }

	fun findPath(start: BlockPos, goals: Collection<BlockPos>, walkable: Walkable, maxNodes: Int = MAX_NODES): List<BlockPos>? {
		val goalSet = goals.map { it.x to it.z }.toSet()
		if (goalSet.isEmpty()) return null
		val s = start.x to start.z
		fun h(p: Pair<Int, Int>) = goalSet.minOf { abs(it.first - p.first) + abs(it.second - p.second) }
		val g = HashMap<Pair<Int, Int>, Int>().apply { put(s, 0) }
		val from = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
		var seq = 0

		val open = PriorityQueue<IntArray>(compareBy<IntArray>({ it[0] }, { it[1] }))
		open.add(intArrayOf(h(s), seq++, s.first, s.second))
		val closed = HashSet<Pair<Int, Int>>()
		while (open.isNotEmpty()) {
			val cur = open.poll().let { it[2] to it[3] }
			if (!closed.add(cur)) continue
			if (cur in goalSet) {
				val out = ArrayList<BlockPos>()
				var p: Pair<Int, Int>? = cur
				while (p != null) { out += BlockPos(p.first, start.y, p.second); p = from[p] }
				return out.asReversed()
			}
			if (closed.size > maxNodes) return null
			val gc = g.getValue(cur)
			for ((dx, dz) in NEIGHBOURS) {
				val n = cur.first + dx to cur.second + dz
				if (n in closed || !walkable.at(n.first, n.second)) continue
				if (gc + 1 < (g[n] ?: Int.MAX_VALUE)) {
					g[n] = gc + 1; from[n] = cur
					open.add(intArrayOf(gc + 1 + h(n), seq++, n.first, n.second))
				}
			}
		}
		return null
	}

	private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

	fun startTile(playerX: Int, playerZ: Int, walkable: Walkable, feetY: Int = FEET_Y): BlockPos? {
		if (walkable.at(playerX, playerZ)) return BlockPos(playerX, feetY, playerZ)
		for (r in 1..2) for (dx in -r..r) for (dz in -r..r) {
			if (maxOf(abs(dx), abs(dz)) != r) continue
			if (walkable.at(playerX + dx, playerZ + dz)) return BlockPos(playerX + dx, feetY, playerZ + dz)
		}
		return null
	}

	fun route(
		playerX: Int, playerZ: Int, steps: List<Boulder.Step>, chest: BlockPos?, walkable: Walkable,
		solverOn: Boolean, pathOn: Boolean, feetY: Int = FEET_Y,
	): Route? {
		if (!enabled(solverOn, pathOn)) return null
		val start = startTile(playerX, playerZ, walkable, feetY) ?: return null
		val targets = steps.firstOrNull()?.let { listOf(targetFor(it, feetY)) } ?: chest?.let { chestTargets(it, walkable, feetY) } ?: return null
		if (targets.isEmpty()) return null
		val tiles = findPath(start, targets.map { it.stand }, walkable) ?: return null
		val end = tiles.last()
		val target = targets.first { it.stand.x == end.x && it.stand.z == end.z }
		return Route(target, steps.isEmpty(), tiles, linePoints(tiles, target))
	}

	fun linePoints(tiles: List<BlockPos>, target: Target): List<Vec3> {
		val y = target.stand.y + LIFT
		val pts = ArrayList<Vec3>()
		for ((i, t) in tiles.withIndex()) {
			if (i in 1 until tiles.size - 1) {
				val a = tiles[i - 1]; val b = tiles[i + 1]
				if ((t.x - a.x == b.x - t.x) && (t.z - a.z == b.z - t.z)) continue
			}
			pts += Vec3(t.x + 0.5, y, t.z + 0.5)
		}
		val s = target.stand; val b = target.box
		pts += Vec3((s.x + b.x) / 2.0 + 0.5, y, (s.z + b.z) / 2.0 + 0.5)
		return pts
	}

	class Tracker {
		private var key: List<Any?>? = null
		var route: Route? = null; private set
		var computations = 0; private set

		fun update(
			playerX: Int, playerZ: Int, steps: List<Boulder.Step>, chest: BlockPos?, walkable: Walkable,
			solverOn: Boolean, pathOn: Boolean, force: Boolean = false, feetY: Int = FEET_Y,
		): Route? {
			val k = listOf(playerX, playerZ, steps.size, steps.firstOrNull(), chest, solverOn, pathOn, feetY)
			if (!force && k == key) return route
			key = k
			computations++
			route = route(playerX, playerZ, steps, chest, walkable, solverOn, pathOn, feetY)
			return route
		}

		fun clear() { key = null; route = null }
	}
}
