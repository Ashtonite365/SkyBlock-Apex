/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: neutral room-frame helpers shared by the puzzle solvers and the Secrets features (feat-secrets-1025).
 * A room frame is the room's blue-terracotta corner plus a quarter-turn rotation; it converts between real block
 * positions and room-local ones. The current room comes from DungeonRoomTracker (core detection) first, then the
 * roof-corner scan (RoomCornerFinder) for rooms it does not know.
 */
package dev.ashtonite365.apex.features.dungeon.rooms

import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder
import dev.ashtonite365.apex.features.dungeon.RoomCornerLog
import dev.ashtonite365.apex.features.dungeon.SecretRooms
import dev.ashtonite365.apex.util.Client

object DungeonRooms {

	const val GRID_ORIGIN = -200
	const val CELL = 32

	fun roomCorner(pos: BlockPos): BlockPos {
		fun start(v: Int) = GRID_ORIGIN + Math.floorDiv(v - GRID_ORIGIN, CELL) * CELL
		return BlockPos(start(pos.x), 0, start(pos.z))
	}

	data class RoomFrame(val corner: BlockPos, val rotation: Int) {
		fun toLocal(pos: BlockPos): BlockPos = turn(pos.subtract(corner), rotation)
		fun toReal(local: BlockPos): BlockPos = turn(local, 360 - rotation).offset(corner)
	}

	internal fun turn(p: BlockPos, degrees: Int): BlockPos = when (Math.floorMod(degrees, 360)) {
		90 -> BlockPos(p.z, p.y, -p.x)
		180 -> BlockPos(-p.x, p.y, -p.z)
		270 -> BlockPos(-p.z, p.y, p.x)
		else -> p
	}

	fun frameOf(room: SecretRooms.Detected) = RoomFrame(BlockPos(room.clayX, 0, room.clayZ), when (room.rotation) {
		SecretRooms.Rotation.SOUTH -> 0
		SecretRooms.Rotation.WEST -> 90
		SecretRooms.Rotation.NORTH -> 180
		SecretRooms.Rotation.EAST -> 270
	})

	sealed interface FrameResult {
		data class Ok(val frame: RoomFrame) : FrameResult
		data class Missing(val cellX: Int, val cellZ: Int, val reason: String) : FrameResult
	}

	private const val CACHE_NANOS = 250_000_000L
	private var cache: Triple<Pair<Int, Int>, Long, FrameResult>? = null

	fun currentRoomFrame(): RoomFrame? = (currentRoomFrameResult() as? FrameResult.Ok)?.frame

	fun currentRoomFrameResult(): FrameResult {
		val level = Client.level ?: return FrameResult.Missing(0, 0, "no world")
		val pos = Client.player?.blockPosition() ?: return FrameResult.Missing(0, 0, "no player")
		val cell = RoomCornerFinder.cell(pos.x) to RoomCornerFinder.cell(pos.z)
		val now = System.nanoTime()
		cache?.let { (c, t, r) -> if (c == cell && now - t < CACHE_NANOS) return r }
		val result = DungeonRoomTracker.currentRoom()?.let { FrameResult.Ok(frameOf(it)) } ?: run {
			val kind = DungeonRoomTracker.blockKind(level)
			val roof = { x: Int, z: Int -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 }
			when (val r = RoomCornerFinder.find(pos.x, pos.z, kind, roof)) {
				is RoomCornerFinder.Result.Ok -> FrameResult.Ok(RoomFrame(BlockPos(r.found.x, 0, r.found.z), r.found.rotation))
				is RoomCornerFinder.Result.Missing -> FrameResult.Missing(cell.first, cell.second, r.reason)
			}
		}
		cache = Triple(cell, now, result)
		return result
	}

	val cornerLog = RoomCornerLog { dev.ashtonite365.apex.Apex.logger.warn(it) }

	fun roomFrameFor(solver: String): RoomFrame? = when (val r = currentRoomFrameResult()) {
		is FrameResult.Ok -> r.frame
		is FrameResult.Missing -> { cornerLog.missing(solver, r.cellX, r.cellZ, r.reason); null }
	}

	@Subscribe
	fun onWorldReady(event: WorldReadyEvent) {
		cache = null
		cornerLog.clear()
	}
}
