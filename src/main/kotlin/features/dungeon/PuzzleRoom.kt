/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H3): room-local coordinates for dungeon puzzle solvers. Local coordinates are x/z
 * relative to the room's blue-terracotta corner, with absolute y. Instead of trusting one rotation
 * convention, solvers try all four quarter turns and keep the one whose expected blocks are really there.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos

object PuzzleRoom {

	fun toReal(corner: BlockPos, local: BlockPos, rotation: Int): BlockPos {
		val (x, z) = when (Math.floorMod(rotation, 4)) {
			0 -> local.x to local.z
			1 -> -local.z to local.x
			2 -> -local.x to -local.z
			else -> local.z to -local.x
		}
		return BlockPos(corner.x + x, local.y, corner.z + z)
	}

	fun bestRotation(score: (Int) -> Int): Int? =
		(0..3).map { it to score(it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first

	fun corner(): BlockPos? = dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms.currentRoomFrame()?.corner

	fun cornerFor(solver: String): BlockPos? = dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms.roomFrameFor(solver)?.corner
}
