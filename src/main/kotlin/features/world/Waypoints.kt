// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import dev.ashtonite365.apex.util.render.WaypointType
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.github.notenoughupdates.moulconfig.ChromaColour
import me.shedaniel.math.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.center
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.asFakeServer
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.tr

object Waypoints {
	val identifier: String get() = "waypoints"

	const val WAYPOINTS_SUBCOMMAND = "waypoints"

	@Config
	object TConfig : ManagedConfig(category = Category.RENDER, name = identifier) {

		val enabled by toggle("enabled") { true }

		val manager by option("manager", { }, ApexWaypointManagerHandler)
		val markerStyle by choice("marker-style") { MarkerStyle.BEAM_AND_BOX }
		val markerColour by colour("colour") { ChromaColour.fromStaticRGB(255, 170, 0, 110) }
		val tempWaypointDuration: Duration by duration("temp-waypoint-duration", 0.seconds, 1.hours) { 30.seconds }
		val showIndex: Boolean by toggle("show-index") { true }
		val skipToNearest: Boolean by toggle("skip-to-nearest") { false }
		val resetWaypointOrderOnWorldSwap: Boolean by toggle("reset-order-on-swap") { true }
	}

	enum class MarkerStyle(val id: String, val beam: Boolean, val box: Boolean) : StringRepresentable {
		BEAM("beam", beam = true, box = false),
		BOX("box", beam = false, box = true),
		BEAM_AND_BOX("beam-and-box", beam = true, box = true);

		override fun getSerializedName(): String = this.id
	}

	var waypoints: WaypointRoute? = null

	var orderedIndex = 0

	private val unorderedColour = Color.ofRGBA(0, 80, 160, 128)

	private val upcomingColours = listOf(Color.ofRGBA(0, 200, 40, 180), Color.ofRGBA(180, 200, 40, 150), Color.ofRGBA(180, 80, 20, 140))

	fun useEditableWaypoints(): WaypointRoute =
		waypoints ?: WaypointRoute("Unlabeled", "unknown", null, mutableListOf(), false).also { waypoints = it }

	fun useNonEmptyWaypoints(): WaypointRoute? = waypoints?.takeUnless { it.waypoints.isEmpty() }

	private fun nearestIndex(route: WaypointRoute, from: Vec3): Int? =
		route.waypoints.indices.minByOrNull { route.waypoints[it].blockPos.distToCenterSqr(from) }

	@Subscribe
	fun onRenderOrderedWaypoints(event: WorldRenderLastEvent): Unit {
		if (!TConfig.enabled) return
		val route = useNonEmptyWaypoints() ?: return
		WorldDraw.drawInWorld(event, WaypointType.ROUTES) {
			fun mark(index: Int, point: WaypointRoute.Waypoint, colour: Color): Unit {
				block(point.blockPos, colour.color)
				if (TConfig.showIndex) facingCamera(point.blockPos.center()) { text(Component.literal("$index")) }
			}
			if (route.isOrdered) {
				orderedIndex %= route.size
				tracer(route.waypoints[orderedIndex].blockPos.center(), color = upcomingColours[0].color, lineWidth = 3f)
				val upcoming = route.waypoints.withIndex().toList().wrappingWindow(orderedIndex, upcomingColours.size)

				for (step in upcoming.indices.reversed()) mark(upcoming[step].index, upcoming[step].value, upcomingColours[step])
			} else {
				route.waypoints.forEachIndexed { index, point -> mark(index, point, unorderedColour) }
			}
		}
	}

	@Subscribe
	fun onTick(event: TickEvent): Unit {
		val route = useNonEmptyWaypoints()?.takeIf { it.isOrdered } ?: return
		val count = route.size
		orderedIndex %= count
		val feet = Client.player?.position() ?: return
		orderedIndex = when {
			TConfig.skipToNearest -> (nearestIndex(route, feet)!! + 1) % count
			route.waypoints[orderedIndex].blockPos.closerToCenterThan(feet, 3.0) -> (orderedIndex + 1) % count
			else -> orderedIndex
		}
	}

	@Subscribe
	fun onWorldSwap(event: WorldReadyEvent): Unit {
		if (TConfig.resetWaypointOrderOnWorldSwap) orderedIndex = 0
	}

	private fun moveIndex(source: DefaultSource, from: Int, to: Int): Unit {
		val points = useEditableWaypoints().waypoints
		val bad = listOf(from, to).firstOrNull { it !in points.indices }
		if (bad != null) return source.sendError(textInvalidIndex(bad))
		val moving = points.removeAt(from)
		points.add(if (to > from) to - 1 else to, moving)
		source.sendFeedback(tr("apex.command.waypoint.indexchange", "Moved waypoint from index $from to $to. Note that this only matters for ordered waypoints."))
	}

	private fun toggleOrdered(source: DefaultSource): Unit {
		val route = useEditableWaypoints()
		route.isOrdered = route.isOrdered.not()
		if (route.isOrdered) orderedIndex = nearestIndex(route, Client.player?.position() ?: Vec3.ZERO) ?: 0
		source.sendFeedback(Component.translatable("apex.command.waypoint.ordered.toggle.${route.isOrdered}"))
	}

	private fun removeAt(source: DefaultSource, index: Int): Unit {
		val route = useNonEmptyWaypoints()
		if (route == null || index !in route.waypoints.indices) {
			source.sendError(Component.translatableEscape("apex.command.waypoint.remove.error"))
			return
		}
		route.waypoints.removeAt(index)
		source.sendFeedback(Component.translatableEscape("apex.command.waypoint.remove", index))
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand): Unit {
		event.register(ManagedConfig.Category.RENDER, "waypoint",
			"Named per-island waypoints: add <name> [x y z] | list [all] | remove <name>; or <x y z> to add a route point") {
			ApexWaypointCommands.attach(this, ApexWaypoints.commandSink)
			onArgument("pos", BlockPosArgument.blockPos()) { posArg ->
				onRun {
					val at = posArg.get(this).getBlockPos(source.asFakeServer())
					useEditableWaypoints().waypoints += WaypointRoute.Waypoint.from(at)
					source.sendFeedback(Component.translatableEscape("apex.command.waypoint.added", at.x, at.y, at.z))
				}
			}
		}
		event.addSubcommand(WAYPOINTS_SUBCOMMAND) {
			onLiteral("reset") {
				onRun {
					Waypoints.orderedIndex = 0
					source.sendFeedback(tr("apex.command.waypoint.reset", "Reset your ordered waypoint index back to 0. If you want to delete all waypoints use /apex waypoints clear instead."))
				}
			}
			onLiteral("changeindex") {
				onArgument("from", IntegerArgumentType.integer(0)) { fromArg ->
					onArgument("to", IntegerArgumentType.integer(0)) { toArg ->
						onRun { moveIndex(source, fromArg.get(this), toArg.get(this)) }
					}
				}
			}
			onLiteral("clear") {
				onRun {
					Waypoints.waypoints = null
					source.sendFeedback(Component.translatable("apex.command.waypoint.clear"))
				}
			}
			onLiteral("toggleordered") { onRun { toggleOrdered(source) } }
			onLiteral("skip") {
				onRun {
					val route = useNonEmptyWaypoints()?.takeIf { it.isOrdered }
					if (route == null) {
						source.sendError(Component.translatable("apex.command.waypoint.skip.error"))
					} else {
						orderedIndex = (orderedIndex + 1) % route.size
						source.sendFeedback(Component.translatable("apex.command.waypoint.skip"))
					}
				}
			}
			onLiteral("remove") {
				onArgument("index", IntegerArgumentType.integer(0)) { indexArg -> onRun { removeAt(source, get(indexArg)) } }
			}
		}
	}

	fun textInvalidIndex(index: Int) = tr("apex.command.waypoint.invalid-index", "Invalid index $index provided.")

	fun textNothingToExport(): Component = tr("apex.command.waypoint.export.nowaypoints", "No waypoints to export found. Add some with /apex waypoint ~ ~ ~.")
}

fun <E> List<E>.wrappingWindow(startIndex: Int, windowSize: Int): List<E> {
	val end = startIndex + windowSize
	if (end < size) return ArrayList(subList(startIndex, end))
	val fromFront = minOf(end - size, startIndex)
	val head = subList(startIndex, size)
	return ArrayList<E>(windowSize).apply {
		addAll(head)
		addAll(this@wrappingWindow.subList(0, fromFront))
	}
}
