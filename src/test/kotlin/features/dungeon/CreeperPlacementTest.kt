package dev.ashtonite365.apex.test.features.dungeon

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Hitbox
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Placement
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Placement.Cubes
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Placement.Eye
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Placement.Verdict

class CreeperPlacementTest {
	private fun at(cx: Double, feet: Double, cz: Double) = Hitbox(cx - 0.3, feet, cz - 0.3, cx + 0.3, feet + 1.7, cz + 0.3)
	private fun p(x: Int, y: Int, z: Int) = BlockPos(x, y, z)
	private val cell = 0 to 5
	private val real = at(-184.5, 76.0, -24.5)
	private val lantern = p(-185, 75, -25)

	private val eye = Eye(-179.0, 70.62, -30.0)
	private val floorTop = 69
	private fun isLantern(x: Int, y: Int, z: Int) = x == lantern.x && y == lantern.y && z == lantern.z

	private val roomSolid: (Int, Int, Int) -> Boolean = { x, y, z -> y <= 68 || isLantern(x, y, z) }
	private fun room(extra: (Int, Int, Int) -> Boolean = { _, _, _ -> false }, minus: (Int, Int, Int) -> Boolean = { _, _, _ -> false }) =
		Cubes({ x, y, z -> !minus(x, y, z) && (roomSolid(x, y, z) || extra(x, y, z)) },
			{ x, y, z -> if (isLantern(x, y, z)) "sea_lantern" else if (!minus(x, y, z) && (roomSolid(x, y, z) || extra(x, y, z))) "stone" else "air" })
	private fun verdict(box: Hitbox, view: Placement.BlockView, floor: Int? = floorTop, from: Eye = eye, name: String? = "Creeper Beams") =
		Placement.judge(box, from, name, floor, view).verdict

	@Test fun realCreeperIsKeptAndChosen() {
		assertEquals(cell, CreeperBeamsLogic.RoomGate.cellOf(real.cx, real.cz))
		assertEquals(-184.5 to -24.5, Placement.centreOf(cell))
		assertEquals(0.0, Placement.centreDistance(real), 1e-9)
		assertEquals(Verdict.OK, verdict(real, room()))
		assertEquals(Verdict.OK, verdict(real, room(), name = null))
		assertNull(Placement.buried(real, room()))
		assertTrue(Placement.standing(real, room()))

		assertEquals(69, Placement.roomFloor(cell, 70, room()))
		assertEquals(7, Placement.select(listOf(7 to real), eye, null, 69, room()).chosen)
	}

	@Test fun belowFloorIsNeverTakenWhateverElse() {

		val allAir = Cubes({ x, y, z -> x == -185 && z == -25 && y == 60 })
		for (feet in listOf(68.999, 68.5, 68.0, 66.0, 61.0, 40.0)) {
			val b = at(-184.5, feet, -24.5)
			assertEquals(Verdict.BELOW_FLOOR, verdict(b, allAir), "feet $feet")
			assertEquals(Verdict.BELOW_FLOOR, verdict(b, allAir, name = null), "feet $feet")
			assertNull(Placement.select(listOf(1 to b), eye, "Creeper Beams", floorTop, allAir).chosen, "feet $feet")
		}

		val lone = at(-184.5, 61.0, -24.5)
		val ok = Cubes({ x, y, z -> x == -185 && z == -25 && y == 60 })
		assertEquals(Verdict.OK, verdict(lone, ok, floor = 61))
		assertEquals(Verdict.BELOW_FLOOR, verdict(lone, ok, floor = 62))

		assertEquals(Verdict.FLOOR_UNKNOWN, verdict(real, room(), floor = null))
		assertTrue(Placement.belowFloor(68.9, 69)); assertFalse(Placement.belowFloor(69.0, 69))
	}

	@Test fun roomFloorComesFromTheRoomNotFromWhereYouStood() {

		val hole: (Int, Int, Int) -> Boolean = { x, y, z -> x in -190..-188 && z in -30..-28 && y in 66..68 }
		val view = room(minus = hole)
		val tracker = Placement.FloorTracker()
		tracker.update(cell, true, 69.0)
		val stood = tracker.update(cell, true, 66.0)
		assertEquals(66, stood)
		val roomFloor = Placement.roomFloor(cell, 70, view)
		assertEquals(69, roomFloor)
		assertEquals(69, Placement.floorFor(roomFloor, stood))
		val inHole = at(-188.5, 66.0, -28.5)
		assertEquals(Verdict.BELOW_FLOOR, verdict(inHole, view, Placement.floorFor(roomFloor, stood)))

		val pockets = room(minus = { _, y, _ -> y in 60..64 })
		assertEquals(69, Placement.roomFloor(cell, 70, pockets))

		assertEquals(69, Placement.roomFloor(cell, 78, room()))

		assertNull(Placement.roomFloor(cell, 70, Cubes({ _, _, _ -> true })))
		assertEquals(69, Placement.floorFor(null, 69))
		assertNull(Placement.floorFor(null, null))
	}

	@Test fun a_bodyOrEyesInsideABlock() {
		val c = at(-184.5, 69.0, -22.5)
		assertEquals(Verdict.OK, verdict(c, room(), from = Eye(-184.5, 70.62, -18.0)))
		for (y in listOf(69, 70)) {
			val v = room(extra = { x, yy, z -> x == -185 && yy == y && z == -23 })
			assertEquals(Verdict.BURIED, verdict(c, v), "block at y $y")
			assertEquals(p(-185, y, -23), Placement.buried(c, v))
		}

		assertEquals(Verdict.BURIED, verdict(at(-184.5, 69.0, -22.5), room(extra = { _, y, _ -> y == 69 })))
	}

	@Test fun b_belowFloorNextToLighting() {
		val under = at(-184.5, 64.0, -24.5)
		val lanternBelow = room(minus = { _, y, _ -> y in 64..66 }, extra = { x, y, z -> x == -185 && y == 63 && z == -25 })
		val lanternAbove = room(minus = { _, y, _ -> y in 64..66 }, extra = { x, y, z -> x == -185 && y == 67 && z == -25 })
		for (v in listOf(lanternBelow, lanternAbove)) {
			assertEquals(Verdict.BELOW_FLOOR, verdict(under, v))
			assertEquals(Verdict.BELOW_FLOOR, verdict(under, v, from = Eye(-184.5, 65.6, -21.0)))
			assertNull(Placement.select(listOf(1 to under), eye, null, floorTop, v).chosen)
		}
	}

	@Test fun c_pitAndLowCeiling() {
		val pit = room(minus = { x, y, z -> x == -185 && y == 68 && z == -24 })
		assertEquals(Verdict.BELOW_FLOOR, verdict(at(-184.5, 68.0, -23.5), pit))

		val low = room(extra = { x, y, z -> x == -185 && y == 70 && z == -23 })
		assertEquals(Verdict.BURIED, verdict(at(-184.5, 69.0, -22.5), low))

		val gap2 = room(extra = { x, y, z -> x == -185 && y == 71 && z == -23 })
		assertNull(Placement.buried(at(-184.5, 69.0, -22.5), gap2))
	}

	@Test fun d_anyOverlapIsBuried() {
		val wall = room(extra = { x, _, _ -> x == -186 })

		val into = Hitbox(-185.04, 69.0, -24.8, -184.44, 70.7, -24.2)
		assertEquals(p(-186, 69, -25), Placement.buried(into, wall))
		assertEquals(Verdict.BURIED, verdict(into, wall))

		assertNull(Placement.buried(Hitbox(-185.0, 69.0, -24.8, -184.4, 70.7, -24.2), wall))

		assertNotEquals(null, Placement.buried(at(-180.5, 68.999, -20.5), room()))
	}

	@Test fun e_lineOfSight() {

		val wall = room(extra = { x, y, _ -> x == -182 && y in 69..85 })
		assertEquals(Verdict.HIDDEN, verdict(real, wall, from = Eye(-179.0, 77.6, -24.5)))
		assertEquals(Verdict.OK, verdict(real, room(), from = Eye(-179.0, 77.6, -24.5)))

		val under = room(minus = { x, y, z -> x == -185 && y in 66..67 && z == -25 })
		assertFalse(Placement.lineClear(eye.x, eye.y, eye.z, -184.5, 67.4, -24.5) { x, y, z -> roomSolid(x, y, z) })
		assertNotEquals(Verdict.OK, verdict(at(-184.5, 66.0, -24.5), under))

		assertFalse(Placement.lineClear(0.5, 0.5, 0.5, 5.5, 0.5, 0.5) { x, _, _ -> x == 3 })
		assertTrue(Placement.lineClear(0.5, 0.5, 0.5, 5.5, 0.5, 0.5) { x, _, _ -> x == 7 })
		assertTrue(Placement.lineClear(0.5, 0.5, 0.5, 0.5, 0.5, 0.5) { _, _, _ -> false })
	}

	@Test fun offCentreAndFloatingAreNotThePuzzleCreeper() {
		val corner = at(-195.5, 69.0, -35.5)
		assertEquals(Verdict.OFF_CENTRE, verdict(corner, room()))
		val floating = at(-184.5, 80.0, -24.5)
		assertEquals(Verdict.NOT_STANDING, verdict(floating, room(), from = Eye(-179.0, 81.6, -24.5)))
		assertEquals(Verdict.OTHER_ROOM, verdict(real, room(), name = "Boulder"))
		assertEquals(Verdict.OTHER_CELL, verdict(real, room(), from = Eye(-150.0, 70.6, -24.5)))
	}

	@Test fun nearestTheCentreWinsAndEveryCandidateIsReported() {
		val buried = at(-184.5, 66.0, -24.5)
		val side = at(-182.5, 69.0, -24.5)
		val sel = Placement.select(listOf(3 to side, 2 to buried, 1 to real), eye, null, floorTop, room())
		assertEquals(1, sel.chosen)
		val byId = sel.reports.associateBy { it.id }
		assertEquals(Verdict.BELOW_FLOOR, byId[2]!!.verdict)
		assertEquals(Verdict.OK, byId[3]!!.verdict)

		val line = byId[2]!!.line()
		assertTrue(line.contains("pos=-184.50,66.00,-24.50") && line.contains("feet=stone@66") && line.contains("below=stone@65") &&
			line.contains("head=stone@67") && line.contains("BELOW_FLOOR"), line)
		val ok = byId[1]!!.line()
		assertTrue(ok.contains("below=sea_lantern@75") && ok.contains("feet=air@76") && ok.contains("-> OK"), ok)

		assertNull(Placement.select(listOf(2 to buried), eye, null, floorTop, room()).chosen)
	}

	@Test fun f_hiddenLightingIsNotABeamEnd() {

		val solid: (Int, Int, Int) -> Boolean = { x, y, z -> y <= 68 || isLantern(x, y, z) || y == 90 || y == 91 || y >= 93 || x <= -200 || x >= -170 }
		val view = Cubes(solid)
		val open: (Int, Int, Int) -> Boolean = { x, y, z -> !solid(x, y, z) }
		val wallEnd = p(-200, 78, -22)
		val floorEnd = p(-190, 68, -30)
		val ceilingEnd = p(-180, 90, -20)
		val aboveCeiling = p(-181, 91, -20)
		val underFloor = p(-190, 66, -30)
		val sealed = p(-180, 67, -20)
		val kept = Placement.endsInRoom(setOf(wallEnd, floorEnd, ceilingEnd, aboveCeiling, underFloor, sealed), floorTop, open, real, view)
		assertEquals(setOf(wallEnd, floorEnd, ceilingEnd), kept)
		assertTrue(Placement.exposed(aboveCeiling, open))
		assertFalse(Placement.seenFrom(aboveCeiling, real, open, view))
		assertFalse(Placement.exposed(sealed, open))

		assertTrue(Placement.endsInRoom(setOf(p(-190, 60, -30)), floorTop, { _, _, _ -> true }).isEmpty())
	}

	@Test fun beamEndsAroundThePillarStillCount() {

		val pillar: (Int, Int, Int) -> Boolean = { x, y, z -> y <= 68 || (x == -185 && z == -25 && y <= 75) }
		val view = Cubes(pillar)
		val open: (Int, Int, Int) -> Boolean = { x, y, z -> !pillar(x, y, z) }
		val near = p(-187, 68, -25)
		assertTrue(Placement.seenFrom(near, real, open, view))
		assertEquals(Verdict.OK, verdict(real, view, from = Eye(-179.0, 77.6, -24.5)))
	}
}
