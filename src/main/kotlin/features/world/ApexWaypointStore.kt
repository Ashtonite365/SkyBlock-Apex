/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex Waypoints: the pure part (model, per-island storage, coordinate and command parsing). No Minecraft
 * classes, so it is unit tested without the game. The game side lives in ApexWaypoints.kt (render, commands, the
 * DataHolder) and ApexWaypointManagerUi.kt (the settings manager list). Part of the single waypoint feature that
 * also owns the route/import waypoints (Waypoints.kt, WaypointRouteManager.kt, CoordListWaypoints.kt,
 * TemporaryWaypoints.kt).
 */
package dev.ashtonite365.apex.features.world

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.StringReader
import kotlin.math.floor
import kotlinx.serialization.Serializable

@Serializable
data class ApexWaypoint(
	val name: String,
	val x: Int,
	val y: Int,
	val z: Int,
	val visible: Boolean = true,
)

@Serializable
data class ApexWaypointData(
	val version: Int = 1,
	val islands: MutableMap<String, MutableList<ApexWaypoint>> = linkedMapOf(),

	val seededDefaults: MutableList<String> = mutableListOf(),
)

sealed interface ApexWaypointRow {
	data class Header(val island: String, val count: Int) : ApexWaypointRow
	data class Entry(val island: String, val waypoint: ApexWaypoint) : ApexWaypointRow
}

class ApexWaypointStore(val data: ApexWaypointData = ApexWaypointData()) {
	enum class AddResult { ADDED, UPDATED }

	var version: Int = 0
		private set

	private fun changed() { version++ }

	companion object {
		const val UNKNOWN = "unknown"
		const val MAX_NAME = 32

		const val MINING = "mining"

		val ISLAND_GROUPS: Map<String, String> = mapOf(
			"mining_2" to MINING,
			"mining_3" to MINING,
			"crystal_hollows" to MINING,
		)

		val GROUP_MEMBERS: Map<String, List<String>> = ISLAND_GROUPS.entries.groupBy({ it.value }, { it.key })

		fun keyFor(mode: String?): String {
			val m = mode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return UNKNOWN
			return ISLAND_GROUPS[m] ?: m
		}

		fun headerLabel(key: String, repoName: (String) -> String? = { null }): String {
			val members = GROUP_MEMBERS[key] ?: return islandLabel(key, repoName)
			return islandLabel(key, repoName) + " (" + members.joinToString(", ") { islandLabel(it, repoName) } + ")"
		}

		fun migrate(data: ApexWaypointData, log: (String) -> Unit = {}): Boolean {
			val moved = data.islands.keys.filter { keyFor(it) != it }
			if (moved.isEmpty()) return false
			val byTarget = moved.groupBy { keyFor(it) }
			for ((target, sources) in byTarget) {
				val merged = data.islands[target]?.toMutableList() ?: mutableListOf()
				val seen = merged.mapTo(mutableSetOf()) { it.name.lowercase() }
				for (src in sources) {
					for (w in data.islands.remove(src).orEmpty()) {
						if (seen.add(w.name.lowercase())) merged.add(w)
						else log("Apex Waypoints: dropped duplicate '${w.name}' (${w.x} ${w.y} ${w.z}) from '$src' while merging into '$target'")
					}
				}
				if (merged.isEmpty()) data.islands.remove(target) else data.islands[target] = merged
			}
			return true
		}

		const val HUB_RIFT_PORTAL_X = 45
		const val HUB_RIFT_PORTAL_Y = 152
		const val HUB_RIFT_PORTAL_Z = 73
		const val SEED_HUB_RIFT = "hub-rift"

		fun seedDefaults(data: ApexWaypointData): Boolean {
			if (SEED_HUB_RIFT in data.seededDefaults) return false
			data.seededDefaults.add(SEED_HUB_RIFT)
			val hub = data.islands.getOrPut(keyFor("hub")) { mutableListOf() }
			if (hub.none { it.name.equals("Rift", ignoreCase = true) })
				hub.add(ApexWaypoint("Rift", HUB_RIFT_PORTAL_X, HUB_RIFT_PORTAL_Y, HUB_RIFT_PORTAL_Z))
			return true
		}

		fun cleanName(name: String): String? = name.trim().replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ")
			.takeIf { it.isNotEmpty() && it.length <= MAX_NAME && '§' !in it }

		val KNOWN_NAMES = mapOf(
			"hub" to "Hub", "dynamic" to "Private Island", "dungeon_hub" to "Dungeon Hub", "winter" to "Jerry's Workshop",
			"dungeon" to "Dungeons", "kuudra" to "Kuudra", "mining_1" to "Gold Mine", "mining_2" to "Deep Caverns",
			"mining_3" to "Dwarven Mines", "crystal_hollows" to "Crystal Hollows", "mineshaft" to "Glacite Mineshaft",
			"combat_1" to "Spider's Den", "combat_3" to "The End", "crimson_isle" to "Crimson Isle",
			"farming_1" to "The Farming Islands", "garden" to "Garden", "foraging_1" to "The Park", "foraging_2" to "Galatea",
			"fishing_1" to "Backwater Bayou", "rift" to "The Rift", MINING to "Mining", UNKNOWN to "Unknown area",
		)

		fun islandLabel(key: String, repoName: (String) -> String? = { null }): String =
			KNOWN_NAMES[key] ?: repoName(key)?.takeIf { it.isNotBlank() && it != key }
			?: key.split('_', '-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
	}

	fun list(island: String): List<ApexWaypoint> = data.islands[keyFor(island)].orEmpty().toList()

	fun visibleOn(island: String): List<ApexWaypoint> = list(island).filter { it.visible }

	fun find(island: String, name: String): ApexWaypoint? =
		data.islands[keyFor(island)]?.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

	fun add(island: String, name: String, x: Int, y: Int, z: Int): AddResult {
		val clean = requireNotNull(cleanName(name)) { "Bad waypoint name '$name'" }
		val list = data.islands.getOrPut(keyFor(island)) { mutableListOf() }
		val i = list.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
		changed()
		return if (i >= 0) {
			list[i] = list[i].copy(x = x, y = y, z = z)
			AddResult.UPDATED
		} else {
			list.add(ApexWaypoint(clean, x, y, z))
			AddResult.ADDED
		}
	}

	fun remove(island: String, name: String): Boolean {
		val key = keyFor(island)
		val list = data.islands[key] ?: return false
		val removed = list.removeIf { it.name.equals(name.trim(), ignoreCase = true) }
		if (list.isEmpty()) data.islands.remove(key)
		if (removed) changed()
		return removed
	}

	fun setVisible(island: String, name: String, visible: Boolean): Boolean {
		val list = data.islands[keyFor(island)] ?: return false
		val i = list.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
		if (i < 0) return false
		list[i] = list[i].copy(visible = visible)
		changed()
		return true
	}

	fun islands(current: String?, label: (String) -> String = { islandLabel(it) }): List<String> {
		val cur = current?.let(::keyFor)
		return data.islands.filterValues { it.isNotEmpty() }.keys
			.sortedWith(compareBy<String>({ it != cur }, { label(it).lowercase() }, { it }))
	}

	fun grouped(current: String?, label: (String) -> String = { islandLabel(it) }): List<ApexWaypointRow> =
		islands(current, label).flatMap { island ->
			val wps = list(island)
			listOf<ApexWaypointRow>(ApexWaypointRow.Header(island, wps.size)) + wps.map { ApexWaypointRow.Entry(island, it) }
		}
}

object ApexWaypointParse {
	data class Pos(val x: Int, val y: Int, val z: Int)

	sealed interface AddRequest {
		data class Ok(val name: String, val pos: Pos, val fromPlayer: Boolean) : AddRequest
		data class Error(val message: String) : AddRequest
	}

	fun coord(token: String, base: Int?): Int? {
		val t = token.trim()
		if (t.startsWith("~")) {
			base ?: return null
			val rest = t.substring(1)
			if (rest.isEmpty()) return base
			return rest.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { base + floor(it).toInt() }
		}
		return t.toDoubleOrNull()?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 30_000_000 }?.let { floor(it).toInt() }
	}

	private fun isCoordToken(t: String) = t.startsWith("~") || t.toDoubleOrNull() != null

	fun coords(x: String, y: String, z: String, base: Pos? = null): Pos? {
		return Pos(coord(x, base?.x) ?: return null, coord(y, base?.y) ?: return null, coord(z, base?.z) ?: return null)
	}

	fun add(rest: String, player: Pos?): AddRequest {
		val words = rest.trim().split(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE).filter { it.isNotEmpty() }
		if (words.isEmpty()) return AddRequest.Error("Give the waypoint a name: /apex waypoint add <name> [x y z]")
		val tail = words.takeLast(3)
		if (words.size >= 4 && tail.all(::isCoordToken)) {
			val pos = coords(tail[0], tail[1], tail[2], player)
				?: return AddRequest.Error(if (player == null && tail.any { it.startsWith("~") }) "Relative coordinates need you to be in a world."
				else "Coordinates must be numbers (or ~ for your position).")
			val name = ApexWaypointStore.cleanName(words.dropLast(3).joinToString(" "))
				?: return AddRequest.Error("Names are 1-${ApexWaypointStore.MAX_NAME} characters, without colour codes.")
			return AddRequest.Ok(name, pos, fromPlayer = false)
		}
		val name = ApexWaypointStore.cleanName(words.joinToString(" "))
			?: return AddRequest.Error("Names are 1-${ApexWaypointStore.MAX_NAME} characters, without colour codes.")
		player ?: return AddRequest.Error("No position: stand somewhere or give x y z.")
		return AddRequest.Ok(name, player, fromPlayer = true)
	}
}

object ApexWaypointCommands {
	interface Sink<S> {
		fun add(source: S, rest: String)
		fun list(source: S, all: Boolean)
		fun remove(source: S, name: String)
		fun names(source: S): Iterable<String>

		fun lookedAt(source: S): String? = null
		fun playerPos(source: S): ApexWaypointParse.Pos? = null
	}

	data class Hint(val start: Int, val text: String, val tooltip: String?)

	fun addHints(arg: String, player: ApexWaypointParse.Pos?, looked: ApexWaypointParse.Pos?): List<Hint> {
		if (arg.isBlank()) return listOf(Hint(0, "[name]", "Waypoint name (spaces allowed), then optional x y z"))
		val tokStart = arg.lastIndexOf(' ') + 1
		val words = arg.substring(0, tokStart).trim().split(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE).filter { it.isNotEmpty() }
		if (words.isEmpty()) return emptyList()
		val isCoord = { t: String -> t.startsWith("~") || t.toDoubleOrNull() != null }
		val trailing = words.reversed().takeWhile(isCoord).size
		val axis = if (words.size - trailing >= 1) trailing else trailing - 1
		if (axis !in 0..2) return emptyList()
		val partial = arg.substring(tokStart)
		val label = "xyz"[axis].toString()
		val out = ArrayList<Hint>()
		fun pick(p: ApexWaypointParse.Pos) = when (axis) { 0 -> p.x; 1 -> p.y; else -> p.z }
		if (axis == 0) {
			looked?.let { out.add(Hint(tokStart, "${it.x} ${it.y} ${it.z}", "x y z: block you are looking at")) }
			out.add(Hint(tokStart, "~ ~ ~", "x y z: your position"))
		}
		looked?.let { out.add(Hint(tokStart, pick(it).toString(), label)) }
		out.add(Hint(tokStart, "~", label))
		player?.let { out.add(Hint(tokStart, pick(it).toString(), label)) }
		return out.distinctBy { it.text }.filter { it.text.startsWith(partial) }
	}

	fun removeHints(arg: String, names: Iterable<String>): List<String> =
		names.filter { it.startsWith(arg, ignoreCase = true) }.sorted()

	private fun parsePos(s: String?): ApexWaypointParse.Pos? = s?.trim()?.split(' ')?.mapNotNull { it.toIntOrNull() }
		?.takeIf { it.size == 3 }?.let { ApexWaypointParse.Pos(it[0], it[1], it[2]) }

	object Rest : ArgumentType<String> {
		override fun parse(reader: StringReader): String = reader.remaining.also { reader.cursor += it.length }
	}

	fun <S, T : ArgumentBuilder<S, T>> attach(node: T, sink: Sink<S>): T {
		node.then(LiteralArgumentBuilder.literal<S>("add")
			.then(RequiredArgumentBuilder.argument<S, String>("[name] [x y z]", Rest)
				.suggests { ctx, b ->
					val looked = runCatching { sink.lookedAt(ctx.source) }.getOrNull()
					val player = runCatching { sink.playerPos(ctx.source) }.getOrNull()
					for (h in addHints(b.remaining, player, parsePos(looked))) {
						val sb = b.createOffset(b.start + h.start)
						if (h.tooltip != null) sb.suggest(h.text, com.mojang.brigadier.LiteralMessage(h.tooltip)) else sb.suggest(h.text)
						b.add(sb)
					}
					b.buildFuture()
				}
				.executes { sink.add(it.source, it.getArgument("[name] [x y z]", String::class.java)); 1 }))
		node.then(LiteralArgumentBuilder.literal<S>("list")
			.executes { sink.list(it.source, false); 1 }
			.then(LiteralArgumentBuilder.literal<S>("all").executes { sink.list(it.source, true); 1 }))
		node.then(LiteralArgumentBuilder.literal<S>("remove")
			.then(RequiredArgumentBuilder.argument<S, String>("name", Rest)
				.suggests { ctx, b ->
					removeHints(b.remaining, sink.names(ctx.source)).forEach { b.suggest(it) }
					b.buildFuture()
				}
				.executes { sink.remove(it.source, it.getArgument("name", String::class.java)); 1 }))
		return node
	}
}
