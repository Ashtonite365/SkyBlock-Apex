// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.ClipboardUtils
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.tr

object CoordListWaypoints {

	@Serializable
	data class CoordListEntry(val x: Int? = null, val y: Int? = null, val z: Int? = null, val r: Int = 0, val g: Int = 0, val b: Int = 0)

	private val listFormat = ListSerializer(CoordListEntry.serializer())

	fun tryParse(string: String): Result<List<CoordListEntry>> = runCatching {
		Apex.json.decodeFromString(listFormat, string)
	}

	fun fromRoute(waypoints: WaypointRoute, relativeTo: BlockPos): List<CoordListEntry> =
		waypoints.waypoints.map { point ->
			CoordListEntry(point.x - relativeTo.x, point.y - relativeTo.y, point.z - relativeTo.z)
		}

	fun intoRoute(
		waypoints: List<CoordListEntry>,
		relativeTo: BlockPos,
	): WaypointRoute {
		val shifted = mutableListOf<WaypointRoute.Waypoint>()
		for (entry in waypoints) {
			val x = entry.x ?: continue
			val y = entry.y ?: continue
			val z = entry.z ?: continue
			shifted += WaypointRoute.Waypoint(x + relativeTo.x, y + relativeTo.y, z + relativeTo.z)
		}
		return WaypointRoute("Imported Waypoints", "imported", null, shifted, false)
	}

	private fun playerFeet(): BlockPos = Client.player?.blockPosition() ?: BlockPos.ZERO

	private fun export(source: DefaultSource, relative: Boolean) {
		val route = Waypoints.useNonEmptyWaypoints() ?: return source.sendError(Waypoints.textNothingToExport())
		val origin = if (relative) playerFeet() else BlockPos.ZERO
		val entries = fromRoute(route, origin)
		ClipboardUtils.setTextContent(Apex.tightJson.encodeToString(listFormat, entries))
		val n = entries.size
		val note: Component = if (relative) {
			tr("apex.command.waypoint.export.coords.relative", "Copied $n relative waypoints to clipboard as a coordinate list. Make sure to stand in the same position when importing.")
		} else {
			tr("apex.command.waypoint.export.coords", "Copied $n waypoints to clipboard as a coordinate list.")
		}
		source.sendFeedback(note)
	}

	private fun import(source: DefaultSource, relative: Boolean) {
		val entries = tryParse(ClipboardUtils.getTextContents()).getOrNull()
			?: return source.sendError(tr("apex.command.waypoint.import.coords.error", "Could not import a coordinate list from the clipboard."))
		val route = intoRoute(entries, if (relative) playerFeet() else BlockPos.ZERO)
		Waypoints.waypoints = route
		val n = route.size
		source.sendFeedback(
			if (relative) tr("apex.command.waypoint.import.coords.relative", "Imported $n relative waypoints from clipboard. Make sure you stand in the same position as when you exported these waypoints for them to line up correctly.")
			else tr("apex.command.waypoint.import.coords.success", "Imported $n waypoints from a coordinate list.")
		)
	}

	@Subscribe
	fun onEvent(event: CommandEvent.SubCommand): Unit {
		event.addSubcommand(Waypoints.WAYPOINTS_SUBCOMMAND) {
			for (relative in listOf(false, true)) {
				val infix = if (relative) "relative" else ""
				onLiteral("export${infix}coords") { onRun { export(source, relative) } }
				onLiteral("import${infix}coords") { onRun { import(source, relative) } }
			}
		}
	}
}
