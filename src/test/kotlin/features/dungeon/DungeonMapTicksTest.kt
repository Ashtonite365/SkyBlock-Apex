package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.dungeon.secrets.MapLayout

class DungeonMapTicksTest {
	private val size = DungeonMap.SIZE
	private fun room(c: ByteArray, gx: Int, gy: Int, len: Int, colour: Int, centre: Int? = null) {
		for (y in 0 until len) for (x in 0 until len) c[gx + x + (gy + y) * size] = colour.toByte()
		if (centre != null) for (y in -1..1) for (x in -1..1) c[gx + len / 2 + x + (gy + len / 2 + y) * size] = centre.toByte()
	}

	@Test
	fun whiteAndGreenTicksAreDetectedAndDrawn() {
		val c = ByteArray(size * size)
		room(c, 10, 10, 16, 30)
		room(c, 30, 10, 16, 18, centre = 34)
		room(c, 50, 10, 16, 18, centre = 30)
		room(c, 70, 10, 16, 18)
		val grid = DungeonMap.detectGrid(c)!!
		assertEquals(16, grid.room)
		val ticks = DungeonMap.roomTicks(c, grid)
		assertEquals(mapOf((1 to 0) to DungeonMap.Tick.WHITE, (2 to 0) to DungeonMap.Tick.GREEN), ticks.filterKeys { it.second == 0 })
		val px = DungeonMap.layoutPixels(c, grid)
		fun has(argb: Int, gx: Int) = (0 until 16).any { y -> (0 until 16).any { x -> px[gx + x + (10 + y) * size] == argb } }
		assert(has(DungeonMap.Tick.WHITE.argb, 30)) { "white tick drawn" }
		assert(has(DungeonMap.Tick.GREEN.argb, 50)) { "green tick drawn" }
		assert(!has(DungeonMap.Tick.GREEN.argb, 10) && !has(DungeonMap.Tick.WHITE.argb, 70))
	}

	@Test
	fun doorPixelReadsHypixelsDoorConnector() {

		val c = ByteArray(size * size)
		room(c, 10, 10, 16, 30)
		room(c, 30, 10, 16, 18)
		room(c, 10, 30, 16, 18)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 119.toByte()
		val f = MapLayout.MapFrame(10, 10, 20, 16)
		assertEquals(119, DungeonMap.doorPixelOf(c, f, 0 to 0, 1 to 0))
		assertEquals(null, DungeonMap.doorPixelOf(c, f, 0 to 0, 0 to 1))
		for (y in 26..29) for (x in 16..19) c[x + y * size] = 63.toByte()
		assertEquals(63, DungeonMap.doorPixelOf(c, f, 0 to 0, 0 to 1))
	}

	@Test
	fun preForkColoursUnenteredRoomsAndDoors() {

		val c = ByteArray(size * size)
		room(c, 10, 10, 16, 30)
		room(c, 30, 10, 16, 85, centre = 34)
		room(c, 50, 10, 16, 63)
		for (y in 16..19) for (x in 26..29) c[x + y * size] = 119.toByte()
		for (y in 16..19) for (x in 46..49) c[x + y * size] = 82.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		val px = DungeonMap.layoutPixels(c, grid)
		fun at(x: Int, y: Int) = px[x + y * size]
		fun mc(id: Int) = net.minecraft.world.level.material.MapColor.getColorFromPackedId(id)
		assertEquals(mc(85), at(30, 10))
		assertEquals(mc(85), at(31, 11))
		assert((0 until 16).none { y -> (0 until 16).any { x -> at(30 + x, 10 + y) == DungeonMap.Tick.WHITE.argb } })
		assertEquals(mc(30), at(12, 12))
		assertEquals(mc(119), at(27, 17))
		assertEquals(mc(82), at(47, 17))
		assertEquals(119, DungeonMap.doorPixelOf(c, MapLayout.MapFrame(10, 10, 20, 16), 0 to 0, 1 to 0))
	}

	@Test
	fun realWitherDoorBlocksOverrideFairyPinkDoor() {

		val c = ByteArray(size * size)
		room(c, 10, 10, 16, 30)
		room(c, 10, 30, 16, 82)
		room(c, 10, 50, 16, 63)
		for (y in 46..49) for (x in 16..19) c[x + y * size] = 82.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		val wither = net.minecraft.world.level.material.MapColor.getColorFromPackedId(119)
		val pink = net.minecraft.world.level.material.MapColor.getColorFromPackedId(82)
		val half = grid.room / 2
		fun doorAt(px: IntArray) = px[grid.x + half + (grid.y + grid.step + grid.room + 1) * size]
		assertEquals(pink, doorAt(DungeonMap.layoutPixels(c, grid)))
		val real = mapOf(DungeonMap.DoorKey(0, 1, false) to 119)
		assertEquals(wither, doorAt(DungeonMap.layoutPixels(c, grid, blockDoors = real)))

		val noFairy = c.copyOf().also { for (y in 30 until 50) for (x in 10 until 26) it[x + y * size] = 0 }
		val g2 = DungeonMap.detectGrid(noFairy) ?: return
		val out = DungeonMap.layoutPixels(noFairy, g2, blockDoors = mapOf(DungeonMap.DoorKey(0, 1, false) to 119))
		assertEquals(wither, out[g2.x + half + (g2.y + g2.step + g2.room + 1) * size])
		assertEquals(0, out[g2.x + g2.room + 1 + (g2.y + g2.step + half) * size])
	}

	@Test
	fun fairyRunF5OnlyReachableRealDoorsAreDrawn() {

		val c = ByteArray(size * size)
		fun cell(col: Int, row: Int, colour: Int) = room(c, 5 + col * 20, 5 + row * 20, 16, colour)
		cell(1, 5, 30); cell(0, 0, 82); cell(0, 1, 63); cell(1, 1, 63)
		val grid = DungeonMap.detectGrid(c)!!
		assertEquals(DungeonMap.Grid(5, 5, 16), grid)
		val slot = dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.slotNear(-169, -185)!!
		val key = DungeonMap.DoorKey(slot.a.i, slot.a.j, slot.east)
		assertEquals(DungeonMap.DoorKey(0, 0, true), key)
		val hidden = DungeonMap.DoorKey(3, 0, true)
		val real = mapOf(key to 119, hidden to 119)
		assertEquals(mapOf(key to 119), DungeonMap.shownBlockDoors(c, grid, real))
		val px = DungeonMap.layoutPixels(c, grid, blockDoors = real)
		fun mc(id: Int) = net.minecraft.world.level.material.MapColor.getColorFromPackedId(id)
		assertEquals(mc(119), px[5 + 16 + 1 + (5 + 8) * size])
		assertEquals(0, px[5 + 3 * 20 + 16 + 1 + (5 + 8) * size])
	}

	@Test
	fun fairyRoomEntranceIsANormalDoorUnlessRealBlocksAreCoal() {

		val c = ByteArray(size * size)
		fun cell(col: Int, row: Int, colour: Int) = room(c, 5 + col * 20, 5 + row * 20, 16, colour)
		cell(0, 0, 82); cell(0, 1, 63); cell(1, 5, 30)
		for (y in 21..24) for (x in 11..14) c[x + y * size] = 63.toByte()
		val grid = DungeonMap.detectGrid(c)!!
		val slot = 5 + 8 + (5 + 16 + 1) * size
		fun mc(id: Int) = net.minecraft.world.level.material.MapColor.getColorFromPackedId(id)

		assertEquals(mc(63), DungeonMap.layoutPixels(c, grid)[slot])
		assertEquals(mc(63), DungeonMap.layoutPixels(c, grid, blockDoors = mapOf(DungeonMap.DoorKey(3, 3, true) to 119))[slot])

		val ctx = dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.SlotContext(fairyBeyond = true, mapPixel = 63)
		assertEquals(false, dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.isWitherDoor(coal = true, open = true) { ctx })
		assertEquals(false, dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.isWitherDoor(coal = false, open = false) { ctx })

		assertEquals(true, dev.ashtonite365.apex.features.kit.dungeon.DoorLogic.isWitherDoor(coal = true, open = false) { ctx })
		assertEquals(mc(119), DungeonMap.layoutPixels(c, grid, blockDoors = mapOf(DungeonMap.DoorKey(0, 0, false) to 119))[slot])
	}
}
