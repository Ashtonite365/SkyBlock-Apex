// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules.migration

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.util.data.GenericConfig
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig

object ModuleMigrations {

	fun defaults(holders: List<ManagedConfig>): Map<String, JsonObject> =
		holders.associate { h ->
			h.name to JsonObject(h.sortedOptions.mapNotNull { o -> optionDefault(o)?.let { o.propertyName to it } }.toMap())
		}

	@Suppress("UNCHECKED_CAST")
	private fun optionDefault(o: dev.ashtonite365.apex.gui.config.ManagedOption<*>): JsonElement? {
		val opt = o as dev.ashtonite365.apex.gui.config.ManagedOption<Any>
		return runCatching { opt.handler.encode(opt.default()) }.getOrNull()
	}

	fun activeRenames(registered: Set<String>, table: RenameTable = RenameTable.DEFAULT): RenameTable {
		val holders = table.holders.filter { (old, new) -> new in registered && old !in registered }
		val options = table.options.filter { (old, new) ->
			val h = old.substringBefore('.')
			new.substringBefore('.').let { it in registered && it == (holders[h] ?: h) }
		}
		return RenameTable(holders, options)
	}

	fun holderId(holder: IDataHolder<*>): String = when (holder) {
		is ManagedConfig -> holder.name
		is GenericConfig<*> -> holder.prefix
		is ProfileKeyedConfig<*> -> holder.prefix
	}

	private fun registeredHolders(): List<IDataHolder<*>> = ApexConfigLoader.allConfigs

	@Subscribe
	fun onConfigFix(event: ConfigFixEvent) = fix(event, ::registeredHolders)

	fun fix(event: ConfigFixEvent, holders: () -> List<IDataHolder<*>>) {
		event.on(ModuleSwitchMigration.VERSION, ConfigStorageClass.CONFIG) {
			val defaults = defaults(holders().filterIsInstance<ManagedConfig>())
			transform { ModuleSwitchMigration.migrate(it.jsonObject, ModuleTable.DEFAULT, defaults) }
		}
		for (storageClass in ConfigStorageClass.entries)
			event.on(HolderRenameMigration.VERSION, storageClass) {
				val renames = activeRenames(holders().map(::holderId).toSet())
				transform { HolderRenameMigration.migrateFolder(it.jsonObject, renames) }
			}
	}
}
