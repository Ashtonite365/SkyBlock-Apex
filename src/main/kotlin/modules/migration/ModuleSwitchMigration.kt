// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules.migration

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import dev.ashtonite365.apex.modules.ModuleRow
import dev.ashtonite365.apex.modules.ModuleTable

object ModuleSwitchMigration {
	const val VERSION = 1004
	const val HOLDER = "modules"

	fun initialSwitch(module: ModuleRow, config: JsonObject, table: ModuleTable, defaults: Map<String, JsonObject> = emptyMap()): Boolean? {
		if (module.master.isNotEmpty()) return null
		if (!module.tag.gated) return true
		val saved = config[module.key] as? JsonObject ?: return false
		val tagged = table.optionRows[module.key].orEmpty().filter { it.tag.gated }.mapNotNull { it.option }
		val keys = tagged.ifEmpty { saved.keys.toList() }
		val holderDefaults = defaults[module.key]
		return keys.any { key ->
			when (val v = saved[key]) {
				is JsonPrimitive -> !v.isString && v.booleanOrNull == true
				is JsonObject, is JsonArray -> holderDefaults == null || holderDefaults[key] != v
				else -> false
			}
		}
	}

	fun migrate(config: JsonObject, table: ModuleTable = ModuleTable.DEFAULT, defaults: Map<String, JsonObject> = emptyMap()): JsonObject {
		val existing = config[HOLDER] as? JsonObject ?: JsonObject(emptyMap())
		val add = LinkedHashMap<String, JsonPrimitive>()
		for (m in table.modules) {
			if (m.key in existing) continue
			initialSwitch(m, config, table, defaults)?.let { add[m.key] = JsonPrimitive(it) }
		}
		if (add.isEmpty() && config[HOLDER] is JsonObject) return config
		return JsonObject(config + (HOLDER to JsonObject(existing + add)))
	}
}
