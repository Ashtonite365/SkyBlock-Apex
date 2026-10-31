// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import dev.ashtonite365.apex.util.render.WaypointType
import java.util.regex.Pattern
import me.shedaniel.math.Color
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.world.Waypoints.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.center
import dev.ashtonite365.apex.util.render.UvRect
import dev.ashtonite365.apex.util.render.WorldDraw

object TemporaryWaypoints {

	data class TemporaryWaypoint(val pos: BlockPos, val postedAt: TimeMark)

	val temporaryPlayerWaypointList = mutableMapOf<String, TemporaryWaypoint>()

	val temporaryPlayerWaypointMatcher: Pattern = Pattern.compile("(?i)x: (-?[0-9]+),? y: (-?[0-9]+),? z: (-?[0-9]+)")

	private val MARKER_COLOUR = Color.ofRGBA(255, 255, 0, 128)

	@Subscribe
	fun onProcessChat(it: ProcessChatEvent): Unit {
		val sender = it.nameHeuristic ?: return
		if (TConfig.tempWaypointDuration <= 0.seconds) return
		val found = temporaryPlayerWaypointMatcher.matcher(it.bareText)
		if (!found.find()) return
		val (x, y, z) = (1..3).map { group -> found.group(group).toInt() }
		temporaryPlayerWaypointList[sender] = TemporaryWaypoint(BlockPos(x, y, z), TimeMark.now())
	}

	@Subscribe
	fun onRenderTemporaryWaypoints(event: WorldRenderLastEvent): Unit {
		val lifetime = TConfig.tempWaypointDuration
		temporaryPlayerWaypointList.values.removeAll { marker -> marker.postedAt.passedTime() > lifetime }
		if (!TConfig.enabled || temporaryPlayerWaypointList.isEmpty()) return
		val markers = temporaryPlayerWaypointList.toList()
		WorldDraw.drawInWorld(event, WaypointType.TEMPORARY) {
			for ((_, marker) in markers) block(marker.pos, MARKER_COLOUR.color)
			for ((sender, marker) in markers) {
				val face = Client.connection?.listedOnlinePlayers?.firstOrNull { entry -> entry.profile.name == sender }?.skin?.body
				facingCamera(marker.pos.center()) {
					waypoint(marker.pos, Component.translatableEscape("apex.waypoint.temporary", sender))
					if (face == null) return@facingCamera
					worldPoses.translate(0F, -20F, 0F)

					texture(face.id(), 16, 16, UvRect(1 / 8f, 1 / 8f, 2 / 8f, 2 / 8f))
					texture(face.id(), 16, 16, UvRect(5 / 8f, 1 / 8f, 6 / 8f, 2 / 8f))
				}
			}
		}
	}

	@Subscribe
	fun onWorldReady(event: WorldReadyEvent) = temporaryPlayerWaypointList.clear()
}
