/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Creeper Beams puzzle solver, rebuilt from scratch (replaces the table port in
 * CreeperBeams.kt and its PuzzleSolvers tick/render hooks). Visual only: it draws a box on both lanterns of each
 * beam pair plus a line between them, one colour per pair. It never clicks, attacks or sends packets.
 * Pairing rules live in CreeperBeamsLogic.kt (pure, unit-tested).
 * Room detection by the plain creeper standing on a sea lantern in the middle, with sea lanterns / prismarine
 * around it, and treating prismarine as beam ends too: ideas from other
 * dungeon mods (see CREDITS.md); ideas only, no code copied.
 * Deliberately independent of the room-corner lookup (PuzzleRoom.corner / DungeonRooms.currentRoomFrame),
 * which gated the old solver and made it draw nothing; see CreeperBeamsLogic.kt.
 * Room scope (Ashton, 18 Oct 2026): the solver belongs to the creeper's dungeon room cell. It only looks for the
 * creeper in the cell you stand in, and clears everything the moment you are outside that cell's 31x31 footprint
 * or in another cell (CreeperBeamsLogic.RoomGate). Before, it only reset 20 blocks (circle) from the creeper and
 * re-detected the creeper through walls from 16 blocks away, so the lanterns stayed lit after you left the room.
 * Underground (Ashton, 25 Oct 2026, Floor 7): a creeper below the floor / inside blocks sitting on the room's
 * built-in sea-lantern lighting was taken as the puzzle creeper, and that hidden lighting was drawn as beam ends.
 * Now the creeper must pass CreeperBeamsLogic.Placement.judge: first, ALWAYS, feet not below the room floor (the
 * floor scanned from the room itself); then room named Creeper Beams or not yet known, same cell, near the room
 * centre, no block collision box overlapping its hitbox, standing on a block, a clear line from your eyes. The one
 * nearest the room centre wins, a confirmed creeper is re-judged every scan, and only blocks at or above the floor
 * that the creeper can see are beam ends. Each creeper's verdict is logged once as "[ApexCreeper]" with its
 * position, the blocks at its feet / under it / at its head and the rejection reason.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object CreeperBeamsSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	val tracker = CreeperBeamsLogic.Tracker()
	private var creeperId: Int? = null
	private var hitbox: CreeperBeamsLogic.Hitbox? = null

	private var roomCell: Pair<Int, Int>? = null

	private const val SEARCH = 16.0

	private const val ROOM_HALF = 15
	private const val ROOM_BELOW = 10
	private const val ROOM_ABOVE = 12

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { reset(); floor.reset(); roomFloor = null; rejected.clear(); logged.clear() }

	private fun reset() { tracker.reset(); creeperId = null; hitbox = null; roomCell = null }

	private val floor = CreeperBeamsLogic.Placement.FloorTracker()

	private var roomFloor: Pair<Pair<Int, Int>, Int>? = null
	private val log = org.slf4j.LoggerFactory.getLogger("Apex/CreeperBeams")

	private val logged = HashSet<String>()

	private fun logOnce(key: String, line: String) {
		if (logged.size >= 400 || !logged.add(key)) return
		log.info("[ApexCreeper] {}", line)
	}

	private val HARD = setOf(
		CreeperBeamsLogic.Placement.Verdict.FLOOR_UNKNOWN, CreeperBeamsLogic.Placement.Verdict.BELOW_FLOOR,
		CreeperBeamsLogic.Placement.Verdict.OTHER_ROOM, CreeperBeamsLogic.Placement.Verdict.OFF_CENTRE,
		CreeperBeamsLogic.Placement.Verdict.BURIED,
	)

	private val rejected = HashSet<Int>()

	private fun hitboxOf(c: Creeper) = c.boundingBox.let { CreeperBeamsLogic.Hitbox(it.minX, it.minY, it.minZ, it.maxX, it.maxY, it.maxZ) }

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!cfg.creeperBeamsSolver || !DungeonUtil.isInDungeonIsland) { if (hitbox != null) reset(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val cell = CreeperBeamsLogic.RoomGate.cellOf(player.x, player.z)

		val standFloor = floor.update(cell, player.onGround(), player.y)
		if (event.tickCount % 5 != 0) return

		if (CreeperBeamsLogic.RoomGate.shouldClear(roomCell, player.x, player.z)) { reset(); return }
		val view = viewOf(level)
		if (roomFloor?.first != cell) {
			roomFloor = CreeperBeamsLogic.Placement.roomFloor(cell, Math.floor(player.eyeY).toInt(), view)?.let { cell to it }
		}
		val floorTop = CreeperBeamsLogic.Placement.floorFor(roomFloor?.second, standFloor)
		val eye = CreeperBeamsLogic.Placement.Eye(player.x, player.eyeY, player.z)
		val roomName = DungeonRoomTracker.currentRoom()?.info?.name
		val known = creeperId?.let { level.getEntity(it) as? Creeper }?.takeIf { it.isAlive }

		val knownBox = known?.let(::hitboxOf) ?: hitbox.takeIf { creeperId != null }
		if (knownBox != null) {
			val j = CreeperBeamsLogic.Placement.judge(knownBox, eye, roomName, floorTop, view)
			if (j.verdict in HARD) {
				logOnce("drop ${creeperId} ${j.verdict}", "creeper id=$creeperId dropped: ${j.verdict} ${j.detail} floorTop=$floorTop")
				creeperId?.let { rejected += it }
				reset(); return
			}
		}

		val creeper = known ?: if (creeperId != null) null else findCreeper(level, player, eye, roomName, floorTop, view)
		if (creeper != null) hitbox = hitboxOf(creeper)
		val box = hitbox ?: return
		val (rawLanterns, rawPrismarine) = scan(level, BlockPos.containing(box.cx, box.minY, box.cz), roomCell ?: CreeperBeamsLogic.RoomGate.cellOf(box.cx, box.cz))
		val open = openAt(level)

		val lanterns = CreeperBeamsLogic.Placement.endsInRoom(rawLanterns, floorTop, open, box, view)
		val prismarine = CreeperBeamsLogic.Placement.endsInRoom(rawPrismarine, floorTop, open, box, view)
		if (creeperId == null) {

			val under = CreeperBeamsLogic.Placement.support(box, view) ?: box.base
			val onBeamBlock = under in rawLanterns || under in rawPrismarine
			val around = lanterns.size + prismarine.size
			if (creeper == null || !onBeamBlock || around < CreeperBeamsLogic.MIN_ROOM_BLOCKS) {
				if (creeper != null) logOnce("notroom ${creeper.id}", "creeper id=${creeper.id} at ${fmt(box)} not taken: onBeamBlock=$onBeamBlock visibleBeamBlocks=$around")
				hitbox = null; return
			}
			creeperId = creeper.id
			roomCell = CreeperBeamsLogic.RoomGate.cellOf(box.cx, box.cz)
			logOnce("taken ${creeper.id}", "creeper id=${creeper.id} at ${fmt(box)} TAKEN: cell=${roomCell} floorTop=$floorTop (room=${roomFloor?.second} stood=$standFloor) lanterns=${lanterns.size}/${rawLanterns.size} prismarine=${prismarine.size}/${rawPrismarine.size} (seen/all)")
		}
		tracker.update(box, lanterns, prismarine)
	}

	private fun fmt(b: CreeperBeamsLogic.Hitbox) = "%.2f,%.2f,%.2f".format(b.cx, b.minY, b.cz)

	private fun findCreeper(
		level: Level, player: net.minecraft.world.entity.player.Player, eye: CreeperBeamsLogic.Placement.Eye,
		roomName: String?, floorTop: Int?, view: CreeperBeamsLogic.Placement.BlockView,
	): Creeper? {
		val found = level.getEntitiesOfClass(Creeper::class.java, player.boundingBox.inflate(SEARCH, 10.0, SEARCH)) {
			CreeperBeamsLogic.isPuzzleCreeper(it.hasCustomName(), it.isAlive) && it.id !in rejected
		}
		if (found.isEmpty()) return null
		val sel = CreeperBeamsLogic.Placement.select(found.map { it.id to hitboxOf(it) }, eye, roomName, floorTop, view)
		for (r in sel.reports) {

			if (r.verdict == CreeperBeamsLogic.Placement.Verdict.OTHER_CELL) continue
			logOnce("${r.id} ${r.verdict}", r.line() + " | room=${roomName ?: "?"} floorTop=$floorTop (room=${roomFloor?.second} stood=${floor.floorTop}) eye=%.1f,%.1f,%.1f".format(eye.x, eye.y, eye.z) +
				if (r.id == sel.chosen) " CHOSEN" else "")
		}
		return sel.chosen?.let { id -> found.firstOrNull { it.id == id } }
	}

	private fun viewOf(level: Level): CreeperBeamsLogic.Placement.BlockView = object : CreeperBeamsLogic.Placement.BlockView {
		private val m = BlockPos.MutableBlockPos()
		private fun boxes(x: Int, y: Int, z: Int): List<AABB> {
			m.set(x, y, z)
			val shape = level.getBlockState(m).getCollisionShape(level, m)
			return if (shape.isEmpty) emptyList() else shape.toAabbs().map { it.move(x.toDouble(), y.toDouble(), z.toDouble()) }
		}
		override fun collides(x: Int, y: Int, z: Int, box: CreeperBeamsLogic.Hitbox): Boolean {
			val b = AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
			return boxes(x, y, z).any { it.intersects(b) }
		}
		override fun collisionTop(x: Int, y: Int, z: Int): Double? = boxes(x, y, z).maxOfOrNull { it.maxY }
		override fun blocksLine(x: Int, y: Int, z: Int, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Boolean {
			val list = boxes(x, y, z)
			if (list.isEmpty()) return false
			val a = Vec3(ax, ay, az); val b = Vec3(bx, by, bz)
			return list.any { it.contains(a) || it.clip(a, b).isPresent }
		}
		override fun name(x: Int, y: Int, z: Int): String {
			m.set(x, y, z)
			return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(m).block).path
		}
	}

	private fun openAt(level: Level): (Int, Int, Int) -> Boolean {
		val m = BlockPos.MutableBlockPos()
		return { x, y, z ->
			m.set(x, y, z)
			val st = level.getBlockState(m)
			st.isAir || !st.canOcclude() || !st.isCollisionShapeFullBlock(level, m)
		}
	}

	private fun scan(level: Level, feet: BlockPos, cell: Pair<Int, Int>): Pair<Set<BlockPos>, Set<BlockPos>> {
		val lanterns = mutableSetOf<BlockPos>(); val prismarine = mutableSetOf<BlockPos>()
		val p = BlockPos.MutableBlockPos()
		val g = CreeperBeamsLogic.RoomGate
		val cellX = g.ORIGIN + g.STEP * cell.first; val cellZ = g.ORIGIN + g.STEP * cell.second
		val xs = maxOf(feet.x - ROOM_HALF, cellX)..minOf(feet.x + ROOM_HALF, cellX + g.SIZE - 1)
		val zs = maxOf(feet.z - ROOM_HALF, cellZ)..minOf(feet.z + ROOM_HALF, cellZ + g.SIZE - 1)
		for (x in xs) for (z in zs)
			for (y in feet.y - ROOM_BELOW..feet.y + ROOM_ABOVE) {
				val s = level.getBlockState(p.set(x, y, z))
				if (s.`is`(Blocks.SEA_LANTERN)) lanterns += p.immutable()
				else if (s.`is`(Blocks.PRISMARINE)) prismarine += p.immutable()
			}
		return lanterns to prismarine
	}

	private fun colour(slot: Int): Int {
		val list = listOf(cfg.creeperBeamsColour1, cfg.creeperBeamsColour2, cfg.creeperBeamsColour3, cfg.creeperBeamsColour4)
		return list[slot % list.size].getEffectiveColourRGB()
	}

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.creeperBeamsSolver) return
		val beams = tracker.beams
		if (beams.isEmpty()) return

		val player = Client.player ?: return
		if (CreeperBeamsLogic.RoomGate.shouldClear(roomCell, player.x, player.z) || roomCell == null) return
		WorldDraw.drawInWorld(event) {
			for (beam in beams) {
				val c = colour(beam.colour)
				box(AABB(beam.a).inflate(0.01), c)
				box(AABB(beam.b).inflate(0.01), c)
				line(Vec3.atCenterOf(beam.a), Vec3.atCenterOf(beam.b), color = c or 0xFF000000.toInt(), lineWidth = 3F)
			}
		}
	}
}
