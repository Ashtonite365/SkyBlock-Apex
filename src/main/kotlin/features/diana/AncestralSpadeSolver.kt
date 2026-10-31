// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.diana

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.WarpTravel
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems

class SpadeEcho(private val keep: Int = 20) {
	val trail = ArrayDeque<Vec3>()
	private val notes = ArrayDeque<Float>()

	private fun <T> ArrayDeque<T>.push(value: T) {
		addLast(value)
		while (size > keep) removeFirst()
	}

	fun particle(at: Vec3) = trail.push(at)
	fun note(pitch: Float) = notes.push(pitch)

	fun clear() {
		trail.clear()
		notes.clear()
	}

	fun estimate(soundAt: Vec3): Vec3? {
		if (trail.size < 3) return null
		val meanStep = notes.zipWithNext { lower, higher -> higher - lower }.average()
		val distance = Math.E / meanStep - trail.first().distanceTo(soundAt)
		if (distance > 1000) return null
		val heading = trail[trail.size - 1].subtract(trail[trail.size - 3]).normalize()
		return soundAt.add(heading.scale(distance))
	}
}

object AncestralSpadeSolver {
	private val echo = SpadeEcho()
	private val spades = setOf(SkyBlockItems.ANCESTRAL_SPADE, SkyBlockItems.ARCHAIC_SPADE, SkyBlockItems.DEIFIC_SPADE)

	val particlePositions: List<Vec3> get() = echo.trail

	var lastDing: TimeMark = TimeMark.farPast()
		private set

	var nextGuess: Vec3? = null
		private set

	private var lastWarp = TimeMark.farPast()

	fun isEnabled(): Boolean {
		if (!DianaWaypoints.TConfig.ancestralSpadeSolver || SkyBlockSession.skyblockIsland != SkyBlockIsland.HUB) return false
		val inventory = Client.player?.inventory ?: return false
		return inventory.hasAnyMatching { stack -> stack.view().repoItemId in spades }
	}

	@Subscribe
	fun warpKey(event: WorldKeyboardEvent) {
		if (!isEnabled() || !event.matches(DianaWaypoints.TConfig.ancestralSpadeTeleport)) return
		val target = nextGuess
		if (lastWarp.passedTime() < 3.seconds || target == null) return
		WarpTravel.warpNearest(SkyBlockIsland.HUB, target)
		lastWarp = TimeMark.now()
	}

	@Subscribe
	fun onParticleSpawn(event: ParticleSpawnEvent) {
		val still = event.offset.x == 0f && event.offset.y == 0f && event.offset.z == 0f
		if (isEnabled() && event.particleEffect == ParticleTypes.DRIPPING_LAVA && still) echo.particle(event.position)
	}

	@Subscribe
	fun hearNote(event: SoundReceiveEvent) {
		if (!isEnabled() || !SoundEvents.NOTE_BLOCK_HARP.`is`(event.sound.value().location)) return
		val heard = TimeMark.now()

		if (lastDing.passedTime() > 1.seconds) echo.clear()
		lastDing = heard
		echo.note(event.pitch)
		echo.estimate(event.position)?.let { nextGuess = it }
	}

	@Subscribe
	fun drawGuess(event: WorldRenderLastEvent) {
		if (isEnabled()) WorldDraw.drawInWorld(event, WaypointType.DIANA) { drawEstimate() }
	}

	private fun WorldDraw.drawEstimate() {
		val guess = nextGuess ?: return
		val faint = 0x80FFFFFF.toInt()
		tinyBlock(guess, 1f, faint)
		tracer(guess, lineWidth = 3f, color = faint)
		if (DianaWaypoints.TConfig.guessBeam) line(guess, guess.add(0.0, 100.0, 0.0), color = 0xFFFFAA00.toInt(), lineWidth = 6f)

		if (echo.trail.size > 2 && lastDing.passedTime() < 10.seconds) line(echo.trail.toList(), color = faint)
	}

	@Subscribe
	fun onSwapWorld(event: WorldReadyEvent) = forget()

	private fun forget() {
		echo.clear()
		lastDing = TimeMark.farPast().also { nextGuess = null }
	}
}
