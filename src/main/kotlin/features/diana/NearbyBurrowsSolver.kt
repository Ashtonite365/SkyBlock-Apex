// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.diana

import dev.ashtonite365.apex.util.render.WaypointType
import me.shedaniel.math.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.collections.lruMap
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.WorldDraw.Companion.drawInWorld

object NearbyBurrowsSolver {
	enum class BurrowType { START, MOB, TREASURE }

	val burrows: MutableMap<BlockPos, BurrowType> = mutableMapOf()

	private val dugAt: MutableMap<BlockPos, TimeMark> = lruMap(20)
	private val markedAt: MutableMap<BlockPos, TimeMark> = lruMap(500)
	private var clickedBlock: BlockPos? = null

	private val enabled get() = DianaWaypoints.TConfig.nearbyWaypoints

	private val DUG_MEMORY = 10.seconds
	private val MARK_MEMORY = 4.seconds

	private val DIG_MESSAGES = listOf("You dug out a Griffin Burrow!", " ☠ You were killed by", "You finished the Griffin burrow chain!")

	private class Kind(val type: ParticleType<*>, val count: Int, val spread: Float, val burrow: BurrowType)

	private val kinds = listOf(
		Kind(ParticleTypes.ENCHANTED_HIT, 4, 0.5f, BurrowType.START),
		Kind(ParticleTypes.CRIT, 3, 0.5f, BurrowType.MOB),
		Kind(ParticleTypes.DRIPPING_LAVA, 2, 0.35f, BurrowType.TREASURE),
	)

	private fun MutableMap<BlockPos, TimeMark>.within(pos: BlockPos, memory: Duration) =
		(this[pos] ?: TimeMark.farPast()).passedTime() < memory

	fun wasRecentlyDug(blockPos: BlockPos): Boolean = dugAt.within(blockPos, DUG_MEMORY)
	fun markAsDug(blockPos: BlockPos) = dugAt.set(blockPos, TimeMark.now())
	fun wasRecentlyEnchanted(blockPos: BlockPos): Boolean = markedAt.within(blockPos, MARK_MEMORY)
	fun markAsEnchanted(blockPos: BlockPos) = markedAt.set(blockPos, TimeMark.now())

	private fun ParticleSpawnEvent.spreadIs(horizontal: Float, vertical: Float) =
		offset.x == horizontal && offset.z == horizontal && offset.y == vertical

	@Subscribe
	fun onParticles(event: ParticleSpawnEvent) {
		if (!enabled) return
		val block = event.position.toBlockPos().below()
		if (wasRecentlyDug(block)) return
		val type = event.particleEffect.type
		if (type == ParticleTypes.ENCHANT) {
			if (event.count == 5 && event.speed == 0.05f && event.spreadIs(0.5f, 0.4f)) markAsEnchanted(block)
			return
		}
		if (!wasRecentlyEnchanted(block) || event.speed != 0.01f) return
		val kind = kinds.firstOrNull { it.type == type && it.count == event.count && event.spreadIs(it.spread, 0.1f) } ?: return
		burrows[block] = kind.burrow
	}

	fun onBlockClick(blockPos: BlockPos) {
		if (!enabled) return
		burrows -= blockPos
		clickedBlock = blockPos
	}

	@Subscribe
	fun onChatEvent(event: ProcessChatEvent) {
		val block = clickedBlock ?: return
		if (DIG_MESSAGES.none { event.bareText.startsWith(it) }) return
		markAsDug(block)
		burrows -= block
	}

	private fun BurrowType.colour(): Color = when (this) {
		BurrowType.START -> translucent(.2f, .8f, .2f)
		BurrowType.MOB -> translucent(.3f, .4f, .9f)
		BurrowType.TREASURE -> translucent(1f, .7f, .2f)
	}

	private fun translucent(r: Float, g: Float, b: Float): Color = Color.ofRGBA(r, g, b, 0.4f)

	private fun BurrowType.label(): String = when (this) {
		BurrowType.START -> "§aStart"
		BurrowType.MOB -> "§9Mob"
		BurrowType.TREASURE -> "§6Treasure"
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (enabled) drawInWorld(event, WaypointType.DIANA) { drawBurrows(DianaWaypoints.TConfig) }
	}

	private fun WorldDraw.drawBurrows(cfg: DianaWaypoints.TConfig) {
		for ((pos, kind) in burrows) {
			val argb = kind.colour().color
			block(pos, argb)
			if (cfg.burrowLabels) waypoint(pos.above(), Component.literal(kind.label()))
			if (!cfg.burrowBeams) continue
			val foot = Vec3.atCenterOf(pos)
			line(foot, foot.add(0.0, 100.0, 0.0), color = argb or 0xFF000000.toInt(), lineWidth = 6f)
		}
	}

	@Subscribe
	fun onSwapWorld(worldReadyEvent: WorldReadyEvent) {
		listOf(burrows, markedAt, dugAt).forEach { it.clear() }
		clickedBlock = null
	}
}

fun Position.toBlockPos(): BlockPos = BlockPos(Mth.floor(x()), Mth.floor(y()), Mth.floor(z()))
