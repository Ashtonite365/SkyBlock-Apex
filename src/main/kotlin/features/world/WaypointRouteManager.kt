// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.serializer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.ClipboardUtils
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ShareCodes
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.tr

object WaypointRouteManager {
	@Config
	object DConfig : DataHolder<MutableMap<String, WaypointRoute>>(serializer(), "waypoints", ::mutableMapOf)

	val SHARE_PREFIX = "FIRM_WAYPOINTS/"
	val ENCODED_SHARE_PREFIX = ShareCodes.base64PrefixKey(SHARE_PREFIX)

	private fun WaypointRoute.shiftedBy(sign: Int, origin: BlockPos): WaypointRoute = deepCopy().also { moved ->
		moved.waypoints.replaceAll { p -> p.shifted(sign * origin.x, sign * origin.y, sign * origin.z) }
	}

	fun createExportableCopy(waypoints: WaypointRoute): WaypointRoute {
		if (waypoints.isRelativeTo == null) return waypoints.deepCopy()
		val origin: BlockPos = waypoints.lastRelativeImport
			?: throw NotImplementedError("Exporting a relative route needs the position it was placed at")
		return waypoints.shiftedBy(-1, origin)
	}

	fun loadWaypoints(waypoints: WaypointRoute, sendFeedback: (Component) -> Unit): Unit {
		val hint = waypoints.isRelativeTo
		val active = if (hint == null) {
			waypoints.deepCopy().also { sendFeedback(tr("apex.command.waypoint.import.success", "Imported ${it.size} waypoints.")) }
		} else {
			val feet = Client.player!!.blockPosition().immutable()
			waypoints.shiftedBy(+1, feet).also {
				it.lastRelativeImport = feet
				sendFeedback(tr("apex.command.waypoint.import.ordered.success", "Imported ${it.size} relative waypoints. Make sure you stand in the correct spot while loading the waypoints: ${it.isRelativeTo}."))
			}
		}
		Waypoints.waypoints = active
	}

	fun setOrigin(source: DefaultSource, text: String?): Unit {
		val route = Waypoints.useEditableWaypoints()
		val feet = Client.player!!.blockPosition()
		route.isRelativeTo = text ?: route.isRelativeTo ?: ""
		route.lastRelativeImport = feet
		source.sendFeedback(tr("apex.command.waypoint.originset", "Set the origin of waypoints to ${ApexFormatters.positionText(feet)}. Run /apex waypoints export to save the waypoints relative to this position."))
	}

	private fun DefaultSource.exportableRouteOrComplain(): WaypointRoute? {
		val route = Waypoints.useNonEmptyWaypoints()
		if (route == null) sendError(Waypoints.textNothingToExport())
		return route
	}

	private fun saveAs(source: DefaultSource, key: String): Unit {
		val route = source.exportableRouteOrComplain() ?: return
		route.id = key
		DConfig.data[key] = createExportableCopy(route)
		DConfig.markDirty()
		source.sendFeedback(tr("apex.command.waypoint.saved", "Saved waypoints locally as $key. Use /apex waypoints load to load them again."))
	}

	private fun loadSaved(source: DefaultSource, key: String): Unit {
		when (val stored = DConfig.data[key]) {
			null -> source.sendError(tr("apex.command.waypoint.nosaved", "No saved waypoint for $key. Use tab completion to see available names."))
			else -> loadWaypoints(stored, source::sendFeedback)
		}
	}

	private fun exportToClipboard(source: DefaultSource): Unit {
		val route = source.exportableRouteOrComplain() ?: return
		val shareable = createExportableCopy(route)
		ClipboardUtils.setTextContent(ShareCodes.toShareCode(SHARE_PREFIX, shareable))
		source.sendFeedback(tr("apex.command.waypoint.export", "Copied ${shareable.size} waypoints to clipboard in SkyBlock Apex format."))
	}

	private fun importFromClipboard(source: DefaultSource): Unit {
		val clip = ClipboardUtils.getTextContents()
		if (clip.startsWith("[")) {
			source.sendError(tr("apex.command.waypoint.import.lookslikecoords", "The waypoints in your clipboard look like a coordinate list. If so, use /apex waypoints importcoords or /apex waypoints importrelativecoords."))
			return
		}
		val decoded = ShareCodes.fromShareCode<WaypointRoute>(SHARE_PREFIX, clip)
		if (decoded != null) return loadWaypoints(decoded, source::sendFeedback)
		source.sendError(tr("apex.command.waypoint.import.error", "Could not import waypoints from your clipboard. Make sure they are compatible waypoints."))
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand): Unit {
		event.addSubcommand(Waypoints.WAYPOINTS_SUBCOMMAND) {
			onLiteral("setorigin") {
				onRun { setOrigin(source, null) }
				onArgument("hint", RestArgumentType) { hint -> onRun { setOrigin(source, this[hint]) } }
			}
			onLiteral("clearorigin") {
				onRun {
					Waypoints.useEditableWaypoints().apply {
						lastRelativeImport = null
						isRelativeTo = null
					}
					source.sendFeedback(tr("apex.command.waypoint.originunset", "Unset the origin of the waypoints. Run /apex waypoints export to save the waypoints with absolute coordinates."))
				}
			}
			onLiteral("save") {
				onArgument("name", StringArgumentType.string()) { key ->
					suggestFrom { DConfig.data.keys }
					onRun { saveAs(source, get(key)) }
				}
			}
			onLiteral("load") {
				onArgument("name", StringArgumentType.string()) { key ->
					suggestFrom { DConfig.data.keys }
					onRun { loadSaved(source, get(key)) }
				}
			}
			onLiteral("export") { onRun { exportToClipboard(source) } }
			onLiteral("import") { onRun { importFromClipboard(source) } }
		}
	}
}
