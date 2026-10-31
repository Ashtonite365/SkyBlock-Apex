// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.TabListAPI
import dev.ashtonite365.apex.util.bareText

object DwarvenMinesWaypoints {
	enum class LocationMode(val label: String) : StringRepresentable {
		OFF("off"),
		COMMISSIONS("commissions"),
		ALL("all"),
		;

		override fun getSerializedName(): String = label
	}

	@Config
	object TConfig : ManagedConfig("dwarven-waypoints", Category.MINING) {
		val locationWaypoints by choice("location-waypoints") { LocationMode.COMMISSIONS }
		val hideAtLocation by toggle("hide-at-location") { true }
		val emissaryWaypoints by toggle("emissary-waypoints") { true }
		val powderGhastWaypoint by toggle("powder-ghast") { true }
		val fallenStarWaypoint by toggle("fallen-star") { true }
	}

	val areas: Map<String, BlockPos> = linkedMapOf(
		"Dwarven Village" to BlockPos(-37, 199, -122),
		"Miner's Guild" to BlockPos(-74, 220, -122),
		"Fetchur" to BlockPos(85, 223, -120),
		"Palace Bridge" to BlockPos(129, 186, 8),
		"Royal Palace" to BlockPos(129, 194, 194),
		"Puzzler" to BlockPos(181, 195, 135),
		"Grand Library" to BlockPos(183, 195, 181),
		"Barracks of Heroes" to BlockPos(93, 195, 181),
		"Royal Mines" to BlockPos(178, 149, 71),
		"Cliffside Veins" to BlockPos(40, 136, 17),
		"Forge Basin" to BlockPos(0, 169, -2),
		"The Forge" to BlockPos(0, 148, -69),
		"Rampart's Quarry" to BlockPos(-106, 147, 2),
		"Far Reserve" to BlockPos(-160, 148, 17),
		"Upper Mines" to BlockPos(-123, 170, -71),
		"Goblin Burrows" to BlockPos(-138, 143, 141),
		"Great Ice Wall" to BlockPos(0, 127, 160),
		"Aristocrat Passage" to BlockPos(129, 150, 137),
		"Hanging Court" to BlockPos(91, 186, 129),
		"Divan's Gateway" to BlockPos(0, 127, 87),
		"Lava Springs" to BlockPos(57, 196, -15),
		"The Mist" to BlockPos(0, 75, 82),
	)

	val emissaries: Map<String, BlockPos> = linkedMapOf(
		"Emissary Ceanna" to BlockPos(42, 134, 22),
		"Emissary Carlton" to BlockPos(-73, 153, -11),
		"Emissary Wilson" to BlockPos(171, 150, 31),
		"Emissary Lilith" to BlockPos(58, 198, -8),
		"Emissary Frasier" to BlockPos(-132, 174, -50),
		"Emissary Eliza" to BlockPos(-37, 200, -131),
	)

	data class Announcement(val label: String, val area: String)

	private const val SHOWN_FOR_MS = 30_000L
	private val GHAST = "Find the Powder Ghast near the " to "§6Powder Ghast"
	private val STAR = "Fallen Star has crashed at " to "§5Fallen Star"

	private fun isLineBreak(c: Char) = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'

	private fun placeAfter(text: String, lead: String): String? {
		var from = text.indexOf(lead)
		while (from >= 0) {
			val start = from + lead.length
			var end = start
			while (end < text.length && !isLineBreak(text[end])) end++
			val bang = text.lastIndexOf('!', end - 1)
			if (bang > start) return text.substring(start, bang)
			from = text.indexOf(lead, from + 1)
		}
		return null
	}

	fun announcement(text: String, ghasts: Boolean, stars: Boolean): Announcement? {
		for ((kind, on) in listOf(GHAST to ghasts, STAR to stars)) {
			val place = placeAfter(text, kind.first) ?: continue
			if (on) return Announcement(kind.second, place.trim())
		}
		return null
	}

	fun parseCommissions(lines: List<String>): List<Pair<String, String>> = lines.mapNotNull { raw ->
		val line = raw.trim()
		val colon = line.lastIndexOf(':')
		if (colon < 0) null else line.take(colon).trim() to line.drop(colon + 1).trim()
	}

	fun currentCommissions(): List<Pair<String, String>> =
		parseCommissions(TabListAPI.getWidgetLines(TabListAPI.WidgetName.COMMISSIONS).map { it.bareText })

	fun scoreboardArea(): String? = ScoreboardUtil.simplifiedScoreboardLines.firstOrNull { "⏣" in it }?.substringAfter("⏣")?.trim()

	private fun simplify(name: String) = name.lowercase().replace("'", "")

	fun markers(
		commissions: List<Pair<String, String>>, here: String?, mode: LocationMode, hideHere: Boolean,
		showEmissaries: Boolean, live: Announcement?,
	): List<Pair<BlockPos, String>> = buildList {
		live?.let { a -> areas[a.area]?.let { add(it.above(15) to a.label) } }
		val hereKey = here?.let(::simplify)
		for ((name, pos) in areas) when (mode) {
			LocationMode.OFF -> {}
			LocationMode.ALL -> add(pos to "§b$name")
			LocationMode.COMMISSIONS -> {
				if (hideHere && simplify(name) == hereKey) continue
				val wanted = commissions.map { it.first }.filter { name.lowercase() in it.lowercase() }
				if (wanted.isNotEmpty()) add(pos to (if (wanted.any { "Titanium" in it }) "§f" else "§b") + name)
			}
		}
		if (showEmissaries && commissions.any { it.second == "DONE" })
			emissaries.forEach { (name, pos) -> add(pos.above(2) to "§6$name") }
	}

	private var live: Announcement? = null
	private var liveSince = 0L

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		announcement(event.bareText, TConfig.powderGhastWaypoint, TConfig.fallenStarWaypoint)?.let {
			live = it
			liveSince = System.currentTimeMillis()
		}
	}

	@Subscribe
	fun onWorldReady(event: WorldReadyEvent) {
		live = null
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.DWARVEN_MINES) return
		val showing = live?.takeIf { System.currentTimeMillis() - liveSince < SHOWN_FOR_MS }
		val points = markers(currentCommissions(), scoreboardArea(), TConfig.locationWaypoints, TConfig.hideAtLocation,
			TConfig.emissaryWaypoints, showing)
		WorldDraw.drawInWorld(event, WaypointType.DWARVEN) {
			points.forEach { (pos, label) -> waypoint(pos, Component.literal(label)) }
		}
	}
}
