/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.fishing

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseItemEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object FishingRisky {
	@Config
	object TConfig : ManagedConfig("fishing-risky", Category.FISHING) {
		val radarGuesser by riskyToggle("radar-guesser", RiskLevel.GREY_AREA)
		val wormholeLocator by riskyToggle("wormhole-locator", RiskLevel.GREY_AREA)
	}

	private var radarUsed: TimeMark? = null
	private var radarOrigin: Vec3? = null
	private var radarPitch: Double? = null
	private val radarParticles = mutableListOf<Vec3>()
	private var guess: Vec3? = null

	@Subscribe
	fun onUse(event: UseItemEvent) {
		if (!TConfig.radarGuesser) return
		if (!event.item.hoverName.bareText.contains("Radar", true)) return
		radarUsed = TimeMark.now(); radarOrigin = event.playerEntity.position(); radarPitch = event.playerEntity.xRot.toDouble(); radarParticles.clear()
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		val used = radarUsed ?: return
		if (used.passedTime() > 3.seconds) return
		val o = radarOrigin ?: return
		if (!HotspotRadarMath.isTrailParticle(event.particleEffect.type == ParticleTypes.FLAME, event.count, event.speed)) return
		if (!HotspotRadarMath.accept(radarParticles, event.position, o)) return
		radarParticles += event.position

		HotspotRadarMath.guess(radarParticles, radarPitch)?.let { guess = HotspotReached.hotspots.track(it, null, null) }
	}

	private var wormhole: BlockPos? = null

	@Subscribe
	fun onTick(event: TickEvent) {
		val player = Client.player ?: return
		val bobber = player.fishing?.position()

		guess?.let { g ->
			if (HotspotReached.hotspots.track(g, player.position(), bobber, ReachedTargets.GUESS_REACH) == null) {
				guess = null; radarUsed = null; radarOrigin = null; radarPitch = null; radarParticles.clear()
			}
		}
		if (event.tickCount % 10 != 0) return
		wormhole = if (TConfig.wormholeLocator) Client.level?.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(128.0)) {
			it.customName?.bareText?.contains("Wormhole", true) == true
		}?.let { stands -> HotspotReached.wormholes.pick(stands.map { it.position() }, player.position(), bobber) }
			?.let { BlockPos.containing(it) } else null
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		guess = null; wormhole = null; radarUsed = null
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val g = guess.takeIf { TConfig.radarGuesser }
		val w = wormhole
		if (g == null && w == null) return
		WorldDraw.drawInWorld(event, WaypointType.FISHING) {
			if (g != null) {
				waypoint(BlockPos.containing(g), Component.literal("§dHotspot (guess)"))
				tracer(g, 0xFFFF55FF.toInt())
			}
			if (w != null) {
				waypoint(w, Component.literal("§5Wormhole"))
				tracer(Vec3.atCenterOf(w), 0xFFAA00AA.toInt())
			}
		}
	}
}
