// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules.migration

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class RenameTable(val holders: Map<String, String>, val options: Map<String, String>) {
	init {
		val targets = holders.values.groupBy { it }.filterValues { it.size > 1 }.keys
		require(targets.isEmpty()) { "two holders renamed to the same id: $targets" }
		val clash = holders.values.toSet() intersect holders.keys
		require(clash.isEmpty()) { "rename chains are not supported (old id is also a new id): $clash" }
		for ((old, new) in options) {
			require('.' in old && '.' in new) { "option rename $old -> $new needs holder.option on both sides" }
			val h = old.substringBefore('.')
			require(new.substringBefore('.') == (holders[h] ?: h)) { "option rename $old -> $new moves the option to another holder" }
		}
	}

	fun holder(old: String) = holders[old] ?: old

	fun option(oldHolder: String, option: String) = options["$oldHolder.$option"]?.substringAfter('.') ?: option

	companion object {
		const val RESOURCE = "/assets/apex/modules/renames.tsv"

		fun parse(text: String): RenameTable {
			val holders = LinkedHashMap<String, String>()
			val options = LinkedHashMap<String, String>()
			for ((n, line) in text.lineSequence().withIndex()) {
				if (line.isBlank() || line.startsWith("#")) continue
				val f = line.split('\t')
				require(f.size == 2 && f.all { it.isNotEmpty() }) { "renames line ${n + 1}: want old<TAB>new" }
				val into = if ('.' in f[0]) options else holders
				require(into.put(f[0], f[1]) == null) { "renames line ${n + 1}: ${f[0]} renamed twice" }
			}
			return RenameTable(holders, options)
		}

		fun load(): RenameTable = parse(
			RenameTable::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
				?: error("missing $RESOURCE")
		)

		val DEFAULT: RenameTable by lazy { load() }
	}
}

object HolderRenameMigration {
	const val VERSION = 1005
	const val LEGACY_KEY = "_legacy"

	private fun merge(newer: JsonElement?, older: JsonElement): JsonElement =
		if (newer is JsonObject && older is JsonObject) JsonObject(older + newer) else newer ?: older

	private fun renameKeys(obj: JsonObject, rename: (String) -> String): JsonObject {
		val out = LinkedHashMap<String, JsonElement>()
		for ((k, v) in obj) if (rename(k) == k) out[k] = v
		for ((k, v) in obj) {
			val nk = rename(k)
			if (nk != k) out[nk] = merge(out[nk], v)
		}
		return JsonObject(out)
	}

	private fun renameHolder(oldName: String, value: JsonElement, table: RenameTable): JsonElement =
		if (value is JsonObject && oldName != ModuleSwitchMigration.HOLDER) renameKeys(value) { table.option(oldName, it) } else value

	fun migrateFolder(folder: JsonObject, table: RenameTable = RenameTable.DEFAULT): JsonObject {
		val inner = JsonObject(folder.mapValues { (k, v) -> renameHolder(k, v, table) })
		val out = renameKeys(inner, table::holder)
		val modules = out[ModuleSwitchMigration.HOLDER] as? JsonObject ?: return out
		return JsonObject(out + (ModuleSwitchMigration.HOLDER to renameKeys(modules, table::holder)))
	}

	fun migrateSettingsTree(tree: JsonObject, table: RenameTable = RenameTable.DEFAULT): JsonObject =
		JsonObject(tree.mapValues { (main, subs) ->
			when {
				main == LEGACY_KEY && subs is JsonObject -> migrateFolder(subs, table)
				main.startsWith("_") || subs !is JsonObject -> subs
				else -> renameKeys(JsonObject(subs.mapValues { (k, v) -> renameHolder(k, v, table) }), table::holder)
			}
		})
}
