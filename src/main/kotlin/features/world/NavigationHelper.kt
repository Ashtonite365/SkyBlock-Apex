// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import io.github.moulberry.repo.constants.Islands
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.WarpTravel
import dev.ashtonite365.apex.util.render.WaypointType
import dev.ashtonite365.apex.util.render.WorldDraw

object NavigationHelper {
	private const val ARRIVED_DIST_SQ = 25.0

	var targetWaypoint: NavigableWaypoint? = null
		set(value) {
			field = value
			routeAgain()
		}

	var firstHop: Islands.Teleporter? = null
		private set

	val Islands.Teleporter.originIsland get() = SkyBlockIsland.ofMode(from)
	val Islands.Teleporter.destIsland get() = SkyBlockIsland.ofMode(to)

	val Islands.Teleporter.exitPos: BlockPos
		get() = portalOverride(from, to) ?: BlockPos(x.toInt(), y.toInt(), z.toInt())

	fun portalOverride(from: String?, to: String?): BlockPos? =
		if (from == "hub" && to == "rift") BlockPos(45, 152, 73) else null

	private fun routeFrom(from: SkyBlockIsland, to: SkyBlockIsland, seen: MutableSet<SkyBlockIsland>): List<Islands.Teleporter>? {
		val passed = seen.toMutableSet().also { it += from }
		var best: List<Islands.Teleporter>? = null
		for (hop in SafeRepoConstants.teleporters()) {
			if (hop.originIsland != from) continue
			val dest = hop.destIsland
			if (dest in passed) continue
			val candidate = if (dest == to) listOf(hop) else routeFrom(dest, to, passed)?.let { listOf(hop) + it } ?: continue
			if (best == null || candidate.size < best.size) best = candidate
		}
		return best
	}

	fun routeAgain() {
		val target = targetWaypoint
		val here = SkyBlockSession.skyblockIsland
		firstHop = if (target == null || here == null || here == target.island) null
		else routeFrom(here, target.island, mutableSetOf())?.firstOrNull()
	}

	@Subscribe
	fun serverChanged(event: SkyblockServerUpdateEvent) = routeAgain()

	@Subscribe
	fun checkArrival(event: TickEvent) {
		val target = targetWaypoint ?: return
		if (SkyBlockSession.skyblockIsland != target.island) return
		val me = Client.player ?: return
		if (target.position.distToCenterSqr(me.position()) < ARRIVED_DIST_SQ)
			targetWaypoint = null
	}

	@Subscribe
	fun paintTarget(event: WorldRenderLastEvent) {
		val target = targetWaypoint ?: return
		val hop = firstHop
		if (hop == null && SkyBlockSession.skyblockIsland != target.island) return
		WorldDraw.drawInWorld(event, WaypointType.NPCS) {
			if (hop == null) {
				waypoint(target.position, Component.literal(target.name))
			} else {
				waypoint(hop.exitPos,
					Component.literal("Teleporter to ${hop.destIsland.userFriendlyName}"),
					Component.literal("(towards ${target.name}§f)"))
			}
		}
	}

	fun tryWarpNear() {
		val target = targetWaypoint
		if (target == null) {
			Client.showChat(Component.literal("Could not find a waypoint to warp you to. Select one first."))
			return
		}
		WarpTravel.warpNearest(target.island, target.position.asPosition())
	}
}

fun Vec3i.asPosition(): Position {
	val v = this
	return object : Position {
		override fun x(): Double = v.x.toDouble()
		override fun y(): Double = v.y.toDouble()
		override fun z(): Double = v.z.toDouble()
	}
}
