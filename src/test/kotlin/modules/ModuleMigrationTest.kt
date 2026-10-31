// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.migration.HolderRenameMigration
import dev.ashtonite365.apex.modules.migration.ModuleSwitchMigration
import dev.ashtonite365.apex.modules.migration.RenameTable
import dev.ashtonite365.apex.util.data.LegacyDataMigration

class ModuleMigrationTest {
	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
	private fun modules(o: JsonObject) = o.getValue(ModuleSwitchMigration.HOLDER).jsonObject

	private val table = ModuleTable.parse(
		ModuleTable.COLUMNS.joinToString("\t") + "\n" + listOf(
			"with-master\tmodule\tGUI\tallowed\tallowed\tA0\t\tenabled\t\t",
			"red-master\tmodule\tMining\tred\tred\tR1\t\tenabled\t\t",
			"plain\tmodule\tGUI\tallowed\tallowed\tA0\t\t\t\t",
			"mixed\tmodule\tDungeons\tallowed\tpurple\tA0\t\t\t\t",
			"mixed.blocker\toption\t\tpurple\tpurple\tP2\t\t\t\t",
			"whole-red\tmodule\tMining\tred\tred\tR1\t\t\t\t",
			"red-opts\tmodule\tMining\tred\tred\tC2\t\t\t\t",
			"red-opts.a\toption\t\tred\tred\tC2\t\t\t\t",
			"red-opts.b\toption\t\tred\tred\tC2\t\t\t\t",
			"repo\tcore\tMisc\tallowed\tallowed\t-\t\t\t\t",
		).joinToString("\n")
	)

	@Test fun masterTogglesAreTheSwitch() {
		for (cfg in listOf("{}", """{"with-master":{"enabled":true}}""", """{"with-master":{"enabled":false},"red-master":{"enabled":true}}""")) {
			val m = modules(ModuleSwitchMigration.migrate(json(cfg), table))
			assertFalse("with-master" in m || "red-master" in m, cfg)
		}
	}

	@Test fun firstRunSwitches() {
		val fresh = modules(ModuleSwitchMigration.migrate(json("{}"), table))
		assertEquals(json("""{"plain":true,"mixed":true,"whole-red":false,"red-opts":false}"""), fresh)
		assertFalse("repo" in fresh, "core holders have no switch")

		val used = modules(ModuleSwitchMigration.migrate(json("""{"whole-red":{"colour":"#fff","show":true},"red-opts":{"b":true,"other":false},"mixed":{"blocker":false}}"""), table))
		assertEquals(JsonPrimitive(true), used["whole-red"])
		assertEquals(JsonPrimitive(true), used["red-opts"])
		assertEquals(JsonPrimitive(true), used["mixed"], "a mixed module stays on: its purple option keeps its own gate")

		val untouched = modules(ModuleSwitchMigration.migrate(json("""{"whole-red":{"show":false,"name":"true"},"red-opts":{"other":true}}"""), table))
		assertEquals(JsonPrimitive(false), untouched["whole-red"], "a string \"true\" is not a saved toggle")
		assertEquals(JsonPrimitive(false), untouched["red-opts"], "only tagged options count when the module has tagged options")
	}

	@Test fun keybindOnlyRedModule() {
		val unbound = json("""{"button":"unbound","mods":0}""")
		val bound = json("""{"button":"key:71","mods":0}""")
		val defaults = mapOf("whole-red" to JsonObject(mapOf("home-key" to unbound)))
		fun sw(v: JsonObject, d: Map<String, JsonObject>) =
			modules(ModuleSwitchMigration.migrate(JsonObject(mapOf("whole-red" to JsonObject(mapOf("home-key" to v)))), table, d))["whole-red"]
		assertEquals(JsonPrimitive(false), sw(unbound, defaults))
		assertEquals(JsonPrimitive(true), sw(bound, defaults))
		assertEquals(JsonPrimitive(true), sw(unbound, emptyMap()), "without defaults it errs towards ON (today's behaviour)")
	}

	@Test fun idempotentAndKeepsUserChoices() {
		val once = ModuleSwitchMigration.migrate(json("""{"plain":{"x":1}}"""), table)
		assertSame(once, ModuleSwitchMigration.migrate(once, table))
		val chosen = json("""{"modules":{"plain":false,"whole-red":true,"gone-module":true}}""")
		val m = modules(ModuleSwitchMigration.migrate(chosen, table))
		assertEquals(JsonPrimitive(false), m["plain"])
		assertEquals(JsonPrimitive(true), m["whole-red"])
		assertEquals(JsonPrimitive(true), m["gone-module"], "unknown entries are kept")
		assertEquals(JsonPrimitive(true), m["mixed"])
	}

	@Test fun shippedTableFirstRun() {
		val fresh = modules(ModuleSwitchMigration.migrate(json("{}")))
		val t = ModuleTable.DEFAULT
		for (row in t.modules) {
			when {
				row.master.isNotEmpty() -> assertNull(fresh[row.key], row.key)
				row.tag.gated -> assertEquals(JsonPrimitive(false), fresh[row.key], row.key)
				else -> assertEquals(JsonPrimitive(true), fresh[row.key], row.key)
			}
		}
		assertTrue(t.rows.filter { !it.kind.holder || it.kind.id != "module" }.none { it.key in fresh })
	}

	private val renames = RenameTable.parse("# c\nsh-a\talpha\nsh-b\tbeta\nsh-a.old-opt\talpha.new-opt\n")

	private val keybind = """{"keyCode":71,"shift":true,"ctrl":false,"alt":false}"""

	@Test fun renamesHoldersOptionsAndSwitches() {
		val cfg = json("""{"sh-a":{"old-opt":true,"key":$keybind},"sh-b":{"x":2},"other":{"old-opt":1},"modules":{"sh-a":true,"other":false}}""")
		val out = HolderRenameMigration.migrateFolder(cfg, renames)
		assertEquals(json("""{"alpha":{"new-opt":true,"key":$keybind},"beta":{"x":2},"other":{"old-opt":1},"modules":{"alpha":true,"other":false}}"""), out)
		assertEquals(out, HolderRenameMigration.migrateFolder(out, renames), "idempotent")
	}

	@Test fun newKeyWinsAndOldEntriesAreMergedIn() {
		val out = HolderRenameMigration.migrateFolder(json("""{"alpha":{"x":1},"sh-a":{"x":9,"y":2}}"""), renames)
		assertEquals(json("""{"alpha":{"x":1,"y":2}}"""), out)
	}

	@Test fun settingsTreeAndLegacy() {
		val tree = json("""{"misc":{"sh-a":{"old-opt":true},"keep":{"v":1}},"fishing":{"sh-b":{"x":1}},"_legacy":{"sh-b":{"z":3}},"_meta":{"sh-a":1}}""")
		val out = HolderRenameMigration.migrateSettingsTree(tree, renames)
		assertEquals(json("""{"misc":{"alpha":{"new-opt":true},"keep":{"v":1}},"fishing":{"beta":{"x":1}},"_legacy":{"beta":{"z":3}},"_meta":{"sh-a":1}}"""), out)
	}

	@Test fun renameTableValidation() {
		assertThrows<IllegalArgumentException> { RenameTable.parse("a\tx\nb\tx\n") }
		assertThrows<IllegalArgumentException> { RenameTable.parse("a\tb\nb\tc\n") }
		assertThrows<IllegalArgumentException> { RenameTable.parse("a\tb\na.o\tc.o\n") }
		assertThrows<IllegalArgumentException> { RenameTable.parse("a\n") }
		assertEquals(35, RenameTable.DEFAULT.holders.size)
		assertTrue(RenameTable.DEFAULT.holders.keys.all { it.startsWith("sh-") })
	}

	@Test fun chainFromLegacyThrough1005() {
		val shipped = ModuleTable.DEFAULT
		val old = json("""{"sh-rift":{"ubik-quick-close":true,"bind":$keybind},"sh-lava-replacement":{"enabled":true},"plain-holder":{"v":"s"}}""")
		val legacy = LegacyDataMigration.migrateJsonObject(old)
		val v1004 = ModuleSwitchMigration.migrate(legacy, shipped)
		val v1005 = HolderRenameMigration.migrateFolder(v1004)
		val rift = RenameTable.DEFAULT.holder("sh-rift")
		assertEquals(json("""{"ubik-quick-close":true,"bind":$keybind}"""), v1005.getValue(rift))
		assertEquals(json("""{"v":"s"}"""), v1005.getValue("plain-holder"))
		assertFalse(v1005.keys.any { it.startsWith("sh-") })
		assertFalse(modules(v1005).keys.any { it.startsWith("sh-") })

		for ((k, v) in legacy) assertEquals(v, v1005.getValue(RenameTable.DEFAULT.holder(k)), k)
	}
}
