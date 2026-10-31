package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.Boulder
import dev.ashtonite365.apex.features.dungeon.BoulderPath
import dev.ashtonite365.apex.features.dungeon.PuzzleRoom
import kotlin.math.abs

class BoulderPathTest {
	private val corner = BlockPos(-136, 0, -200)

	private inner class Room(key: String, val rot: Int) {
		val cells = mutableSetOf<Pair<Int, Int>>()
		val extraWalls = mutableSetOf<Pair<Int, Int>>()
		init {
			var i = 0
			for (z in 24 downTo 9 step 3) for (x in 24 downTo 6 step 3) { if (key[i++] == '1') cells += x to z }
		}
		fun real(x: Int, z: Int) = PuzzleRoom.toReal(corner, BlockPos(x, BoulderPath.FEET_Y, z), rot)
		private fun local(x: Int, z: Int): Pair<Int, Int> {
			for (lx in 0..30) for (lz in 0..30) { val r = real(lx, lz); if (r.x == x && r.z == z) return lx to lz }
			return -1 to -1
		}
		private val localCache = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
		fun localOf(x: Int, z: Int) = localCache.getOrPut(x to z) { local(x, z) }
		fun boulderAt(x: Int, z: Int): Boolean {
			val (lx, lz) = localOf(x, z)
			return cells.any { (cx, cz) -> abs(lx - cx) <= 1 && abs(lz - cz) <= 1 }
		}
		val walkable = BoulderPath.Walkable { x, z ->
			val (lx, lz) = localOf(x, z)
			lx in 0..30 && lz in 0..30 && (x to z) !in extraWalls && !boulderAt(x, z)
		}

		fun push(step: Boulder.Step) {
			val box = BoulderPath.targetFor(step).box
			val (bx, bz) = localOf(box.x, box.z)
			val (kx, kz) = localOf(step.click.x, step.click.z)
			val cell = cells.first { (cx, cz) -> abs(bx - cx) <= 1 && abs(bz - cz) <= 1 }
			cells -= cell
			cells += (cell.first + 3 * (bx - kx)) to (cell.second + 3 * (bz - kz))
		}
	}

	private fun steps(key: String, rot: Int): List<Boulder.Step> {
		val room = Room(key, rot)
		val blocks = mutableSetOf<BlockPos>()
		for ((cx, cz) in room.cells) blocks += PuzzleRoom.toReal(corner, BlockPos(cx, 66, cz), rot)
		val scan = Boulder.scan(corner, Boulder.table, { it !in blocks }, { it in blocks })
		return (scan as Boulder.Scan.Solved).steps
	}

	private fun Room.entrance() = real(15, 28)

	private fun onBoxFace(p: Vec3, box: BlockPos): Boolean {
		val minX = box.x.toDouble(); val maxX = minX + 1; val minZ = box.z.toDouble(); val maxZ = minZ + 1
		val inside = p.x in minX..maxX && p.z in minZ..maxZ
		val onFace = abs(p.x - minX) < 1e-9 || abs(p.x - maxX) < 1e-9 || abs(p.z - minZ) < 1e-9 || abs(p.z - maxZ) < 1e-9
		return inside && onFace
	}

	@Test
	fun endsAtTheBoxUnderTheButtonNotAtTheButton() {
		var checked = 0
		for ((key, _) in Boulder.table) for (rot in 0..3) {
			val room = Room(key, rot)
			val all = steps(key, rot)
			for ((i, step) in all.withIndex()) {
				val start = room.entrance()
				val route = BoulderPath.route(start.x, start.z, all.drop(i), null, room.walkable, solverOn = true, pathOn = true)
				assertNotNull(route, "$key rot $rot step $i")
				route!!
				val box = route.target.box

				assertTrue(room.boulderAt(box.x, box.z), "box is a boulder block: $key rot $rot step $i")
				assertEquals(1, abs(box.x - step.click.x) + abs(box.z - step.click.z))
				assertNotEquals(step.click, box)
				val end = route.points.last()
				assertTrue(onBoxFace(end, box), "ends on the box face: $end $box")

				assertEquals(BoulderPath.FEET_Y + BoulderPath.LIFT, end.y, 1e-9)
				assertTrue(end.y < step.click.y + 0.5)
				assertTrue(route.points.all { it.y == end.y }, "flat on the floor")
				assertNotEquals(Vec3.atCenterOf(step.click), end)

				assertEquals(step.click.x to step.click.z, route.tiles.last().x to route.tiles.last().z)
				room.push(step)
				checked++
			}
		}
		assertTrue(checked > 20)
	}

	@Test
	fun avoidsBouldersAndWalls() {
		for ((key, _) in Boulder.table) for (rot in 0..3) {
			val room = Room(key, rot)
			val all = steps(key, rot)
			val s = room.entrance()
			val route = BoulderPath.route(s.x, s.z, all, null, room.walkable, true, true)!!
			route.tiles.forEach { assertTrue(room.walkable.at(it.x, it.z), "walkable $it") }
			route.tiles.zipWithNext().forEach { (a, b) -> assertEquals(1, abs(a.x - b.x) + abs(a.z - b.z), "4-connected") }
		}

		val open = BoulderPath.Walkable { x, z -> x in 0..10 && z in 0..10 && !(z == 5 && x in 0..8) }
		val path = BoulderPath.findPath(BlockPos(0, 65, 0), listOf(BlockPos(0, 65, 10)), open)!!
		assertTrue(path.none { it.z == 5 && it.x <= 8 })
		assertEquals(10 + 2 * 9 + 1, path.size)

		val shut = BoulderPath.Walkable { x, z -> x in 0..10 && z in 0..10 && z != 5 }
		assertNull(BoulderPath.findPath(BlockPos(0, 65, 0), listOf(BlockPos(0, 65, 10)), shut))
	}

	@Test
	fun recomputesAfterAPushAndWhenThePlayerMoves() {
		val (key, _) = Boulder.table.entries.first { it.value.size >= 2 }
		val room = Room(key, 1)
		val all = steps(key, 1).toMutableList()
		val s = room.entrance()
		val tracker = BoulderPath.Tracker()
		val first = tracker.update(s.x, s.z, all, null, room.walkable, true, true)!!
		assertEquals(BoulderPath.targetFor(all[0]), first.target)
		assertEquals(1, tracker.computations)

		tracker.update(s.x, s.z, all, null, room.walkable, true, true)
		assertEquals(1, tracker.computations)

		room.push(all[0]); val pushed = all.removeAt(0)
		val second = tracker.update(s.x, s.z, all, null, room.walkable, true, true)!!
		assertEquals(2, tracker.computations)
		assertEquals(BoulderPath.targetFor(all[0]), second.target)
		assertNotEquals(BoulderPath.targetFor(pushed), second.target)

		val next = second.tiles[1]
		val third = tracker.update(next.x, next.z, all, null, room.walkable, true, true)!!
		assertEquals(3, tracker.computations)
		assertEquals(second.tiles.size - 1, third.tiles.size)

		tracker.update(next.x, next.z, all, null, room.walkable, true, true, force = true)
		assertEquals(4, tracker.computations)
	}

	@Test
	fun goesToTheChestAfterTheLastPress() {
		val (key, _) = Boulder.table.entries.first()
		val room = Room(key, 2)
		val all = steps(key, 2)
		all.forEach { room.push(it) }
		val chest = room.real(15, 2)
		val walk = BoulderPath.Walkable { x, z -> room.walkable.at(x, z) && !(x == chest.x && z == chest.z) }
		val s = room.entrance()
		val route = BoulderPath.route(s.x, s.z, emptyList(), chest, walk, true, true)
		assertNotNull(route)
		route!!
		assertTrue(route.toChest)
		assertEquals(chest, route.target.box)
		val end = route.points.last()
		assertTrue(onBoxFace(end, chest), "ends on the chest face: $end")
		assertEquals(BoulderPath.FEET_Y + BoulderPath.LIFT, end.y, 1e-9)
		route.tiles.forEach { assertTrue(walk.at(it.x, it.z)) }

		assertNull(BoulderPath.route(s.x, s.z, emptyList(), null, walk, true, true))
	}

	@Test
	fun drawsOnlyWhenSolverAndWalkingPathAreBothOn() {
		val (key, _) = Boulder.table.entries.first()
		val room = Room(key, 0)
		val all = steps(key, 0)
		val s = room.entrance()
		for (solver in listOf(true, false)) for (path in listOf(true, false)) {
			val r = BoulderPath.route(s.x, s.z, all, null, room.walkable, solverOn = solver, pathOn = path)
			val t = BoulderPath.Tracker().update(s.x, s.z, all, null, room.walkable, solver, path)
			assertEquals(solver && path, BoulderPath.enabled(solver, path), "solver=$solver path=$path")
			if (solver && path) { assertNotNull(r); assertNotNull(t) } else { assertNull(r, "solver=$solver path=$path"); assertNull(t) }
		}
	}

	@Test
	fun turningWalkingPathOffClearsTheTrackedRoute() {
		val (key, _) = Boulder.table.entries.first()
		val room = Room(key, 3)
		val all = steps(key, 3)
		val s = room.entrance()
		val tracker = BoulderPath.Tracker()
		assertNotNull(tracker.update(s.x, s.z, all, null, room.walkable, true, true))
		assertNull(tracker.update(s.x, s.z, all, null, room.walkable, true, false))
		assertNull(tracker.route)
		assertNull(tracker.update(s.x, s.z, all, null, room.walkable, false, true))
	}
}
