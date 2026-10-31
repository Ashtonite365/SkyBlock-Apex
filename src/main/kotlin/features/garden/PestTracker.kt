// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.util.render.WaypointType
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.features.plus.garden.GardenPlots
import dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.render.WorldDraw.Companion.drawInWorld

object PestTracker {

	val lockOn = PestHeads.LockOn<Int>()

	enum class Source { ENTITY, PARTICLE, TAB_PLOT, CHAT_PLOT }
	data class Target(val pos: Vec3, val source: Source, val plot: Int? = null)
	data class Selection(val entities: List<Vec3>, val guess: Target?) {

		val waypoint: Target? get() = entities.firstOrNull()?.let { Target(it, Source.ENTITY) } ?: guess
	}

	const val PEST_Y_OFFSET = 1.0

	fun pestPoint(raw: Vec3): Vec3 = raw.add(0.0, PEST_Y_OFFSET, 0.0)

	fun chooseGuess(particle: Vec3?, tabPlots: List<Int>, chatPlot: Int?, from: Vec3, plotMiddle: (Int) -> Vec3?): Target? =
		rawGuess(particle, tabPlots, chatPlot, from, plotMiddle)?.let { it.copy(pos = pestPoint(it.pos)) }

	private fun rawGuess(particle: Vec3?, tabPlots: List<Int>, chatPlot: Int?, from: Vec3, plotMiddle: (Int) -> Vec3?): Target? {
		particle?.let { return Target(it, Source.PARTICLE) }
		tabPlots.mapNotNull { id -> plotMiddle(id)?.let { Target(it, Source.TAB_PLOT, id) } }
			.minByOrNull { it.pos.distanceToSqr(from) }?.let { return it }
		chatPlot?.let { id -> plotMiddle(id)?.let { return Target(it, Source.CHAT_PLOT, id) } }
		return null
	}

	fun select(pests: List<Vec3>, from: Vec3, maxDistance: Double, all: Boolean, guess: () -> Target?): Selection {
		val inRange = pests.filter { it.distanceTo(from) <= maxDistance }.sortedBy { it.distanceToSqr(from) }
		val shown = if (all) inRange else inRange.take(1)
		return Selection(shown, if (shown.isEmpty()) guess() else null)
	}

	enum class ClearReason { NO_PESTS_LEFT, NO_INFESTED_PLOTS, OUT_OF_RANGE }

	fun clearReason(alive: Int?, infested: List<Int>, guess: Target?, from: Vec3, maxDistance: Double): ClearReason? = when {
		alive == 0 -> ClearReason.NO_PESTS_LEFT
		alive != null && infested.isEmpty() -> ClearReason.NO_INFESTED_PLOTS
		guess != null && guess.pos.distanceTo(from) > maxDistance -> ClearReason.OUT_OF_RANGE
		else -> null
	}

	fun applyClear(sel: Selection, reason: ClearReason?): Selection = if (reason == null) sel else sel.copy(guess = null)

	fun lineStart(eye: Vec3): Vec3 = eye.subtract(0.0, 0.5, 0.0)

	fun cameraRelative(start: Vec3, end: Vec3, camera: Vec3): Pair<Vec3, Vec3> = start.subtract(camera) to end.subtract(camera)

	private fun active() = (TConfig.pestTrackerLine || TConfig.pestTrackerGuessLine || TConfig.pestTrackerWaypoint) &&
		SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN

	@Subscribe
	fun onTick(event: TickEvent) {
		val level = Client.level
		if (level == null || !active()) { lockOn.clear(); return }
		val byId = level.entitiesForRendering().filter(PestHeads::isCandidate).associateBy { it.id }
		lockOn.update(byId.keys) { PestHeads.entityHeadTexture(byId.getValue(it)) }
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = lockOn.clear()

	private fun pestPositions(partialTick: Float): List<Pair<Vec3, PestHeads.PestType>> {
		val level = Client.level ?: return emptyList()
		return lockOn.pests.mapNotNull { (id, type) ->
			level.getEntity(id)?.takeIf { it.isAlive }?.let { pestPoint(it.getPosition(partialTick)) to type }
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!active()) return
		val player = Client.player ?: return
		val pt = Client.game.deltaTracker.getGameTimeDeltaPartialTick(true)
		val start = lineStart(player.getEyePosition(pt))
		val maxDistance = TConfig.pestTrackerMaxDistance.toDouble()
		val infested = GardenPests.infestedPlots()
		val alive = GardenPests.alivePests()
		val pests = pestPositions(pt)
		val raw = select(pests.map { it.first }, start, maxDistance, TConfig.pestTrackerAllPests) {
			chooseGuess(GardenPestWaypoint.currentGuess(), infested, GardenPests.lastSpawnPlot, start) {
				GardenPlots.plotMiddle(it, player.y)
			}
		}
		val reason = clearReason(alive, infested, raw.guess, start, maxDistance)

		if (reason == ClearReason.NO_PESTS_LEFT || reason == ClearReason.NO_INFESTED_PLOTS) GardenPests.lastSpawnPlot = null
		val sel = applyClear(raw, reason)
		val width = TConfig.pestTrackerThickness.toFloat()
		drawInWorld(event) {
			if (TConfig.pestTrackerLine) {
				val c = TConfig.pestTrackerLineColour.getEffectiveColourRGB()
				sel.entities.forEach { line(start, it, color = c, lineWidth = width) }
			}
			val guess = sel.guess
			if (TConfig.pestTrackerGuessLine && guess != null)
				line(start, guess.pos, color = TConfig.pestTrackerGuessColour.getEffectiveColourRGB(), lineWidth = width)
			val wp = sel.waypoint
			if (TConfig.pestTrackerWaypoint && wp != null) looks(WaypointType.PESTS) {
				val b = BlockPos.containing(wp.pos)
				box(AABB(b.x + 0.3, b.y + 1.0, b.z + 0.3, b.x + 0.7, b.y + 256.0, b.z + 0.7),
					TConfig.pestTrackerWaypointColour.getEffectiveColourRGB())
				val label = when (wp.source) {
					Source.ENTITY -> pests.firstOrNull { it.first == wp.pos }?.second?.let { "§c${it.displayName} (Pest)" } ?: "§cPest"
					Source.PARTICLE -> "§aPest Guess"
					Source.TAB_PLOT, Source.CHAT_PLOT -> "§ePest Guess (Plot ${wp.plot})"
				}
				waypoint(b, Component.literal(label))
			}
		}
	}

	fun migrate(data: JsonObject) {
		val holder = data.getAsJsonObject("garden-tools") ?: return
		val waypoint = holder.remove("pest-waypoint")
		val line = holder.remove("pest-waypoint-line")
		val wasOn = waypoint?.takeIf { it.isJsonPrimitive }?.asBoolean == true
		val lineOn = line?.takeIf { it.isJsonPrimitive }?.asBoolean == true
		if (waypoint != null && !holder.has("pest-tracker-waypoint")) holder.addProperty("pest-tracker-waypoint", wasOn)
		if ((waypoint != null || line != null) && !holder.has("pest-tracker-guess-line"))
			holder.addProperty("pest-tracker-guess-line", wasOn && lineOn)
	}

	@Subscribe
	fun onConfigFix(event: ConfigFixEvent) {
		event.on(1001, ConfigStorageClass.CONFIG) { migrate(event.data) }
	}
}
