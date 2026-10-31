/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Blaze puzzle ("Higher Or Lower") solver, rebuilt from scratch (replaces the old
 * PuzzleSolvers armor-stand sorter). Visual only: it draws a box on the blaze to shoot now and a text label on the
 * next one. It never attacks, clicks or sends packets. Rules live in BlazeOrder.kt (pure, unit-tested).
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object BlazeSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	val tracker = BlazeOrder.Tracker()
	private var roomCentre: Vec3? = null
	private val entities = mutableMapOf<Int, LivingEntity>()

	private const val MIN_BLAZES = 3

	private const val SCAN_RADIUS = 32.0

	private const val ROOM_HALF_XZ = 20.0
	private const val ROOM_HALF_Y = 48.0

	private const val NEAR_ROOM = 32.0

	private const val LEFT_ROOM = 40.0

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	private fun reset() { tracker.reset(); roomCentre = null; entities.clear() }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!cfg.blazeSolver || !DungeonUtil.isInDungeonIsland) return
		tracker.onChat(event.bareText.trim())
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 2 != 0) return
		if (!cfg.blazeSolver || !DungeonUtil.isInDungeonIsland) { if (tracker.known.isNotEmpty()) reset(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val centre = roomCentre
		val scan = if (centre != null) AABB(centre, centre).inflate(ROOM_HALF_XZ, ROOM_HALF_Y, ROOM_HALF_XZ)
		else player.boundingBox.inflate(SCAN_RADIUS)
		val tags = level.getEntitiesOfClass(ArmorStand::class.java, scan.inflate(0.0, 4.0, 0.0)) { it.hasCustomName() }.mapNotNull { stand ->
			val (hp, max) = BlazeOrder.parseNametag(stand.customName?.bareText ?: return@mapNotNull null) ?: return@mapNotNull null
			BlazeOrder.Tag(stand.id, hp, max, stand.x, stand.y, stand.z)
		}
		val blazes = level.getEntitiesOfClass(LivingEntity::class.java, scan.inflate(0.0, 4.0, 0.0)) { it.type == EntityTypes.BLAZE }
			.associateBy { it.id }
		val bodies = blazes.values.map { BlazeOrder.Body(it.id, it.x, it.y, it.z, it.bbHeight.toDouble()) }
		val seen = mutableListOf<BlazeOrder.Blaze>()
		val died = mutableSetOf<Int>()
		val gone = mutableSetOf<Int>()
		entities.clear()
		for ((tag, body) in BlazeOrder.pair(tags, bodies, tracker.tagOwners)) {
			val blaze = blazes[body.id] ?: continue
			if (blaze.isDeadOrDying || blaze.isRemoved) { died += blaze.id; continue }
			if (tag.hp > 0) tracker.tagOwners[tag.id] = blaze.id
			entities[blaze.id] = blaze
			seen += BlazeOrder.Blaze(blaze.id, tag.hp, tag.maxHp, blaze.x, blaze.y, blaze.z)
		}
		if (tracker.known.isEmpty() && seen.size < MIN_BLAZES) return

		for ((id, last) in tracker.known) {
			if (id in entities || id in died || id in tracker.dead) continue
			val e = level.getEntity(id)
			when {
				e == null -> gone += id
				e !is LivingEntity || e.isDeadOrDying || e.isRemoved -> died += id
				else -> { entities[id] = e; seen += last.copy(x = e.x, y = e.y, z = e.z) }
			}
		}
		val first = roomCentre == null
		val near = roomCentre?.let { player.position().distanceTo(it) < NEAR_ROOM } ?: true
		tracker.observe(seen, died, gone, playerNear = near)
		BlazeOrder.centreOf(tracker.known.values)?.let { (x, y, z) -> roomCentre = Vec3(x, y, z) }
		if (first) return
		val dist = roomCentre?.let { player.position().distanceTo(it) } ?: 0.0
		if (tracker.direction == null && !tracker.failed) tracker.direction = detect(level, killed = tracker.dead.size)
		if (tracker.takeDoneAnnouncement())
			Client.showChat(Component.literal("§aBlaze puzzle done."))
		if (dist > LEFT_ROOM && tracker.isDone()) reset()
	}

	private fun detect(level: net.minecraft.world.level.Level, killed: Int): BlazeOrder.Direction? {
		val living = tracker.alive()
		if (living.isEmpty()) return null
		val cx = living.map { it.x }.average().toInt(); val cz = living.map { it.z }.average().toInt()
		val centre = Vec3(cx + 0.5, living.map { it.y }.average(), cz + 0.5)
		val alive = level.getEntitiesOfClass(LivingEntity::class.java, AABB(centre, centre).inflate(ROOM_HALF_XZ, ROOM_HALF_Y, ROOM_HALF_XZ)) {
			it.type == EntityTypes.BLAZE && it.isAlive
		}.size
		if (!BlazeOrder.chestReadable(killed, alive)) return null
		val minY = living.minOf { it.y }.toInt(); val maxY = living.maxOf { it.y }.toInt()
		val chests = mutableListOf<Int>()
		val p = BlockPos.MutableBlockPos()
		for (x in cx - 14..cx + 14) for (z in cz - 14..cz + 14) for (y in minY - 12..maxY + 12) {
			val s = level.getBlockState(p.set(x, y, z))
			if ((s.`is`(Blocks.CHEST) || s.`is`(Blocks.TRAPPED_CHEST)) && level.getBlockState(p.set(x, y + 1, z)).`is`(Blocks.IRON_BARS))
				chests += y
		}
		return BlazeOrder.detectDirection(chests, living.map { it.y }, killed, alive)
	}

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.blazeSolver || tracker.known.isEmpty()) return
		val plan = tracker.plan { id -> entities[id] != null && tracker.isVisible(id) }
		WorldDraw.drawInWorld(event) {
			val centre = roomCentre ?: return@drawInWorld
			when (plan.status) {
				BlazeOrder.Status.IDLE -> return@drawInWorld
				BlazeOrder.Status.FAILED -> { text(centre, Component.literal("§cBlaze failed")); return@drawInWorld }
				BlazeOrder.Status.DONE -> {
					text(centre, Component.literal("Blaze done").withColor(cfg.blazeDoneColour.getEffectiveColourRGB() and 0xFFFFFF))
					return@drawInWorld
				}
				BlazeOrder.Status.FINDING_DIRECTION -> { text(centre, Component.literal("§7Blaze: finding reward chest…")); return@drawInWorld }
				BlazeOrder.Status.SOLVING -> {}
			}
			tracker.direction?.let { d -> text(centre.add(0.0, 1.5, 0.0), Component.literal("§e" + BlazeOrder.label(d))) }
			for (id in plan.boxes) entities[id]?.let { box(it.boundingBox.inflate(0.1), cfg.blazeCurrentColour.getEffectiveColourRGB()) }
			if (plan.tie) {
				for (id in plan.boxes) entities[id]?.let { text(it.position().add(0.0, it.bbHeight + 0.6, 0.0), Component.literal("§6⚠ Tie?")) }
				text(centre.add(0.0, 0.75, 0.0), Component.literal("§6⚠ Rounded health ties: check both blazes"))
			}
			if (plan.targetOutOfView) text(centre, Component.literal("§eBlaze: target out of view"))

			val lines = BlazeOrder.lines(plan, cfg.blazeTargetLine, cfg.blazeNextLine)
			val target = lines.target?.let { entities[it] }
			if (target != null) {

				fun centreOf(e: LivingEntity) = e.position().add(0.0, e.bbHeight / 2.0, 0.0)
				fun arr(v: Vec3) = doubleArrayOf(v.x, v.y, v.z)
				val mid = centreOf(target)
				val nextE = lines.next?.let { entities[it] }
				val live = entities.values.filter { it.isAlive && it.id !in tracker.dead }
				val blocked = cfg.blazeBlockedLineColour.getEffectiveColourRGB()
				if (cfg.blazeTargetLine) {
					val eye = Client.player?.eyePosition ?: mid
					val others = live.filter { it.id != target.id }.map { arr(centreOf(it)) }
					val col = if (BlazeOrder.blocked(arr(eye), arr(mid), others)) blocked else cfg.blazeTargetLineColour.getEffectiveColourRGB()
					tracer(mid, col, 3F)
				}
				nextE?.let { n ->
					val end = centreOf(n)
					val others = live.filter { it.id != target.id && it.id != n.id }.map { arr(centreOf(it)) }
					val col = if (BlazeOrder.blocked(arr(mid), arr(end), others)) blocked else cfg.blazeNextLineColour.getEffectiveColourRGB()
					line(mid, end, color = col, lineWidth = 3F)
				}
			}
			plan.next?.let { id ->
				entities[id]?.let { e ->
					text(e.position().add(0.0, e.bbHeight + 0.6, 0.0), Component.literal("Next").withColor(cfg.blazeNextColour.getEffectiveColourRGB() and 0xFFFFFF))
				}
			}
		}
	}
}
