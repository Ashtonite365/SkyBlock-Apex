// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

object LegacyMenuSwitches {

	val REMOVED: Map<String, String> = mapOf(
		"custom-menu-anvil" to "apex-menus",
		"custom-menu-brewing-stand" to "apex-menus",
		"custom-menu-city-project" to "section-city-projects",
		"custom-menu-community" to "section-community-shop",
	)

	fun apply(holder: String, saved: JsonObject): JsonObject {
		val key = REMOVED[holder] ?: return saved
		val own = (saved[key] as? JsonPrimitive)?.booleanOrNull
		val off = own == false
		if (own == null) return saved
		val out = saved.toMutableMap()
		out.remove(key)
		if (off) out["mode"] = JsonPrimitive(dev.ashtonite365.apex.features.menus.MenuMode.NORMAL.serializedName)
		return JsonObject(out)
	}
}
