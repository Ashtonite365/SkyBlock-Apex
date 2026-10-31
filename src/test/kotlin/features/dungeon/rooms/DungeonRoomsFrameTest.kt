// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon.rooms

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.SecretRooms
import dev.ashtonite365.apex.features.dungeon.SecretRooms.Rotation
import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms

class DungeonRoomsFrameTest {
	@Test
	fun frameRoundTripsInEveryRotation() {
		for (rot in listOf(0, 90, 180, 270)) {
			val f = DungeonRooms.RoomFrame(BlockPos(-104, 0, -74), rot)
			for (p in listOf(BlockPos(-90, 70, -60), BlockPos(-104, 12, -74), BlockPos(-80, 99, -100)))
				assertEquals(p, f.toReal(f.toLocal(p)), "$rot $p")
		}
		assertEquals(BlockPos(3, 70, -5), DungeonRooms.turn(BlockPos(5, 70, 3), 90))
		assertEquals(BlockPos(-5, 70, -3), DungeonRooms.turn(BlockPos(5, 70, 3), 180))
	}

	@Test
	fun roomCornerIsTheCellStart() {
		assertEquals(BlockPos(-200, 0, -200), DungeonRooms.roomCorner(BlockPos(-200, 70, -170)))
		assertEquals(BlockPos(-104, 0, -72), DungeonRooms.roomCorner(BlockPos(-80, 70, -45)))
		assertEquals(BlockPos(-8, 0, -8), DungeonRooms.roomCorner(BlockPos(-1, 70, 0)))
	}

	@Test
	fun frameOfMatchesTheTracker() {
		val live = listOf(
			Triple("Duncan", Rotation.WEST, -74 to -40), Triple("Andesite", Rotation.EAST, -104 to -74),
			Triple("Cell", Rotation.SOUTH, -136 to -40), Triple("Water Board", Rotation.EAST, -40 to -42),
		)
		for ((name, rot, clay) in live) {
			val d = SecretRooms.Detected(SecretRooms.byName.getValue(name), rot, clay.first, clay.second, listOf(3 to 3), 0, "")
			val f = DungeonRooms.frameOf(d)
			assertEquals(BlockPos(clay.first, 0, clay.second), f.corner)
			for ((x, z) in listOf(5 to 9, 1 to 28, 30 to 0)) {
				val r = d.toReal(x, 70, z)
				assertEquals(BlockPos(r.first, 70, r.third), f.toReal(BlockPos(x, 70, z)), "$name $x $z")
			}
		}
	}
}
