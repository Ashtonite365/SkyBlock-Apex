// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3

object GardenPestWaypoint {
	private const val TRAIL_TIMEOUT_MS = 2_000L
	private const val FIRST_POINT_RANGE = 5.0
	private const val CHAIN_RANGE = 3.0
	private const val DUPLICATE_SQ = 1e-6

	private val trail = ArrayList<Vec3>()
	private var trailPitch = 0.0
	private var lastPointAt = 0L
	private var trailStart = 0L
	var guess: Vec3? = null
		private set

	fun isTrailParticle(isAngryVillager: Boolean, count: Int, speed: Float, offsetZero: Boolean): Boolean {
		if (!isAngryVillager || count != 1) return false
		return speed == 0f && offsetZero
	}

	fun add(p: Vec3, player: Vec3, now: Long, playerPitch: Double): Boolean {
		if (now - lastPointAt > TRAIL_TIMEOUT_MS) reset()
		val accepted = if (trail.isEmpty()) {
			!(p.distanceTo(player) > FIRST_POINT_RANGE)
		} else {
			!(p.distanceTo(trail[trail.size - 1]) > CHAIN_RANGE) && trail.none { it.distanceToSqr(p) < DUPLICATE_SQ }
		}
		if (!accepted) return false
		if (trail.isEmpty()) {
			trailPitch = playerPitch
			trailStart = now
		}
		trail.add(p)
		lastPointAt = now
		val solved = PestTrailMath.solve(trail)
		if (solved != null) guess = solved
		return true
	}

	fun reset() {
		trail.clear()
		guess = null
	}

	private fun holdingVacuum(): Boolean {
		val core = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId ?: return false
		return "VACUUM" in core
	}

	fun currentGuess(): Vec3? = guess

	private fun wanted() = TConfig.pestTrackerGuessLine || TConfig.pestTrackerWaypoint

	private fun isHiddenNoise(type: Any, count: Int, speed: Float, offsetZero: Boolean): Boolean =
		type == ParticleTypes.FIREWORK || (type == ParticleTypes.ENCHANT && count == 10 && speed == -2f && offsetZero)

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!wanted() || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val type = event.particleEffect.type
		val off = event.offset
		val offsetZero = off.x == 0f && off.y == 0f && off.z == 0f
		if (TConfig.pestWaypointHideParticles && isHiddenNoise(type, event.count, event.speed, offsetZero)) event.cancel()
		if (!isTrailParticle(type == ParticleTypes.ANGRY_VILLAGER, event.count, event.speed, offsetZero)) return
		val player = Client.player ?: return
		if (trail.isEmpty() && !holdingVacuum()) return
		if (TConfig.pestWaypointHideParticles) event.cancel()
		add(event.position, player.position(), System.currentTimeMillis(), player.xRot.toDouble())
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val target = guess ?: return
		val now = System.currentTimeMillis()
		val player = Client.player ?: return
		val age = now - trailStart
		val horizontal = Vec3(target.x - player.x, 0.0, target.z - player.z).length()
		val expired = age > TConfig.pestWaypointSeconds * 1000L
		val arrived = horizontal < 8 && age > 1000
		if (expired || arrived) reset()
	}
}
