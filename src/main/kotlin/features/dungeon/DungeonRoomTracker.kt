/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the dungeon room the player stands in (rebuild-secret-finder-1020, 20 Oct 2026). Runs the one room
 * detection (SecretRooms.detect, the core hash + clay corner) on the live world over the room's cells from
 * RoomCornerFinder. A found room is kept per cell set for the rest of the run; an unknown or unplaced one is retried
 * every [RETRY_MS] (chunks still loading). Shared by the puzzle room frames, Breakable Walls and the Secrets
 * features. Cleared on world change. Logs "[ApexRoom]" once per room entry / outcome.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object DungeonRoomTracker {
	const val RETRY_MS = 1000L
	const val CELLS_MS = 500L

	private val known = HashMap<Set<Pair<Int, Int>>, SecretRooms.Detected>()
	private val lastTry = HashMap<Set<Pair<Int, Int>>, Long>()
	private var cellsCache: Triple<Pair<Int, Int>, Long, List<Pair<Int, Int>>>? = null

	val detected: Collection<SecretRooms.Detected> get() = known.values

	fun blockKind(level: Level): (Int, Int, Int) -> RoomCornerFinder.Kind {
		val m = BlockPos.MutableBlockPos()
		return { x, y, z ->
			val st = level.getBlockState(m.set(x, y, z))
			when {
				st.isAir -> RoomCornerFinder.Kind.AIR
				st.`is`(Blocks.GOLD_BLOCK) -> RoomCornerFinder.Kind.GOLD
				st.`is`(Blocks.DYED_TERRACOTTA.blue) -> RoomCornerFinder.Kind.BLUE_TERRACOTTA
				else -> RoomCornerFinder.Kind.OTHER
			}
		}
	}

	fun currentCells(): List<Pair<Int, Int>>? {
		if (!DungeonUtil.isInDungeonIsland) return null
		val level = Client.level ?: return null
		val pos = Client.player?.blockPosition() ?: return null
		val ix = RoomCornerFinder.cell(pos.x); val iz = RoomCornerFinder.cell(pos.z)
		if (ix !in 0 until RoomCornerFinder.GRID || iz !in 0 until RoomCornerFinder.GRID) return null
		val now = System.currentTimeMillis()
		val cell = ix to iz
		return cellsCache?.takeIf { it.first == cell && now - it.second < CELLS_MS }?.third ?: run {
			val kind = blockKind(level)
			val roofCache = HashMap<Pair<Int, Int>, Int?>()
			RoomCornerFinder.roomCells(ix, iz, { x, z -> roofCache.getOrPut(x to z) { RoomCornerFinder.scanRoof(x, z, kind) } }, kind)
				.also { cellsCache = Triple(cell, now, it) }
		}
	}

	fun coreOf(level: Level, cell: Pair<Int, Int>): Pair<Int, Int>? {
		val (x, z) = SecretRooms.cellOrigin(cell)
		val m = BlockPos.MutableBlockPos(x, 70, z)
		if (!level.isLoaded(m)) return null
		return SecretRooms.core { y ->
			val st = level.getBlockState(m.set(x, y, z))
			SecretRooms.ColumnBlock(BuiltInRegistries.BLOCK.getKey(st.block).toString(), st.isAir)
		}
	}

	fun roomTypeAt(level: Level, cell: Pair<Int, Int>): String? =
		known.values.firstOrNull { cell in it.cells }?.info?.type ?: coreOf(level, cell)?.let { SecretRooms.info(it.first) }?.type

	fun roomInfoAt(level: Level, cell: Pair<Int, Int>): SecretRooms.Info? =
		known.values.firstOrNull { cell in it.cells }?.info ?: coreOf(level, cell)?.let { SecretRooms.info(it.first) }

	fun currentRoom(): SecretRooms.Detected? {
		val level = Client.level ?: return null
		val cells = currentCells() ?: return null
		val key = cells.toSet()
		known[key]?.let { return it }
		val now = System.currentTimeMillis()
		if (now - (lastTry[key] ?: 0L) < RETRY_MS) return null
		lastTry[key] = now
		val m = BlockPos.MutableBlockPos()
		val result = SecretRooms.detect(cells, { coreOf(level, it) }) { x, y, z -> level.getBlockState(m.set(x, y, z)).`is`(Blocks.DYED_TERRACOTTA.blue) }
		log(cells, result)
		return (result as? SecretRooms.Result.Found)?.room?.also { known[key] = it }
	}

	private val logged = HashSet<String>()

	fun logLine(cells: List<Pair<Int, Int>>, result: SecretRooms.Result): String {
		val c = cells.joinToString(" ") { "${it.first},${it.second}" }
		return when (result) {
			is SecretRooms.Result.Found -> result.room.let {
				"[ApexRoom] ${it.info.name} (${it.info.type} ${it.info.shape}) rotation=${it.rotation} clay=${it.clayX},${it.clayZ} core=${it.core}" +
					" secrets=${it.info.maxSecrets ?: "-"} cells=$c; matched: ${it.why}"
			}
			is SecretRooms.Result.Unknown -> "[ApexRoom] unknown room cells=$c cores=" +
				result.cores.joinToString(" ") { (cell, core) -> "$core@corner${SecretRooms.cellOrigin(cell).let { (x, z) -> "${x - 15},${z - 15}" }}" } +
				(if (result.waiting.isNotEmpty()) " waiting for chunks at ${result.waiting}" else "")
			is SecretRooms.Result.NotPlaced -> "[ApexRoom] ${result.info.name} not placed yet cells=$c: ${result.why}"
		}
	}

	private fun log(cells: List<Pair<Int, Int>>, result: SecretRooms.Result) {
		val line = logLine(cells, result)
		if (logged.size >= 200 || !logged.add(line)) return
		dev.ashtonite365.apex.Apex.logger.info(line)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		known.clear(); lastTry.clear(); cellsCache = null; logged.clear()
	}
}
