// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Hitbox
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Placement

class CreeperRoomEndToEndTest {
	private fun p(x: Int, y: Int, z: Int) = BlockPos(x, y, z)

	private fun mirror(b: BlockPos) = p(-370 - b.x, 153 - b.y, -50 - b.z)
	private val creeper = Hitbox(-184.8, 76.0, -24.8, -184.2, 77.7, -24.2)
	private val ends = listOf(p(-199, 78, -22), p(-187, 80, -39), p(-199, 74, -33), p(-193, 82, -11))
	private val pairs = ends.map { setOf(it, mirror(it)) }
	private val lanterns = pairs.flatten().toSet() + p(-185, 75, -25)
	private val solid: (Int, Int, Int) -> Boolean = { x, y, z ->
		y <= 68 || y >= 90 || x <= -199 || x >= -171 || z <= -39 || z >= -11 || (x == -185 && z == -25 && y <= 75)
	}
	private val view = Placement.Cubes(solid) { x, y, z -> if (p(x, y, z) in lanterns) "sea_lantern" else if (solid(x, y, z)) "stone" else "air" }
	private val open: (Int, Int, Int) -> Boolean = { x, y, z -> !solid(x, y, z) }

	private fun scan(feet: BlockPos, cell: Pair<Int, Int>): Set<BlockPos> {
		val g = CreeperBeamsLogic.RoomGate
		val cx = g.ORIGIN + g.STEP * cell.first; val cz = g.ORIGIN + g.STEP * cell.second
		return lanterns.filter {
			it.x in maxOf(feet.x - 15, cx)..minOf(feet.x + 15, cx + g.SIZE - 1) &&
				it.z in maxOf(feet.z - 15, cz)..minOf(feet.z + 15, cz + g.SIZE - 1) && it.y in feet.y - 10..feet.y + 12
		}.toSet()
	}

	private fun run(eye: Placement.Eye, roomName: String?): List<CreeperBeamsLogic.Beam> {
		val cell = CreeperBeamsLogic.RoomGate.cellOf(eye.x, eye.z)
		val floorTop = Placement.floorFor(Placement.roomFloor(cell, Math.floor(eye.y).toInt(), view), null)
		assertEquals(69, floorTop, "room floor")
		val sel = Placement.select(listOf(1 to creeper), eye, roomName, floorTop, view)
		assertEquals(1, sel.chosen, sel.reports.joinToString { it.line() })
		val raw = scan(BlockPos.containing(creeper.cx, creeper.minY, creeper.cz), CreeperBeamsLogic.RoomGate.cellOf(creeper.cx, creeper.cz))
		val seen = Placement.endsInRoom(raw, floorTop, open, creeper, view)
		assertTrue(creeper.base in raw, "creeper stands on a beam block")
		assertTrue(seen.size >= CreeperBeamsLogic.MIN_ROOM_BLOCKS, "visible ends ${seen.size}")
		return CreeperBeamsLogic.Tracker().update(creeper, seen, emptySet())
	}

	@Test fun syntheticRoomProducesEveryBeamPair() {
		for (name in listOf("Creeper Beams", null)) {
			val beams = run(Placement.Eye(-179.0, 70.62, -30.0), name)
			assertEquals(pairs.toSet(), beams.map { setOf(it.a, it.b) }.toSet(), "room name $name")
		}
	}

	@Test fun pairsSurviveFromEveryCornerOfTheRoom() {
		for ((x, z) in listOf(-196.0 to -36.0, -174.0 to -36.0, -196.0 to -14.0, -174.0 to -14.0))
			assertEquals(4, run(Placement.Eye(x, 70.62, z), "Creeper Beams").size, "player at $x,$z")
	}

	@Test fun realHoveringCreeperFromTheLogIsTaken() {
		val lantern = p(-153, 74, -89)
		val solid: (Int, Int, Int) -> Boolean = { x, y, z -> y <= 68 || (x == -153 && z == -89 && y in 69..74) }
		val room = Placement.Cubes(solid) { x, y, z -> if (p(x, y, z) == lantern) "sea_lantern" else if (solid(x, y, z)) "stone" else "air" }
		val real = Hitbox(-152.8, 75.75, -88.8, -152.2, 77.45, -88.2)
		val eye = Placement.Eye(-141.1, 70.6, -88.0)
		assertEquals(0.0, Placement.centreDistance(real), 1e-9)
		assertEquals(69, Placement.roomFloor(CreeperBeamsLogic.RoomGate.cellOf(eye.x, eye.z), 70, room))
		assertEquals(lantern, Placement.support(real, room))
		assertEquals(Placement.Verdict.OK, Placement.judge(real, eye, "Creeper Beams", 69, room).verdict)
		assertEquals(1, Placement.select(listOf(1 to real), eye, "Creeper Beams", 69, room).chosen)

		val under = Hitbox(-152.8, 60.0, -88.8, -152.2, 61.7, -88.2)
		assertEquals(Placement.Verdict.BELOW_FLOOR, Placement.judge(under, eye, "Creeper Beams", 69, room).verdict)
		val high = Hitbox(-152.8, 77.0, -88.8, -152.2, 78.7, -88.2)
		assertEquals(Placement.Verdict.NOT_STANDING, Placement.judge(high, eye, "Creeper Beams", 69, room).verdict)
	}
}
