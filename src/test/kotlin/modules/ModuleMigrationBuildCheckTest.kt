// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.migration.ModuleSwitchMigration
import dev.ashtonite365.apex.modules.migration.RenameTable
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs
import dev.ashtonite365.apex.util.data.LegacyDataMigration

class ModuleMigrationBuildCheckTest {
	private val table = ModuleTable.DEFAULT
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val byName by lazy { configs.associateBy { it.name } }

	@Suppress("UNCHECKED_CAST")
	@Test fun firstRunOnDefaultsMatchesTodaysBehaviour() {
		val defaults = configs.associate { c ->
			c.name to JsonObject(c.sortedOptions.mapNotNull { o ->
				val opt = o as ManagedOption<Any>
				opt.handler.encode(opt.default())?.let { opt.propertyName to it }
			}.toMap())
		}
		val m = ModuleSwitchMigration.migrate(JsonObject(defaults), table, defaults).getValue(ModuleSwitchMigration.HOLDER).jsonObject
		for (row in table.modules.filter { it.master.isEmpty() })
			assertEquals(JsonPrimitive(!row.tag.gated), m[row.key], row.key)
	}

	@Test fun renamesAreConsistentWithTheCode() {
		val renames = RenameTable.DEFAULT

		val registered = AllRegisteredConfigs.holders.map(dev.ashtonite365.apex.modules.migration.ModuleMigrations::holderId).toSet()
		for ((old, new) in renames.holders) {
			assertTrue((old in registered) != (new in registered), "exactly one of $old / $new must be a registered holder")
		}
		val legacy = LegacyDataMigration.KEY_RENAMES.keys + LegacyDataMigration.KEY_RENAMES.values
		assertEquals(emptyList<String>(), renames.holders.values.filter { it in legacy }, "new ids reused from the legacy renames")
		assertTrue(ModuleSwitchMigration.HOLDER !in renames.holders.values)
		for (opt in renames.options.keys) assertTrue(byName[opt.substringBefore('.')]?.allOptions?.containsKey(opt.substringAfter('.')) == true, opt)
	}
}
