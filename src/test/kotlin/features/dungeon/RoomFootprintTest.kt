package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import net.minecraft.world.level.material.MapColor

class RoomFootprintTest {
	private val size = DungeonMap.SIZE
	private fun all() = (0 until 6).flatMap { x -> (0 until 6).map { y -> x to y } }.toSet()

	private fun ring(shapes: Map<Pair<Int, Int>, List<Pair<Int, Int>>>) =
		DungeonMap.frontierFootprints(setOf(0 to 0), all() - (0 to 0), { _, _ -> true }) { c -> shapes[c] ?: listOf(c) }

	private fun shapeTest(shape: List<Pair<Int, Int>>) {
		val owner = shape.first()
		assertEquals(listOf(shape.toSet()) + listOf(setOf(0 to 1)).filter { (0 to 1) !in shape },
			ring(shape.associateWith { shape }).filter { owner in it || (0 to 1) in it })
	}

	@Test fun oneByTwo() = shapeTest(listOf(1 to 0, 2 to 0))
	@Test fun oneByThree() = shapeTest(listOf(1 to 0, 2 to 0, 3 to 0))
	@Test fun oneByFour() = shapeTest(listOf(1 to 0, 2 to 0, 3 to 0, 4 to 0))
	@Test fun twoByTwo() = shapeTest(listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1))
	@Test fun lShape() = shapeTest(listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2))

	@Test fun unknownShapeFallsBackToSingleCell() {
		assertEquals(setOf(setOf(1 to 0), setOf(0 to 1)), ring(emptyMap()).toSet())

		assertEquals(setOf(setOf(1 to 0), setOf(0 to 1)), DungeonMap.frontierFootprints(setOf(0 to 0), all() - (0 to 0), { _, _ -> true }) { listOf(5 to 5) }.toSet())
	}

	@Test fun twoStepsAwayGetsNothingAdjacentGetsFullShape() {
		val far = listOf(3 to 3, 4 to 3)
		val near = listOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)
		val out = ring(mapOf(3 to 3 to far, 4 to 3 to far) + near.associateWith { near })
		assertTrue(out.none { it.any { c -> c in far } })
		assertTrue(near.toSet() in out)
	}

	@Test fun noConnectionNoFootprint() {
		assertEquals(emptyList<Set<Pair<Int, Int>>>(), DungeonMap.frontierFootprints(setOf(0 to 0), setOf(1 to 0), { _, _ -> false }) { listOf(it) })
	}

	@Test fun recomputedAsRoomsOpen() {
		val l = listOf(2 to 0, 3 to 0, 3 to 1)
		val shapes = l.associateWith { l }
		val before = DungeonMap.frontierFootprints(setOf(0 to 0), all() - (0 to 0), { _, _ -> true }) { shapes[it] ?: listOf(it) }
		assertTrue(before.none { (2 to 0) in it })
		val after = DungeonMap.frontierFootprints(setOf(0 to 0, 1 to 0), all() - setOf(0 to 0, 1 to 0), { _, _ -> true }) { shapes[it] ?: listOf(it) }
		assertTrue(l.toSet() in after)
	}

	@Test fun drawnGreyWithJoinsFromMapPixels() {

		val c = ByteArray(size * size)
		fun room(gx: Int, gy: Int, colour: Int) { for (y in 0 until 16) for (x in 0 until 16) c[gx + x + (gy + y) * size] = colour.toByte() }
		room(10, 10, 30); room(30, 10, 85); room(50, 10, 85)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 63.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		val fp = DungeonMap.footprintsOf(c, grid, emptySet()) { cell -> if (cell.first in 1..2 && cell.second == 0) listOf(1 to 0, 2 to 0) else listOf(cell) }
		assertEquals(listOf(setOf(1 to 0, 2 to 0)), fp)
		val px = DungeonMap.layoutPixels(c, grid, footprints = fp)

		assertEquals(DungeonMap.FOOTPRINT_ARGB, px[grid.x + grid.step + grid.room + 1 + (grid.y + 2) * size])
	}

	@Test fun blankCellsNeverStartAFootprint() {

		val out = DungeonMap.frontierFootprints(setOf(0 to 0), setOf(1 to 0, 0 to 1), { _, _ -> true }, seeds = setOf(0 to 1)) { listOf(it) }
		assertEquals(listOf(setOf(0 to 1)), out)
	}

	@Test fun greySeedShapeMayCoverBlankCellsOfTheSameRoom() {

		val shape = listOf(1 to 0, 2 to 0, 3 to 0)
		val out = DungeonMap.frontierFootprints(setOf(0 to 0), shape.toSet(), { _, _ -> true }, seeds = setOf(1 to 0)) { shape }
		assertEquals(listOf(shape.toSet()), out)
	}

	@Test fun mapPixelsBlankNeighbourGetsNothing() {

		val c = ByteArray(size * size)
		fun room(gx: Int, gy: Int, colour: Int) { for (y in 0 until 16) for (x in 0 until 16) c[gx + x + (gy + y) * size] = colour.toByte() }
		room(10, 10, 30); room(50, 10, 85)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 63.toByte()
		val grid = DungeonMap.detectGrid(c) ?: return
		val fp = DungeonMap.footprintsOf(c, grid, emptySet()) { listOf(it) }
		assertTrue(fp.none { (1 to 0) in it })
	}

	@Test fun footprintNeverDrawsAScannedWitherDoor() {

		val c = ByteArray(size * size)
		fun room(gx: Int, gy: Int, colour: Int) { for (y in 0 until 16) for (x in 0 until 16) c[gx + x + (gy + y) * size] = colour.toByte() }
		room(10, 10, 30); room(30, 10, 85); room(50, 10, 85)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 63.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		val doors = mapOf(DungeonMap.DoorKey(1, 0, false) to dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.WITHER)
		val slot = grid.x + grid.step + grid.room / 2 + (grid.y + grid.room + 1) * size

		assertEquals(0, DungeonMap.layoutPixels(c, grid, blockDoors = doors)[slot])
		val fp = listOf(setOf(1 to 0, 2 to 0))
		assertEquals(0, DungeonMap.layoutPixels(c, grid, blockDoors = doors, footprints = fp)[slot])
	}

	private fun eastSlotRow(): Triple<ByteArray, DungeonMap.Grid, Int> {

		val c = ByteArray(size * size)
		fun room(gx: Int, gy: Int, colour: Int) { for (y in 0 until 16) for (x in 0 until 16) c[gx + x + (gy + y) * size] = colour.toByte() }
		room(10, 10, 30); room(30, 10, 85); room(50, 10, 85); room(70, 10, 30)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 63.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		return Triple(c, grid, grid.x + 2 * grid.step + grid.room + 1 + (grid.y + grid.room / 2) * size)
	}

	@Test fun witherDoorToAnExploredRoomIsShown() {
		val (c, grid, slot) = eastSlotRow()
		val doors = mapOf(DungeonMap.DoorKey(2, 0, true) to dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.WITHER)
		assertTrue(DungeonMap.layoutPixels(c, grid, blockDoors = doors, footprints = listOf(setOf(1 to 0, 2 to 0)))[slot] != 0)
	}

	@Test fun bloodDoorHiddenWhileBloodIsNotRevealed() {
		val (c, grid, slot) = eastSlotRow()
		c[slot] = dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.BLOOD.toByte()
		assertEquals(0, DungeonMap.layoutPixels(c, grid, footprints = listOf(setOf(1 to 0, 2 to 0)))[slot])
	}

	@Test fun bloodDoorShownOnceHypixelShowsTheBloodRoom() {
		val (c, grid, slot) = eastSlotRow()
		for (y in 0 until 16) for (x in 0 until 16) c[70 + x + (10 + y) * size] = 18.toByte()
		c[slot] = 18.toByte()
		val px = DungeonMap.layoutPixels(c, grid, footprints = listOf(setOf(1 to 0, 2 to 0)))
		assertEquals(MapColor.getColorFromPackedId(18), px[slot])
	}

	@Test fun bloodDoorShownOnceScanned() {
		val (c, grid, slot) = eastSlotRow()
		val doors = mapOf(DungeonMap.DoorKey(2, 0, true) to dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.BLOOD)
		val px = DungeonMap.layoutPixels(c, grid, blockDoors = doors, footprints = listOf(setOf(1 to 0, 2 to 0)))
		assertEquals(MapColor.getColorFromPackedId(18), px[slot])
	}

	@Test fun noDoorsOnBlankCellsTowardsUnexploredSide() {

		val c = ByteArray(size * size)
		for (y in 0 until 16) for (x in 0 until 16) { c[10 + x + (10 + y) * size] = 30.toByte(); c[30 + x + (10 + y) * size] = 30.toByte() }
		for (y in 10 until 26) for (x in 26..29) c[x + y * size] = 30.toByte()
		val grid = DungeonMap.detectGrid(c) ?: return
		val doors = mapOf(DungeonMap.DoorKey(0, 1, false) to dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.WITHER)
		val slot = grid.x + grid.room / 2 + (grid.y + grid.step + grid.room + 1) * size
		assertEquals(0, DungeonMap.layoutPixels(c, grid, blockDoors = doors)[slot])
	}

	@Test fun footprintIsHypixelsUnenteredGrey() {
		assertEquals(0xFF414141.toInt(), DungeonMap.FOOTPRINT_ARGB)
		assertEquals(MapColor.COLOR_GRAY.calculateARGBColor(MapColor.Brightness.NORMAL), DungeonMap.FOOTPRINT_ARGB)
	}

	private fun map0140(): Triple<ByteArray, DungeonMap.Grid, (Int, Int, Boolean) -> Int> {
		val c = ByteArray(size * size)
		fun room(col: Int, row: Int, colour: Int) { for (y in 0 until 16) for (x in 0 until 16) c[10 + col * 20 + x + (10 + row * 20 + y) * size] = colour.toByte() }
		fun join(col: Int, row: Int, east: Boolean, colour: Int, full: Boolean = false) {
			val gx = 10 + col * 20; val gy = 10 + row * 20
			if (east) for (y in (if (full) 0 until 16 else 6 until 10)) for (x in 16 until 20) c[gx + x + (gy + y) * size] = colour.toByte()
			else for (y in 16 until 20) for (x in (if (full) 0 until 16 else 6 until 10)) c[gx + x + (gy + y) * size] = colour.toByte()
		}
		room(3, 0, 30); for (r in 1..3) room(3, r, 63); join(3, 1, false, 63, true); join(3, 2, false, 63, true)
		room(1, 1, 63); room(2, 1, 63); join(1, 1, true, 63, true)
		room(0, 1, 85); room(1, 0, 85); room(2, 0, 85); room(2, 2, 85); room(2, 3, 85); join(2, 2, false, 85, true)
		join(3, 0, false, 63); join(2, 1, true, 63); join(1, 0, false, 85); join(2, 0, false, 85); join(2, 1, false, 85)
		join(0, 1, true, 119)
		val grid = DungeonMap.detectGrid(c)!!
		return Triple(c, grid) { col, row, east ->
			if (east) grid.x + col * grid.step + grid.room + 1 + (grid.y + row * grid.step + grid.room / 2) * size
			else grid.x + col * grid.step + grid.room / 2 + (grid.y + row * grid.step + grid.room + 1) * size
		}
	}
	private fun mc(id: Int) = MapColor.getColorFromPackedId(id)

	@Test fun map0140ScannedOpenDoorwayIsNormalWhateverHypixelPaints() {
		val (c, grid, slot) = map0140()
		val px = DungeonMap.layoutPixels(c, grid, noCoalDoors = setOf(DungeonMap.DoorKey(0, 1, true)))
		assertEquals(mc(63), px[slot(0, 1, true)])
	}

	@Test fun map0140FairyEntranceIsNormalWithoutAScan() {
		val (c, grid, slot) = map0140()
		assertEquals(mc(63), DungeonMap.layoutPixels(c, grid, fairyCells = setOf(0 to 1))[slot(0, 1, true)])
	}

	@Test fun map0140UnscannedNonFairyDoorKeepsHypixelsPixel() {
		val (c, grid, slot) = map0140()
		assertEquals(mc(119), DungeonMap.layoutPixels(c, grid)[slot(0, 1, true)])
	}

	@Test fun fairyRoomRealWitherDoorOnTheSideStaysBlackEntranceStaysNormal() {

		val (c, grid, slot) = map0140()
		for (y in 0 until 16) for (x in 0 until 16) c[10 + x + (10 + 2 * 20 + y) * size] = 63.toByte()
		val coal = mapOf(DungeonMap.DoorKey(0, 1, false) to dev.ashtonite365.apex.features.dungeon.secrets.HypixelMapColours.WITHER)
		val px = DungeonMap.layoutPixels(c, grid, blockDoors = coal, fairyCells = setOf(0 to 1), noCoalDoors = setOf(DungeonMap.DoorKey(0, 1, true)))
		assertEquals(mc(119), px[slot(0, 1, false)])
		assertEquals(mc(63), px[slot(0, 1, true)])

		val px2 = DungeonMap.layoutPixels(c, grid, blockDoors = coal, fairyCells = setOf(0 to 1))
		assertEquals(mc(119), px2[slot(0, 1, false)])
		assertEquals(mc(63), px2[slot(0, 1, true)])
	}
}
