// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

object UnbindWardrobeKeysMigration {
	const val FLAG = "unbind-default-keys-version"
	const val VERSION = 1

	private val DIGIT_KEYS = listOf(
		InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5,
		InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9,
	)

	val OLD_DEFAULTS: Map<String, Map<String, Int>> = mapOf(
		"wardrobe-keybinds" to mapOf(
			"change-page" to InputConstants.KEY_RETURN,
			"next-page" to InputConstants.KEY_D,
			"previous-page" to InputConstants.KEY_A,
		) + DIGIT_KEYS.withIndex().associate { (i, k) -> "slot-${i + 1}" to k },
		"loadout-keybinds" to mapOf(
			"next-page" to InputConstants.KEY_RIGHT,
			"previous-page" to InputConstants.KEY_LEFT,
		),
	)

	val PORTED_DEFAULTS: Map<String, Map<String, Int>> = mapOf(
		"wardrobe-keybinds" to (1..9).associate { "slot-$it" to InputConstants.KEY_0 + it },
	)

	private fun decode(e: JsonElement): SavedKeyBinding? = runCatching { Json.decodeFromJsonElement<SavedKeyBinding>(e) }.getOrNull()

	fun apply(saved: JsonObject, oldDefaults: Map<String, Int>, portedDefaults: Map<String, Int> = emptyMap()): JsonObject {
		if ((saved[FLAG]?.jsonPrimitive?.intOrNull ?: 0) >= VERSION) return saved
		val out = saved.toMutableMap()
		val unbound = Json.encodeToJsonElement(SavedKeyBinding.unbound())
		for (id in oldDefaults.keys + portedDefaults.keys) {
			val current = saved[id]?.let(::decode) ?: continue
			val old = listOfNotNull(oldDefaults[id], portedDefaults[id]).map { SavedKeyBinding.keyWithoutMods(it) }
			if (current in old) out[id] = unbound
		}
		out[FLAG] = JsonPrimitive(VERSION)
		return JsonObject(out)
	}

	fun migrate(name: String, saved: JsonObject): JsonObject =
		OLD_DEFAULTS[name]?.let { apply(saved, it, PORTED_DEFAULTS[name].orEmpty()) } ?: saved
	fun flag() = mapOf(FLAG to JsonPrimitive(VERSION))
}
