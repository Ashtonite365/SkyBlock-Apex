package dev.ashtonite365.apex.test.features.world

import kotlin.math.sqrt
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.BlockPathPlanner
import dev.ashtonite365.apex.features.world.SoulAStar

class BlockPathPlannerTest {

	private class Rooms : BlockPathPlanner.Source {
		var reads = 0
		override fun columnLoaded(x: Int, z: Int) = true
		override fun cell(x: Int, y: Int, z: Int): Byte {
			reads++
			return when {
				x !in 0..40 || z !in 0..20 -> SoulAStar.SOLID
				y < 0 -> SoulAStar.SOLID
				x == 20 && y <= 4 && z !in 15..17 -> SoulAStar.SOLID
				else -> SoulAStar.AIR
			}
		}
	}

	private val box = BlockPathPlanner.Box(-1, -2, -1, 41, 6, 21)
	private val start = BlockPos(5, 0, 5)
	private val target = BlockPos(35, 0, 5)

	private fun planner(cellsPerTick: Int, nodeCap: Int = 30_000) = BlockPathPlanner(
		nodeCap = nodeCap, maxDist = 200, replanDistSq = 2.0, minTicksBetween = 10, cellsPerTick = cellsPerTick,
		straightFallback = false, keepLastFound = true, drawHeight = 0.1,
	).also { it.synchronous = true }

	private fun length(points: List<net.minecraft.world.phys.Vec3>) =
		points.zipWithNext().sumOf { (a, b) -> sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z)) }

	@Test fun routesAroundTheWallThroughTheDoorway() {
		val src = Rooms()
		val p = planner(cellsPerTick = 200)
		var ticks = 0
		while (p.result == null && ticks < 500) { p.update(start, target, src, -2, 6, box); ticks++ }
		assertNotNull(p.result)
		val r = p.result!!
		assertTrue(r.found)
		assertEquals(1, p.plansStarted)

		assertTrue(ticks > 20, "snapshot took $ticks ticks")

		for ((a, b) in r.points.zipWithNext()) {
			if ((a.x - 20.5) * (b.x - 20.5) <= 0 && a.x != b.x) {
				val t = (20.5 - a.x) / (b.x - a.x)
				val z = a.z + (b.z - a.z) * t
				assertTrue(z in 14.5..18.5, "crosses the wall at z=$z")
			}
		}

		val len = length(r.points)
		assertTrue(len in 36.0..44.0, "length $len")

		assertEquals(0.1, r.points.first().y, 1e-9)
		assertEquals(target.x + 0.5, r.points.last().x, 1e-9)
	}

	@Test fun replansAfterABlockOrTwoWithAThrottle() {
		val src = Rooms()
		val p = planner(cellsPerTick = Int.MAX_VALUE)
		p.update(start, target, src, -2, 6, box)
		assertEquals(1, p.plansStarted); assertTrue(p.result!!.found)

		p.update(start.offset(1, 0, 0), target, src, -2, 6, box)
		assertEquals(1, p.plansStarted)

		val moved = start.offset(2, 0, 0)
		var ticks = 1
		while (p.plansStarted == 1 && ticks < 30) { p.update(moved, target, src, -2, 6, box); ticks++ }
		assertEquals(2, p.plansStarted)
		assertTrue(ticks >= 10, "re-planned after $ticks ticks")
		assertTrue(p.result!!.found)
		assertEquals(moved.x + 0.5, p.result!!.points.first().x, 1e-9)

		p.update(moved, target.offset(0, 0, 2), src, -2, 6, box)
		assertEquals(3, p.plansStarted)
	}

	@Test fun keepsTheLastRouteWhileReplanning() {
		val src = Rooms()
		val p = planner(cellsPerTick = 500)
		repeat(200) { p.update(start, target, src, -2, 6, box) }
		val first = p.result!!
		assertTrue(first.found)
		p.update(start.offset(0, 0, 3), target, src, -2, 6, box)
		assertEquals(2, p.plansStarted)
		assertEquals(first, p.result)
	}

	@Test fun noRouteMeansNoLineNotAStraightOne() {

		val p = planner(cellsPerTick = Int.MAX_VALUE, nodeCap = 40)
		p.update(start, target, Rooms(), -2, 6, box)
		assertNull(p.result)

		val q = planner(cellsPerTick = Int.MAX_VALUE)
		q.update(start, target, Rooms(), -2, 6, box)
		assertNotNull(q.result)
		q.update(start, null, Rooms(), -2, 6, box)
		assertNull(q.result)
	}

	private class Carpet(val carpet: BlockPos) : BlockPathPlanner.Source {
		val base = Rooms()
		override fun columnLoaded(x: Int, z: Int) = true
		override fun cell(x: Int, y: Int, z: Int): Byte = if (x == carpet.x && y == carpet.y && z == carpet.z) SoulAStar.SOLID else base.cell(x, y, z)
	}

	@Test fun startingOnACarpetOrSlabStillFindsTheRoute() {

		val src = Carpet(start)
		val p = planner(cellsPerTick = Int.MAX_VALUE)
		p.update(start, target, src, -2, 6, box)
		val r = p.result
		assertNotNull(r)
		assertTrue(r!!.found)
		assertEquals(SoulAStar.P(start.x, start.y + 1, start.z), BlockPathPlanner.startCell(SoulAStar.Grid { x, y, z -> src.cell(x, y, z) }, start))
		assertTrue(p.lastOutcome!!.found)
		assertTrue(p.lastOutcome!!.nodes > 0)
	}

	@Test fun outcomeSaysWhyThereIsNoRoute() {
		val p = planner(cellsPerTick = Int.MAX_VALUE, nodeCap = 40)
		p.update(start, target, Rooms(), -2, 6, box)
		val o = p.lastOutcome!!
		assertTrue(!o.found)
		assertTrue(o.reason!!.startsWith("goal not reached"), o.reason)
		assertTrue(o.nodes > 40, "nodes ${o.nodes}")

		val q = planner(cellsPerTick = Int.MAX_VALUE)
		q.update(BlockPos(20, 1, 5), target, Rooms(), -2, 6, box)
		assertTrue(q.lastOutcome!!.reason!!.startsWith("start not standable"), q.lastOutcome!!.reason)
		assertNull(q.result)
	}

	@Test fun goalRadiusReachesADoorBehindAFrame() {

		val src = object : BlockPathPlanner.Source {
			val base = Rooms()
			override fun columnLoaded(x: Int, z: Int) = true
			override fun cell(x: Int, y: Int, z: Int): Byte = if ((x == 19 || x == 21) && z in 4..6 && y in 0..1) SoulAStar.SOLID else base.cell(x, y, z)
		}
		val door = BlockPos(20, 0, 5)
		fun plan(radius: Double) = BlockPathPlanner(
			nodeCap = 30_000, maxDist = 200, replanDistSq = 2.0, minTicksBetween = 10, cellsPerTick = Int.MAX_VALUE,
			straightFallback = false, keepLastFound = true, drawHeight = 0.1, goalRadius = radius,
		).also { it.synchronous = true; it.update(start, door, src, -2, 6, box) }
		assertNull(plan(1.5).result)
		assertTrue(plan(2.5).result!!.found)
	}
}
