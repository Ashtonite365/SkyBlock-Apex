/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Special Zealot alert + Special Zealot path, both part of the Dragon Tracker / Zealot Counter
 * settings (Combat, ⚠, off by default). Original implementation.
 *
 * Detection:
 *  - Hypixel's chat line "A special Zealot has spawned nearby!" (formatting stripped, case and spacing tolerant);
 *  - an Enderman in the End (combat_3, which includes the Dragon's Nest) whose carried block is END_PORTAL_FRAME.
 *    The carried-block test is an idea from another mod's Special Zealot highlighter (CREDITS.md). Idea only, no code
 *    copied.
 * One alert per Special Zealot entity id; a short cooldown keeps the chat line and the entity sighting of the same
 * zealot from both firing.
 *
 * Path: a walking route from the player to the nearest live Special Zealot, planned by the shared block A*
 * ([BlockPathPlanner], the same planner and limits as the Fairy Soul line and the dungeon Key Path) and drawn with
 * [WorldDraw] like the Key Path. Display only: it never moves or clicks for you.
 */
package dev.ashtonite365.apex.features.misc

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.time.Duration.Companion.seconds
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityDespawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.world.BlockPathPlanner
import dev.ashtonite365.apex.features.world.FairySoulPathfinder
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.render.WorldDraw

object SpecialZealot {

	private val FORMATTING = dev.ashtonite365.apex.util.SharedRegex.SECTION
	private val SPAWN_LINE = Regex("^a special zealot has spawned nearby!*$", RegexOption.IGNORE_CASE)

	fun isSpawnLine(line: String): Boolean =
		SPAWN_LINE.matches(line.replace(FORMATTING, "").replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim())

	fun isSpecialCarriedBlock(state: BlockState?): Boolean = state != null && state.`is`(Blocks.END_PORTAL_FRAME)

	fun inZealotArea(island: SkyBlockIsland?): Boolean = island != null && island == SkyBlockIsland.END

	fun isSpecialZealot(island: SkyBlockIsland?, carried: BlockState?): Boolean = inZealotArea(island) && isSpecialCarriedBlock(carried)

	class Deduper(val cooldownMs: Long = 5_000, val maxIds: Int = 256) {
		private val seen = LinkedHashSet<Int>()
		private var lastChatMs = Long.MIN_VALUE / 2
		private var lastEntityMs = Long.MIN_VALUE / 2

		fun onEntity(id: Int, nowMs: Long): Boolean {
			if (!seen.add(id)) return false
			if (seen.size > maxIds) seen.remove(seen.first())
			lastEntityMs = nowMs

			if (nowMs - lastChatMs < cooldownMs) { lastChatMs = Long.MIN_VALUE / 2; return false }
			return true
		}

		fun onChat(nowMs: Long): Boolean {
			if (nowMs - lastChatMs < cooldownMs || nowMs - lastEntityMs < cooldownMs) return false
			lastChatMs = nowMs
			return true
		}

		fun reset() {
			seen.clear(); lastChatMs = Long.MIN_VALUE / 2; lastEntityMs = Long.MIN_VALUE / 2
		}
	}

	fun newPlanner() = BlockPathPlanner(
		nodeCap = FairySoulPathfinder.NODE_CAP, maxDist = FairySoulPathfinder.MAX_DIST, replanDistSq = 4.0,
		minTicksBetween = 10, cellsPerTick = 32_768, straightFallback = false, keepLastFound = true,
		drawHeight = 0.1, goalRadius = 2.5, margin = 8, maxSpan = FairySoulPathfinder.MAX_DIST * 2 + 1, maxVolume = 1_500_000,
	)

	class PathTracker(
		val planner: BlockPathPlanner,
		val range: Int = FairySoulPathfinder.MAX_DIST - 8,
		val retargetDistSq: Double = 9.0,
		val minTicksBetweenRetarget: Int = 10,
	) {
		var target: BlockPos? = null; private set
		var route: List<Vec3>? = null; private set
		private var tick = 0
		private var lastRetarget = Int.MIN_VALUE / 2
		private var lastOutcome: BlockPathPlanner.Outcome? = null

		fun clear() {
			planner.clear(); target = null; route = null; lastOutcome = planner.lastOutcome
		}

		fun update(feet: BlockPos?, zealot: BlockPos?, source: BlockPathPlanner.Source, minY: Int, maxY: Int) {
			tick++
			if (feet == null || zealot == null) { clear(); return }
			val dx = zealot.x - feet.x; val dy = zealot.y - feet.y; val dz = zealot.z - feet.z
			if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy), kotlin.math.abs(dz)) > range) { clear(); return }
			val t = target
			if (t == null || (t.distSqr(zealot) >= retargetDistSq && tick - lastRetarget >= minTicksBetweenRetarget)) {
				target = zealot; lastRetarget = tick
			}
			planner.update(feet, target, source, minY, maxY)
			planner.result?.let { if (it.found) route = it.points }

			val outcome = planner.lastOutcome
			if (outcome !== lastOutcome) { lastOutcome = outcome; if (outcome != null && !outcome.found) route = null }
		}
	}

	private val deduper = Deduper()
	val pathTracker = PathTracker(newPlanner())
	private var tracked: Enderman? = null

	private fun alert() {
		val cfg = DragonTracker.TConfig
		if (!cfg.specialZealotAlert) return
		if (cfg.specialZealotTitle) Alerts.show(Component.literal("§5§lSPECIAL ZEALOT!"), 3.seconds, sound = cfg.specialZealotSound)
		else if (cfg.specialZealotSound) Alerts.sound()
	}

	private fun reset() {
		tracked = null; pathTracker.clear()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!DragonTracker.TConfig.specialZealotAlert || !isSpawnLine(event.bareText)) return
		if (deduper.onChat(System.currentTimeMillis())) alert()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val cfg = DragonTracker.TConfig
		if ((!cfg.specialZealotAlert && !cfg.specialZealotPath) || !inZealotArea(SkyBlockSession.skyblockIsland)) { reset(); return }
		val player = Client.player ?: run { reset(); return }
		val level = Client.level ?: run { reset(); return }
		tracked?.let { if (!it.isAlive || it.isRemoved) tracked = null }
		if (event.tickCount % 5 == 0) {
			val island = SkyBlockSession.skyblockIsland
			val live = level.getEntitiesOfClass(Enderman::class.java, player.boundingBox.inflate(96.0)) {
				it.isAlive && isSpecialZealot(island, it.carriedBlock)
			}
			val now = System.currentTimeMillis()
			for (e in live) if (deduper.onEntity(e.id, now)) alert()
			tracked = live.minByOrNull { it.distanceToSqr(player) }
		}
		if (!cfg.specialZealotPath) { pathTracker.clear(); return }
		val z = tracked
		if (z == null) { pathTracker.clear(); return }
		pathTracker.update(player.blockPosition(), z.blockPosition(), BlockPathPlanner.MinecraftSource(level), level.minY, level.maxY)
	}

	@Subscribe
	fun onDespawn(event: EntityDespawnEvent) {
		if (tracked?.id == event.entityId) reset()
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val cfg = DragonTracker.TConfig
		if (!cfg.specialZealotPath || tracked == null) return
		val route = pathTracker.route ?: return
		if (route.size < 2) return
		WorldDraw.drawInWorld(event) {
			line(route, color = cfg.specialZealotPathColour.getEffectiveColourRGB(), lineWidth = 4F)
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		reset(); deduper.reset()
	}
}
