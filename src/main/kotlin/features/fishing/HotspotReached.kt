/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.fishing

import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ServerConnectedEvent
import dev.ashtonite365.apex.events.WorldReadyEvent

class ReachedTargets(
	val radius: Double = HOTSPOT_RADIUS,
	val vertical: Double = VERTICAL_RANGE,
	val sameTolerance: Double = SAME_TOLERANCE,
) {
	private val reached = linkedSetOf<BlockPos>()

	val keys: Set<BlockPos> get() = reached

	fun key(pos: Vec3): BlockPos = BlockPos.containing(pos)

	fun inside(target: Vec3, p: Vec3?, r: Double = radius, v: Double = vertical): Boolean {
		if (p == null) return false
		val dx = p.x - target.x; val dz = p.z - target.z
		return dx * dx + dz * dz <= r * r && abs(p.y - target.y) <= v
	}

	fun isReached(target: Vec3, tolerance: Double = sameTolerance): Boolean {
		val k = key(target)
		return reached.any { it.distSqr(k) <= tolerance * tolerance }
	}

	fun markReached(target: Vec3) { if (!isReached(target)) reached += key(target) }

	fun pick(candidates: List<Vec3>, player: Vec3?, bobber: Vec3?): Vec3? {
		for (c in candidates) if (inside(c, player) || inside(c, bobber)) markReached(c)
		val left = candidates.filter { !isReached(it) }
		return if (player == null) left.firstOrNull() else left.minByOrNull { it.distanceToSqr(player) }
	}

	fun track(guess: Vec3?, player: Vec3?, bobber: Vec3?, reach: Double = radius, match: Double = GUESS_MATCH): Vec3? {
		if (guess == null) return null
		if (isReached(guess, match)) return null
		if (inside(guess, player, reach, Double.POSITIVE_INFINITY) || inside(guess, bobber, reach, Double.POSITIVE_INFINITY)) { markReached(guess); return null }
		return guess
	}

	fun reset() = reached.clear()

	companion object {

		const val HOTSPOT_RADIUS = 4.0

		const val VERTICAL_RANGE = 6.0
		const val SAME_TOLERANCE = 1.5

		const val GUESS_MATCH = 10.0

		const val GUESS_REACH = 6.0
	}
}

object HotspotReached {
	val hotspots = ReachedTargets()
	val wormholes = ReachedTargets()

	fun reset() { hotspots.reset(); wormholes.reset() }

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	@Subscribe
	fun onServer(event: ServerConnectedEvent) = reset()
}
