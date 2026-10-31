/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Fetchur and Puzzler helpers, HOTM level-as-amount, Crystal Hollows death waypoint, Glacite gemstone
 * collector waypoints (locations from the item repo, constants/glacite_tunnel_waypoints.json). Waypoints use the
 * shared style.
 */
package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.time.Duration.Companion.minutes
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object MiningSolvers {
	@Config
	object TConfig : ManagedConfig("mining-solvers", Category.MINING) {
		val fetchur by toggle("fetchur") { true }
		val puzzler by riskyToggle("puzzler", RiskLevel.GREY_AREA)
		val hotmLevels by toggle("hotm-levels") { true }
		val hotmCosts by toggle("hotm-costs") { true }
		val deathWaypoint by toggle("death-waypoint") { true }
		val gemstoneWaypoints by riskyToggle("gemstone-waypoints", RiskLevel.GREY_AREA)
	}

	private const val FETCHUR_SAYS = "[NPC] Fetchur:"

	private val fetchurWants: Map<String, String> = linkedMapOf(
		"yellow and see through" to "20x Yellow Stained Glass",
		"circular and sometimes moves" to "1x Compass",
		"expensive minerals" to "20x Mithril",
		"useful during celebrations" to "1x Firework Rocket",
		"hot and gives energy" to "1x Cheap, Decent or Black Coffee",
		"tall and can be opened" to "1x Wooden Door or Iron Door",
		"brown and fluffy" to "1x Rabbit's Foot",
		"explosive but more than usual" to "1x Superboom TNT",
		"wearable and grows" to "1x Pumpkin",
		"shiny and makes sparks" to "1x Flint and Steel",
		"green and some dudes trade stuff for it" to "1x Emerald",
		"red and soft" to "50x Red Wool",
	)

	fun fetchurAnswer(line: String): String? {
		if (!line.startsWith(FETCHUR_SAYS)) return null
		val said = line.lowercase().filterNot { it == '\'' }
		for ((clue, item) in fetchurWants) if (clue in said) return item
		return null
	}

	private const val PUZZLER_SAYS = "[NPC] Puzzler: "
	private val puzzlerOrigin = BlockPos(181, 195, 135)

	private val arrowStep = mapOf('▲' to (0 to 1), '▼' to (0 to -1), '◀' to (1 to 0), '▶' to (-1 to 0))

	fun puzzlerArrows(line: String): String? {
		if (!line.startsWith(PUZZLER_SAYS)) return null
		val arrows = line.substring(PUZZLER_SAYS.length)
		return arrows.takeIf { it.isNotEmpty() && it.all(arrowStep::containsKey) }
	}

	fun puzzlerTarget(arrows: String, start: BlockPos = puzzlerOrigin): BlockPos {
		var dx = 0
		var dz = 0
		for (a in arrows) arrowStep[a]?.let { (x, z) -> dx += x; dz += z }
		return start.offset(dx, 0, dz)
	}

	private var puzzlerSolution: BlockPos? = null
	private var deathPos: BlockPos? = null
	private var deathAt = TimeMark.farPast()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText.trim()
		if (TConfig.fetchur) fetchurAnswer(line)?.let {
			Client.showChat(Component.literal("§e[SkyBlock Apex] Fetchur wants §f$it"))
		}
		if (TConfig.puzzler) puzzlerArrows(line)?.let { puzzlerSolution = puzzlerTarget(it) }
		if (line.startsWith("[NPC] Puzzler: Nice")) puzzlerSolution = null
		if (TConfig.deathWaypoint && SkyBlockSession.skyblockIsland == SkyBlockIsland.CRYSTAL_HOLLOWS && line.startsWith("☠ You")) {
			deathPos = Client.player?.blockPosition(); deathAt = TimeMark.now()
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { puzzlerSolution = null; deathPos = null }

	fun parseWaypoint(s: String): BlockPos? = s.split(':').mapNotNull { it.trim().toIntOrNull() }
		.takeIf { it.size == 3 }?.let { BlockPos(it[0], it[1], it[2]) }

	fun gemstoneWaypoints(json: com.google.gson.JsonObject?): List<Pair<String, BlockPos>> =
		json?.entrySet()?.flatMap { (name, el) ->
			val obj = el.asJsonObject
			val title = obj.get("title")?.asString ?: name
			obj.getAsJsonArray("waypoints")?.mapNotNull { w -> parseWaypoint(w.asString)?.let { title to it } } ?: emptyList()
		} ?: emptyList()

	@Subscribe
	fun onRenderGemstones(event: WorldRenderLastEvent) {
		if (!TConfig.gemstoneWaypoints || SkyBlockSession.skyblockIsland != SkyBlockIsland.DWARVEN_MINES) return
		val points = gemstoneWaypoints(dev.ashtonite365.apex.repo.RepoConstants.get("glacite_tunnel_waypoints"))
		if (points.isEmpty()) return
		WorldDraw.drawInWorld(event, WaypointType.GLACITE) {
			for ((title, pos) in points) waypoint(pos, Component.literal(title))
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val p = puzzlerSolution?.takeIf { TConfig.puzzler && SkyBlockSession.skyblockIsland == SkyBlockIsland.DWARVEN_MINES }
		val d = deathPos?.takeIf { TConfig.deathWaypoint && deathAt.passedTime() < 10.minutes }
		if (p == null && d == null) return
		WorldDraw.drawInWorld(event) {
			p?.let { looks(WaypointType.MINING_SOLVERS) { waypoint(it, Component.literal("§aPuzzler")) } }
			d?.let { looks(WaypointType.CRYSTAL_HOLLOWS) { waypoint(it, Component.literal("§cYour death")) } }
		}
	}

	private val LEVEL = Regex("^Level (\\d+)(?:/\\d+)?$")

	fun perkLevel(lore: List<String>, name: String): Int? =
		lore.firstNotNullOfOrNull { LEVEL.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
			?: Regex("^Tier (\\d+)$").matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull()

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		if (!TConfig.hotmLevels) return
		val title = Client.screenTitle ?: return
		if (!title.startsWith("Heart of the Mountain") && !title.startsWith("Heart of the Forest")) return
		if (event.slot.container is Inventory) return
		val stack = event.slot.item
		if (stack.isEmpty) return
		val level = perkLevel(stack.view().loreAccordingToNbt.map { it.bareText }, stack.hoverName.bareText) ?: return
		val text = level.toString()
		event.context.nextStratum()
		event.context.text(Client.font, text, event.slot.x + 17 - Client.font.width(text), event.slot.y + 9, -1, true)
	}
}
