/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Ice Path (silverfish) solver, new. Visual only: it draws the route line and the next hit
 * direction; it never attacks, clicks or sends packets. Slide/BFS logic lives in IcePathRoute.kt (pure, unit-tested).
 *
 * Room detection (layout learned IcePathSolver / IcePath, no code copied): the reward chest
 * stands on packed ice with a hopper two blocks above it and a stone-brick wall behind it; the 17x17 board is in front
 * of it at the chest's height, row 0 four blocks out. The facing whose board floor is mostly packed ice wins.
 * The silverfish's live position is snapped to the board grid; the route is recomputed whenever it comes to rest
 * on a new cell or the board changes.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.IcePathRoute.Cell
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object IcePathSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	private var layout: IcePathRoute.Layout? = null
	private var boardY = 0
	private val tracker = IcePathRoute.Tracker()
	private var silverfish: LivingEntity? = null
	private var lastCell: Cell? = null
	private var lastPos: Vec3? = null
	private var restingAt: Cell? = null
	private var exits: Set<Cell> = emptySet()

	private const val CHEST_Y_RANGE = 2
	private const val HYPIXEL_CHEST_Y = 67
	private const val SCAN_RADIUS = 25
	private const val MIN_FLOOR = 150
	private const val LEFT_ROOM = 40.0

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	private fun reset() { layout = null; tracker.reset(); silverfish = null; lastCell = null; lastPos = null; restingAt = null; exits = emptySet() }

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 2 != 0) return
		if (!cfg.icePathSolver || !DungeonUtil.isInDungeonIsland) { if (layout != null) reset(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val fish = level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(24.0)) {
			it.type == EntityTypes.SILVERFISH && it.isAlive && !it.isInvisible
		}.minByOrNull { it.distanceToSqr(player) }
		var lay = layout
		if (lay != null) {
			val (cx, cz) = lay.toWorld(Cell(IcePathRoute.CHEST_COL, 0))
			if (player.position().distanceTo(Vec3(cx + 0.5, boardY.toDouble(), cz + 0.5)) > LEFT_ROOM) { reset(); lay = null }
		}
		if (fish == null) { silverfish = null; return }
		if (lay == null) {
			if (event.tickCount % 20 != 0) return
			lay = findRoom(level, player.blockPosition()) ?: return
			layout = lay
		}
		silverfish = fish
		val cell = lay.toCell(fish.blockX, fish.blockZ) ?: return

		val pos = fish.position()
		val still = lastPos?.let { it.distanceToSqr(pos) < 0.0025 } == true
		val prev = lastCell
		lastCell = cell; lastPos = pos
		if (cell != prev || !still) return
		restingAt = cell
		val board = readBoard(level, lay)
		exits = IcePathRoute.exitCells(board)
		tracker.update(board, cell, exits)
	}

	private fun readBoard(level: Level, lay: IcePathRoute.Layout): IcePathRoute.Board {
		val p = BlockPos.MutableBlockPos()
		return IcePathRoute.Board.of(IcePathRoute.SIZE, IcePathRoute.SIZE) { c ->
			val (x, z) = lay.toWorld(c)
			p.set(x, boardY, z)
			!level.getBlockState(p).getCollisionShape(level, p).isEmpty
		}
	}

	private val facings = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

	private fun findRoom(level: Level, around: BlockPos): IcePathRoute.Layout? {
		val p = BlockPos.MutableBlockPos()
		var best: Pair<IcePathRoute.Layout, Int>? = null
		for (y in HYPIXEL_CHEST_Y - CHEST_Y_RANGE..HYPIXEL_CHEST_Y + CHEST_Y_RANGE)
			for (x in around.x - SCAN_RADIUS..around.x + SCAN_RADIUS) for (z in around.z - SCAN_RADIUS..around.z + SCAN_RADIUS) {
				val s = level.getBlockState(p.set(x, y, z))
				if (!s.`is`(Blocks.CHEST) && !s.`is`(Blocks.TRAPPED_CHEST)) continue
				if (!level.getBlockState(p.set(x, y - 1, z)).`is`(Blocks.PACKED_ICE)) continue
				if (!level.getBlockState(p.set(x, y + 2, z)).`is`(Blocks.HOPPER)) continue
				for ((fx, fz) in facings) {
					val lay = IcePathRoute.Layout(x, z, fx, fz)
					val wall = level.getBlockState(p.set(x + fx, y, z + fz)).`is`(Blocks.STONE_BRICKS)
					var floor = 0
					for (r in 0 until IcePathRoute.SIZE) for (c in 0 until IcePathRoute.SIZE) {
						val (wx, wz) = lay.toWorld(Cell(c, r))
						if (level.getBlockState(p.set(wx, y - 1, wz)).`is`(Blocks.PACKED_ICE)) floor++
					}
					val score = floor + if (wall) 50 else 0
					if (floor >= MIN_FLOOR && (best == null || score > best.second)) { best = lay to score; boardY = y }
				}
			}
		return best?.first
	}

	private fun compass(dx: Int, dz: Int) = when {
		dz < 0 -> "north"; dz > 0 -> "south"; dx > 0 -> "east"; else -> "west"
	}

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.icePathSolver) return
		val lay = layout ?: return
		val fish = silverfish ?: return
		val at = restingAt ?: return
		val lineColour = cfg.icePathColour.getEffectiveColourRGB() or 0xFF000000.toInt()
		val nextColour = cfg.icePathNextColour.getEffectiveColourRGB() or 0xFF000000.toInt()
		fun centre(c: Cell): Vec3 { val (x, z) = lay.toWorld(c); return Vec3(x + 0.5, boardY + 0.1, z + 0.5) }
		val label = fish.position().add(0.0, fish.bbHeight + 0.8, 0.0)
		WorldDraw.drawInWorld(event) {
			if (at in exits) { text(label, Component.literal("§aIce Path: done")); return@drawInWorld }
			val route = tracker.route
			if (route == null) { text(label, Component.literal("§cIce Path: no route from here")); return@drawInWorld }
			val trail = IcePathRoute.trail(route).map(::centre)
			if (trail.size >= 2) line(trail, color = lineColour, lineWidth = 3F)
			route.stops.drop(1).forEach { tinyBlock(centre(it).add(0.0, 0.1, 0.0), 0.25F, lineColour) }
			val d = route.next ?: return@drawInWorld
			val (dx, dz) = lay.worldStep(d)
			val from = centre(at)
			PuzzleDraw.arrow(this, from, from.add(dx * 1.5, 0.0, dz * 1.5), nextColour, 5F)
			text(label,
				Component.literal("Hit: slides ${compass(dx, dz)}").withColor(nextColour and 0xFFFFFF),
				Component.literal("§7stand on its ${compass(-dx, -dz)} side · ${route.hits.size} hit${if (route.hits.size == 1) "" else "s"} left"))
		}
	}
}
