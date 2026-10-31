/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex Waypoints: named waypoints saved per SkyBlock island, drawn as a beam and/or box with a name and
 * distance label, managed in /apex > Miscellaneous > Render > Apex Waypoints and with /apex waypoint add|list|remove.
 * Settings live in Waypoints.TConfig (config "waypoints"), which this feature took over 's waypoint
 * editor so there is one waypoint feature: the route/import commands (/apex waypoints ...), ordered waypoints and
 * temporary chat waypoints keep working as before and share the master toggle.
 * Visual only: draws the user's own waypoints, never clicks, moves or sends anything. Rendering goes through
 * WorldDraw (box + facing text, the same paths FairySouls and KeyHighlight use on 26.3).
 */
package dev.ashtonite365.apex.features.world

import dev.ashtonite365.apex.util.render.WaypointType
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.world.Waypoints.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.tr

object ApexWaypoints {

	@Config
	object DConfig : DataHolder<ApexWaypointData>(serializer(), "apex-waypoints", ::ApexWaypointData) {

		override fun onLoad() {
			val migrated = ApexWaypointStore.migrate(data) { dev.ashtonite365.apex.Apex.logger.warn(it) }
			val seeded = ApexWaypointStore.seedDefaults(data)
			if (migrated || seeded) ApexWaypoints.pendingSave = true
		}
	}

	@Volatile
	var pendingSave = false

	@Subscribe
	fun onTick(event: dev.ashtonite365.apex.events.TickEvent) {
		if (!pendingSave) return
		pendingSave = false
		save()
	}

	private var cached: ApexWaypointStore? = null

	val store: ApexWaypointStore
		get() {
			val data = DConfig.data
			cached?.takeIf { it.data === data }?.let { return it }
			return ApexWaypointStore(data).also { cached = it }
		}

	fun currentIsland(): String = ApexWaypointStore.keyFor(SkyBlockSession.skyblockIsland?.serverMode)

	private fun repoName(k: String): String? =
		runCatching { dev.ashtonite365.apex.util.SkyBlockIsland.ofMode(k).userFriendlyName }.getOrNull()

	fun islandLabel(key: String): String = ApexWaypointStore.islandLabel(key, ::repoName)

	fun headerLabel(key: String): String = ApexWaypointStore.headerLabel(key, ::repoName)

	fun playerPos(): ApexWaypointParse.Pos? =
		Client.player?.blockPosition()?.let { ApexWaypointParse.Pos(it.x, it.y, it.z) }

	fun save() = DConfig.markDirty()

	fun add(island: String, name: String, pos: ApexWaypointParse.Pos): ApexWaypointStore.AddResult =
		store.add(island, name, pos.x, pos.y, pos.z).also { save() }

	fun remove(island: String, name: String): Boolean = store.remove(island, name).also { if (it) save() }

	fun setVisible(island: String, name: String, visible: Boolean) {
		if (store.setVisible(island, name, visible)) save()
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.enabled) return
		val waypoints = store.visibleOn(currentIsland())
		if (waypoints.isEmpty()) return
		val player = Client.player?.position() ?: return
		val colour = TConfig.markerColour.getEffectiveColourRGB()
		val style = TConfig.markerStyle
		val bg = HudStyle.backgroundColour().takeIf { it ushr 24 != 0 } ?: 0xAA202020.toInt()
		WorldDraw.drawInWorld(event, WaypointType.PERSONAL) {
			for (w in waypoints) {
				if (style.box) markerBox(AABB(w.x.toDouble(), w.y.toDouble(), w.z.toDouble(), w.x + 1.0, w.y + 1.0, w.z + 1.0), colour)
				if (style.beam) box(AABB(w.x + 0.3, w.y + 1.0, w.z + 0.3, w.x + 0.7, w.y + 256.0, w.z + 0.7), colour)
				val centre = Vec3(w.x + 0.5, w.y + 0.5, w.z + 0.5)
				text(
					Vec3(w.x + 0.5, w.y + 1.6, w.z + 0.5),
					Component.literal(w.name),
					Component.literal("§e" + ApexFormatters.distanceText(player.distanceTo(centre))),
					background = bg,
				)
			}
		}
	}

	val commandSink = object : ApexWaypointCommands.Sink<DefaultSource> {
		override fun playerPos(source: DefaultSource) = this@ApexWaypoints.playerPos()

		override fun lookedAt(source: DefaultSource): String? =
			(net.minecraft.client.Minecraft.getInstance().hitResult as? net.minecraft.world.phys.BlockHitResult)
				?.takeIf { it.type == net.minecraft.world.phys.HitResult.Type.BLOCK }?.blockPos?.let { "${it.x} ${it.y} ${it.z}" }
		override fun add(source: DefaultSource, rest: String) {
			when (val r = ApexWaypointParse.add(rest, playerPos())) {
				is ApexWaypointParse.AddRequest.Error -> source.sendError(Component.literal(r.message))
				is ApexWaypointParse.AddRequest.Ok -> {
					val island = currentIsland()
					val result = add(island, r.name, r.pos)
					val where = "${r.pos.x} ${r.pos.y} ${r.pos.z}"
					source.sendFeedback(
						if (result == ApexWaypointStore.AddResult.ADDED)
							tr("apex.command.apex-waypoint.added", "Added waypoint ${r.name} at $where on ${islandLabel(island)}.")
						else tr("apex.command.apex-waypoint.moved", "Moved waypoint ${r.name} to $where on ${islandLabel(island)}.")
					)
				}
			}
		}

		override fun list(source: DefaultSource, all: Boolean) {
			val current = currentIsland()
			val islands = if (all) store.islands(current, ::islandLabel) else listOf(current)
			var any = false
			for (island in islands) {
				val wps = store.list(island)
				if (wps.isEmpty()) continue
				any = true
				source.sendFeedback(Component.literal("§6${headerLabel(island)} §7(${wps.size})"))
				for (w in wps) source.sendFeedback(Component.literal(
					" ${if (w.visible) "§a●" else "§8○"} §f${w.name} §7${w.x} ${w.y} ${w.z}"))
			}
			if (!any) source.sendFeedback(
				if (all) tr("apex.command.apex-waypoint.none-anywhere", "You have no waypoints yet. Add one with /apex waypoint add <name> [x y z].")
				else tr("apex.command.apex-waypoint.none-here", "No waypoints on ${islandLabel(current)}. Use /apex waypoint list all to see every island.")
			)
		}

		override fun remove(source: DefaultSource, name: String) {
			val island = currentIsland()
			if (remove(island, name))
				source.sendFeedback(tr("apex.command.apex-waypoint.removed", "Removed waypoint ${name.trim()} from ${islandLabel(island)}."))
			else source.sendError(tr("apex.command.apex-waypoint.not-found", "No waypoint called ${name.trim()} on ${islandLabel(island)}."))
		}

		override fun names(source: DefaultSource): Iterable<String> = store.list(currentIsland()).map { it.name }
	}
}
