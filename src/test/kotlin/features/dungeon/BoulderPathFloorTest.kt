package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.Boulder
import dev.ashtonite365.apex.features.dungeon.BoulderPath
import dev.ashtonite365.apex.features.dungeon.PuzzleRoom
import kotlin.math.abs

class BoulderPathFloorTest {

	private val corner = BlockPos(-170, 0, -200)
	private val key = "100100101000100010100010101000101000100010"

	private inner class World(val floorY: Int, val rot: Int) {
		val boulders = HashSet<BlockPos>()
		val room = HashSet<Pair<Int, Int>>()
		init {
			for (lx in 0..30) for (lz in 0..30) PuzzleRoom.toReal(corner, BlockPos(lx, 0, lz), rot).let { room += it.x to it.z }
			var i = 0
			for (z in 24 downTo 9 step 3) for (x in 24 downTo 6 step 3) {
				if (key[i++] != '1') continue
				for (dx in -1..1) for (dz in -1..1) for (y in floorY + 1..floorY + 3)
					boulders += PuzzleRoom.toReal(corner, BlockPos(x + dx, y, z + dz), rot)
			}
		}
		fun isBoulder(p: BlockPos) = p in boulders
		fun solid(p: BlockPos) = (p.x to p.z) in room && (p.y == floorY || p in boulders)
		fun walkable(feetY: Int) = BoulderPath.Walkable { x, z -> (x to z) in room && BoulderPath.standable(x, z, feetY, ::solid) }
		fun steps() = (Boulder.scan(corner, Boulder.table, { !isBoulder(it) }, ::isBoulder) as Boulder.Scan.Solved).steps
		fun entrance() = PuzzleRoom.toReal(corner, BlockPos(15, 0, 28), rot)
	}

	@Test
	fun floorOneBlockLowerThanAssumedStillDrawsThePath() {
		for (rot in 0..3) {
			val w = World(floorY = 63, rot = rot)
			val steps = w.steps()
			assertEquals(1, steps.size, "the logged board has a one-step solution")
			val s = w.entrance()

			assertNull(BoulderPath.startTile(s.x, s.z, w.walkable(BoulderPath.FEET_Y)), "rot $rot")
			assertNull(BoulderPath.route(s.x, s.z, steps, null, w.walkable(BoulderPath.FEET_Y), true, true))

			val feetY = BoulderPath.feetLevel(steps.map { it.render.x to it.render.z }, w::isBoulder)
			assertEquals(64, feetY, "rot $rot")
			val resolved = BoulderPath.resolveFeetY(feetY, null, false)
			val route = BoulderPath.route(s.x, s.z, steps, null, w.walkable(resolved), true, true, feetY = resolved)
			assertNotNull(route, "rot $rot")
			route!!
			assertTrue(route.tiles.all { it.y == 64 })
			assertTrue(route.points.all { abs(it.y - (64 + BoulderPath.LIFT)) < 1e-9 }, "line on the real floor")
			assertTrue(w.isBoulder(route.target.box.above()), "ends at the box behind the button")
			assertEquals(steps[0].click.x to steps[0].click.z, route.tiles.last().x to route.tiles.last().z)

			assertNotNull(BoulderPath.Tracker().update(s.x, s.z, steps, null, w.walkable(resolved), true, true, feetY = resolved))
		}
	}

	@Test
	fun floorAtTheOldLevelKeepsWorking() {
		val w = World(floorY = 64, rot = 1)
		val steps = w.steps()
		val feetY = BoulderPath.feetLevel(steps.map { it.render.x to it.render.z }, w::isBoulder)
		assertEquals(65, feetY)
		val s = w.entrance()
		assertNotNull(BoulderPath.route(s.x, s.z, steps, null, w.walkable(feetY!!), true, true, feetY = feetY))
	}

	@Test
	fun feetLevelFallsBackToThePlayerThenTheConstant() {
		assertNull(BoulderPath.feetLevel(listOf(0 to 0)) { false })
		assertEquals(64, BoulderPath.resolveFeetY(64, 70, true))
		assertEquals(64, BoulderPath.resolveFeetY(null, 64, true))
		assertEquals(BoulderPath.FEET_Y, BoulderPath.resolveFeetY(null, 64, false), "mid-jump feet are not the floor")
		assertEquals(BoulderPath.FEET_Y, BoulderPath.resolveFeetY(null, 90, true), "not in the room's range")
		assertEquals(BoulderPath.FEET_Y, BoulderPath.resolveFeetY(null, null, true))
	}
}
