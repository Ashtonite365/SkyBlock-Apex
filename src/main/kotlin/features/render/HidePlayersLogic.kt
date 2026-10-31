/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Hide Players, the pure decision logic (no Minecraft types, unit tested). The game side
 * ([HidePlayers]) feeds it the facts about one player entity and the current settings snapshot.
 * Original implementation.
 */
package dev.ashtonite365.apex.features.render

import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import net.minecraft.util.StringRepresentable

object HidePlayersLogic {

	const val NEVER = 0

	const val REAL = 1

	const val PARTY = 2

	const val LISTED = 4

	fun classify(
		uuid: UUID,
		isLocal: Boolean,
		inTabList: Boolean,
		nameLower: () -> String,
		partyUuids: Set<UUID>,
		partyNames: Set<String>,
		whitelist: Set<String>,
	): Int {
		if (isLocal || uuid.version() != 4 || !inTabList) return NEVER
		var flags = REAL
		var name: String? = null
		if (uuid in partyUuids) flags = flags or PARTY
		else if (partyNames.isNotEmpty()) { name = nameLower(); if (name in partyNames) flags = flags or PARTY }
		if (whitelist.isNotEmpty() && (name ?: nameLower()) in whitelist) flags = flags or LISTED
		return flags
	}

	enum class DungeonMode(val id: String) : StringRepresentable {

		EXCEPT_PARTY("except-party"),

		SHOW_ALL("show-all"),

		SAME("same"),
		;

		override fun getSerializedName(): String = id
	}

	data class Settings(
		val enabled: Boolean = false,
		val keepParty: Boolean = true,

		val keepWithin: Int = 0,
		val dungeonMode: DungeonMode = DungeonMode.EXCEPT_PARTY,

		val screenshot: Boolean = false,

		val screenshotHideSelf: Boolean = false,
	) {
		val keepWithinSq: Double = keepWithin.toDouble() * keepWithin

		val active: Boolean get() = enabled || screenshot
	}

	fun shouldHide(flags: Int, distanceSq: Double, s: Settings, inDungeonArea: Boolean): Boolean {
		if (flags and REAL == 0) return false
		if (s.screenshot) return true
		if (!s.enabled) return false
		val party = flags and PARTY != 0
		if (inDungeonArea) when (s.dungeonMode) {
			DungeonMode.SHOW_ALL -> return false
			DungeonMode.EXCEPT_PARTY -> if (party) return false
			DungeonMode.SAME -> {}
		}
		if (party && s.keepParty) return false
		if (flags and LISTED != 0) return false
		if (s.keepWithin > 0 && distanceSq <= s.keepWithinSq) return false
		return true
	}

	fun hideLocal(s: Settings, thirdPerson: Boolean): Boolean = s.screenshot && s.screenshotHideSelf && thirdPerson

	private val SEPARATORS = Regex("[\\s,;|]+")

	fun parseWhitelist(text: String): Set<String> =
		text.split(SEPARATORS).asSequence().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()

	class DecisionCache {
		private val map = HashMap<UUID, Int>()
		var computes = 0
			private set
		var generation = 0
			private set

		inline fun flags(uuid: UUID, compute: () -> Int): Int = get(uuid) ?: compute().also { put(uuid, it) }

		fun get(uuid: UUID): Int? = map[uuid]
		fun put(uuid: UUID, flags: Int) { computes++; map[uuid] = flags }
		val size: Int get() = map.size

		fun invalidate() {
			if (map.isNotEmpty()) map.clear()
			generation++
		}
	}

	enum class Action { ON, OFF, TOGGLE, SCREENSHOT }

	val COMMAND_WORDS = listOf("on", "off", "toggle", "screenshot")

	fun parseAction(arg: String?): Action? = when (arg?.trim()?.lowercase()) {
		null, "", "toggle" -> Action.TOGGLE
		"on", "enable", "true" -> Action.ON
		"off", "disable", "false" -> Action.OFF
		"screenshot" -> Action.SCREENSHOT
		else -> null
	}

	fun applyAction(current: Boolean, action: Action): Boolean = when (action) {
		Action.ON -> true
		Action.OFF -> false
		Action.TOGGLE -> !current
		Action.SCREENSHOT -> current
	}

	fun feedback(enabled: Boolean): String = "§e[SkyBlock Apex] §fHide Players: " + if (enabled) "§aON" else "§cOFF"
	fun screenshotFeedback(on: Boolean): String =
		"§e[SkyBlock Apex] §fScreenshot mode: " + if (on) "§aON §7(all other players hidden)" else "§cOFF"

	const val OLD_CONFIG = "misc-render"
	const val OLD_TOGGLE = "hide-far-players"
	const val OLD_DISTANCE = "far-player-distance"
	const val OLD_DEFAULT_DISTANCE = 24
	const val MAX_KEEP_WITHIN = 64

	fun foldOldFarPlayers(saved: JsonObject, all: JsonObject): JsonObject {
		if ("enabled" in saved) return saved
		val old = all[OLD_CONFIG] as? JsonObject ?: return saved
		if ((old[OLD_TOGGLE] as? JsonPrimitive)?.content != "true") return saved
		val distance = ((old[OLD_DISTANCE] as? JsonPrimitive)?.intOrNull ?: OLD_DEFAULT_DISTANCE).coerceIn(1, MAX_KEEP_WITHIN)
		val out = saved.toMutableMap()
		out["enabled"] = JsonPrimitive(true)
		out["keep-within"] = JsonPrimitive(distance)
		out["keep-party"] = JsonPrimitive(false)
		return JsonObject(out)
	}
}
