package dev.ashtonite365.apex.test.features.dungeon

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic
import dev.ashtonite365.apex.features.dungeon.CreeperBeamsLogic.Hitbox

class CreeperBeamsTest {
	private fun creeperAt(cx: Double, feet: Double, cz: Double) = Hitbox(cx - 0.3, feet, cz - 0.3, cx + 0.3, feet + 1.7, cz + 0.3)
	private val creeper = creeperAt(0.5, 76.0, 0.5)
	private fun p(x: Int, y: Int, z: Int) = BlockPos(x, y, z)

	private fun mirror(b: BlockPos) = BlockPos(-b.x, 153 - b.y, -b.z)
	private val ends = listOf(p(15, 78, 3), p(3, 80, 15), p(15, 74, -8), p(-9, 82, 15))
	private val pairs = ends.map { setOf(it, mirror(it)) }
	private val lanterns = pairs.flatten().toSet()
	private fun keys(got: List<CreeperBeamsLogic.Pairing>) = got.map { it.key }.toSet()

	@Test fun passDistanceGeometry() {
		assertEquals(0.0, CreeperBeamsLogic.passDistance(p(15, 78, 3), mirror(p(15, 78, 3)), 0.5, 77.0, 0.5), 1e-9)

		assertEquals(2.0, CreeperBeamsLogic.passDistance(p(-10, 76, 2), p(10, 76, 2), 0.5, 76.5, 0.5), 1e-9)
		assertTrue(CreeperBeamsLogic.oppositeSides(p(15, 78, 3), mirror(p(15, 78, 3)), 0.5, 77.0, 0.5))
		assertFalse(CreeperBeamsLogic.oppositeSides(p(15, 78, 3), p(15, 78, 9), 0.5, 77.0, 0.5))
		assertEquals(p(0, 75, 0), creeper.base)
	}

	@Test fun pairsThroughTheCentre() {
		assertEquals(pairs.toSet(), keys(CreeperBeamsLogic.pair(lanterns, creeper)))

		assertEquals(pairs.toSet(), keys(CreeperBeamsLogic.pair(lanterns.reversed(), creeper)))
	}

	@Test fun focalHeightIsCalibrated() {

		for (feet in listOf(76.0, 75.0, 75.5))
			assertEquals(pairs.toSet(), keys(CreeperBeamsLogic.pair(lanterns, creeperAt(0.5, feet, 0.5))), "feet $feet")
	}

	@Test fun anyRoomPositionAndRotationWithoutCorner() {
		fun turn(b: BlockPos) = BlockPos(b.z + 1000, b.y, -b.x - 501)
		val got = CreeperBeamsLogic.pair(lanterns.map(::turn), creeperAt(1000.5, 76.0, -500.5))
		assertEquals(pairs.map { pr -> pr.map(::turn).toSet() }.toSet(), keys(got))
	}

	@Test fun noVerticalBeamThroughTheCreeper() {
		val base = creeper.base
		val ceiling = p(base.x, base.y + 12, base.z)
		val withColumn = lanterns + base + ceiling
		val got = keys(CreeperBeamsLogic.pair(CreeperBeamsLogic.beamEnds(withColumn, creeper), creeper))
		assertEquals(pairs.toSet(), got)

		val raw = keys(CreeperBeamsLogic.pair(listOf(base, ceiling), creeper))
		assertTrue(raw.isEmpty(), "vertical pair $raw")
		assertTrue(CreeperBeamsLogic.isVertical(base, ceiling))
		assertFalse(CreeperBeamsLogic.isVertical(p(15, 78, 3), mirror(p(15, 78, 3))))

		val beams = CreeperBeamsLogic.Tracker().update(creeper, withColumn, emptySet())
		assertEquals(4, beams.size)
		assertTrue(beams.none { CreeperBeamsLogic.isVertical(it.a, it.b) || it.a == base || it.b == base })
	}

	@Test fun closestPassWins() {
		val a = p(15, 78, 3)
		val got = CreeperBeamsLogic.pair(listOf(a, mirror(a), p(-15, 75, -1)), creeper)
		assertEquals(setOf(setOf(a, mirror(a))), keys(got))
	}

	@Test fun strayAndOddLanternsStayUnpaired() {
		for (stray in listOf(p(15, 77, 14), p(-14, 77, 15), p(15, 85, -15))) {
			assertEquals(pairs.toSet(), keys(CreeperBeamsLogic.pair(lanterns + stray, creeper)), "stray $stray")
		}
		assertTrue(CreeperBeamsLogic.pair(listOf(p(15, 78, 3)), creeper).isEmpty())
		assertTrue(CreeperBeamsLogic.pair(emptyList(), creeper).isEmpty())

		assertTrue(CreeperBeamsLogic.pair(listOf(p(15, 78, 3), p(15, 76, -3)), creeper).isEmpty())
	}

	@Test fun manyEndsUseGreedyFallback() {
		val more = ends + listOf(p(-15, 72, -2), p(5, 83, -15), p(15, 80, 10), p(-6, 71, 15), p(10, 84, 15))
		val all = more.map { setOf(it, mirror(it)) }
		assertTrue(all.flatten().size > CreeperBeamsLogic.EXACT_LIMIT)
		assertEquals(all.toSet(), keys(CreeperBeamsLogic.pair(all.flatten(), creeper)))
	}

	@Test fun pairWithOnePrismarineEndFromTheStartIsShown() {

		val t = CreeperBeamsLogic.Tracker()
		val odd = ends[2]
		val got = t.update(creeper, lanterns - odd, setOf(odd))
		assertEquals(pairs.toSet(), got.map { it.key }.toSet())
	}

	@Test fun trackerRemovesCompletedPairsAndKeepsColours() {
		val t = CreeperBeamsLogic.Tracker()
		val first = t.update(creeper, lanterns, emptySet())
		assertEquals(4, first.size)
		assertEquals(listOf(0, 1, 2, 3), first.map { it.colour })
		val colourOf = first.associate { it.key to it.colour }

		val done = pairs[0]
		val second = t.update(creeper, lanterns - done, done)
		assertEquals(pairs.drop(1).toSet(), second.map { it.key }.toSet())
		second.forEach { assertEquals(colourOf[it.key], it.colour) }

		val half = ends[1]
		val third = t.update(creeper, lanterns - done - half, done + half)
		assertEquals(pairs.drop(1).toSet(), third.map { it.key }.toSet())
		third.forEach { assertEquals(colourOf[it.key], it.colour) }

		assertTrue(t.update(creeper, emptySet(), lanterns).isEmpty())
		assertTrue(t.beams.isEmpty())
	}

	@Test fun trackerFollowsBlocksAppearing() {
		val t = CreeperBeamsLogic.Tracker()

		assertTrue(t.update(creeper, ends.toSet(), emptySet()).isEmpty())
		assertEquals(pairs.toSet(), t.update(creeper, lanterns, emptySet()).map { it.key }.toSet())
	}

	@Test fun resetClearsEverything() {
		val t = CreeperBeamsLogic.Tracker()
		t.update(creeper, lanterns, emptySet())
		assertEquals(4, t.beams.size)
		t.reset()
		assertTrue(t.beams.isEmpty())

		val again = t.update(creeper, pairs[2], emptySet())
		assertEquals(listOf(0), again.map { it.colour })
	}

	@Test fun creeperFilter() {
		assertTrue(CreeperBeamsLogic.isPuzzleCreeper(hasCustomName = false, alive = true))
		assertFalse(CreeperBeamsLogic.isPuzzleCreeper(hasCustomName = true, alive = true))
		assertFalse(CreeperBeamsLogic.isPuzzleCreeper(hasCustomName = false, alive = false))
	}

	@Test fun oldToggleMigrates() {
		val on = CreeperBeamsLogic.foldOldToggle(JsonObject(mapOf("creeper-beams" to JsonPrimitive(true), "boulder" to JsonPrimitive(false))))
		assertEquals(JsonObject(mapOf("boulder" to JsonPrimitive(false), "creeper-beams-solver" to JsonPrimitive(true))), on)

		assertEquals(JsonObject(emptyMap()), CreeperBeamsLogic.foldOldToggle(JsonObject(mapOf("creeper-beams" to JsonPrimitive(false)))))

		val both = CreeperBeamsLogic.foldOldToggle(JsonObject(mapOf("creeper-beams" to JsonPrimitive(true), "creeper-beams-solver" to JsonPrimitive(false))))
		assertEquals(JsonObject(mapOf("creeper-beams-solver" to JsonPrimitive(false))), both)
		val untouched = JsonObject(mapOf("boulder" to JsonPrimitive(true)))
		assertEquals(untouched, CreeperBeamsLogic.foldOldToggle(untouched))
	}

	private val gate = CreeperBeamsLogic.RoomGate

	@Test fun roomCellsOnTheDungeonGrid() {
		assertEquals(0 to 0, gate.cellOf(-200.0, -200.0))
		assertEquals(0 to 0, gate.cellOf(-169.01, -185.0))
		assertEquals(1 to 0, gate.cellOf(-168.0, -185.0))
		assertEquals(-1 to 0, gate.cellOf(-200.5, -185.0))
		assertEquals(6 to 6, gate.cellOf(0.5, 0.5))
	}

	@Test fun footprintExcludesTheDoorGap() {
		val cell = 0 to 0
		assertTrue(gate.inFootprint(cell, -185.0, -185.0))
		assertTrue(gate.inFootprint(cell, -200.0, -200.0))
		assertTrue(gate.inFootprint(cell, -169.5, -169.5))
		assertFalse(gate.inFootprint(cell, -169.0, -185.0))
		assertFalse(gate.inFootprint(cell, -150.0, -185.0))
		assertFalse(gate.inFootprint(cell, -185.0, -200.5))
	}

	@Test fun clearsOnceThePlayerLeavesTheRoom() {
		val room = gate.cellOf(-185.0, -185.0)
		assertFalse(gate.shouldClear(room, -185.0, -185.0))
		assertFalse(gate.shouldClear(room, -170.0, -199.0))
		assertTrue(gate.shouldClear(room, -169.0, -185.0))
		assertTrue(gate.shouldClear(room, -160.0, -185.0))
		assertTrue(gate.shouldClear(room, -185.0, -150.0))
		assertFalse(gate.shouldClear(null, 1000.0, 1000.0))
	}

	@Test fun creeperOnlyFoundFromInsideItsRoom() {

		assertTrue(gate.sameRoom(-184.5, -184.5, -175.0, -190.0))

		assertFalse(gate.sameRoom(-184.5, -184.5, -168.5, -184.5))
		assertFalse(gate.sameRoom(-184.5, -184.5, -169.0, -184.5))
		assertFalse(gate.sameRoom(-184.5, -184.5, -184.5, -201.0))
	}

}
