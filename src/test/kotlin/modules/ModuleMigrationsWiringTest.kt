// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.modules

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.modules.ModuleSwitches
import dev.ashtonite365.apex.modules.ModuleTable
import dev.ashtonite365.apex.modules.migration.HolderRenameMigration
import dev.ashtonite365.apex.modules.migration.ModuleMigrations
import dev.ashtonite365.apex.modules.migration.ModuleSwitchMigration
import dev.ashtonite365.apex.modules.migration.RenameTable
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs

class ModuleMigrationsWiringTest {
	private val registered = AllRegisteredConfigs.configs.map { it.name }.toSet()
	private val allIds = AllRegisteredConfigs.holders.map(ModuleMigrations::holderId).toSet()

	@Test
	fun versionCoversBothSteps() {
		assertEquals(1006, ApexConfigLoader.currentConfigVersion)
		assertEquals(1004, ModuleSwitchMigration.VERSION)
		assertEquals(1005, HolderRenameMigration.VERSION)
	}

	@Test
	fun switchHolderIsRegisteredAndMatchesTheTable() {
		assertTrue(ModuleSwitches.HOLDER in registered)
		assertEquals(ModuleTable.DEFAULT.modules.filter { it.master.isEmpty() }.map { it.key }, ModuleSwitches.switchedHere)
	}

	@Test
	fun renamesApplyOnlyOnceTheNewHolderIsRegistered() {
		val table = RenameTable.DEFAULT
		val (old, new) = table.holders.entries.first()
		assertEquals(table.holders.filterValues { it in registered && it !in table.holders.keys }.filterKeys { it !in registered },
			ModuleMigrations.activeRenames(registered, table).holders)
		val after = registered - old + new
		assertEquals(new, ModuleMigrations.activeRenames(after, table).holder(old))
	}

	@Test
	fun step1004AddsTheSwitchHolderOnce() {
		val event = ConfigFixEvent(ConfigStorageClass.CONFIG, 1004, JsonParser.parseString("{}").asJsonObject)
		ModuleMigrations.fix(event) { AllRegisteredConfigs.configs }
		val modules = event.data.getAsJsonObject(ModuleSwitches.HOLDER)
		assertEquals(ModuleSwitches.switchedHere.toSet(), modules.keySet())
		for (row in ModuleTable.DEFAULT.modules.filter { it.master.isEmpty() })
			assertEquals(!row.tag.gated, modules[row.key].asBoolean, row.key)
		val again = ConfigFixEvent(ConfigStorageClass.CONFIG, 1004, event.data.deepCopy())
		ModuleMigrations.fix(again) { AllRegisteredConfigs.configs }
		assertEquals(event.data, again.data)
		val other = ConfigFixEvent(ConfigStorageClass.STORAGE, 1004, JsonParser.parseString("{}").asJsonObject)
		ModuleMigrations.fix(other) { AllRegisteredConfigs.configs }
		assertTrue(other.data.keySet().isEmpty())
	}

	@Test
	fun everyRenameIsActiveNow() {
		val table = RenameTable.DEFAULT
		assertEquals(35, table.holders.size)
		for ((old, new) in table.holders) {
			assertTrue(new in allIds, "$new is not a registered holder")
			assertTrue(old !in allIds, "$old is still registered")
		}
		assertEquals(table.holders, ModuleMigrations.activeRenames(allIds, table).holders)
		assertTrue(allIds.none { it.startsWith("sh-") }, "sh-* holder ids left: ${allIds.filter { it.startsWith("sh-") }}")
	}

	@Test
	fun oldConfigMigratesThroughTheLoaderHook() {
		val keybind = """{"keyCode":82,"shift":true}"""
		val old = """{"sh-rift":{"ubik-quick-close":true,"bind":$keybind},"sh-combat":{"ferocity":true},
			"sh-rift-highlights":{"x":1},"slayer-overlay":{"enabled":false},"combat-extras":{"summons":true},
			"modules":{"sh-dungeon":false,"slayer-overlay":true}}"""
		val event = ConfigFixEvent(ConfigStorageClass.CONFIG, 1005, JsonParser.parseString(old).asJsonObject)
		ModuleMigrations.fix(event) { AllRegisteredConfigs.configs }
		val d = event.data
		for (k in listOf("sh-rift", "sh-combat", "sh-rift-highlights")) assertTrue(!d.has(k), k)
		assertEquals(JsonParser.parseString("""{"ubik-quick-close":true,"bind":$keybind}"""), d["rift-misc"])

		assertEquals(JsonParser.parseString("""{"ferocity":true,"summons":true}"""), d["combat-extras"])
		assertEquals(JsonParser.parseString("""{"x":1}"""), d["rift-mob-highlights"])
		assertEquals(JsonParser.parseString("""{"enabled":false}"""), d["slayer-overlay"])
		assertEquals(JsonParser.parseString("""{"dungeon-tweaks":false,"slayer-overlay":true}"""), d["modules"])
		val again = ConfigFixEvent(ConfigStorageClass.CONFIG, 1005, d.deepCopy())
		ModuleMigrations.fix(again) { AllRegisteredConfigs.configs }
		assertEquals(d, again.data)

		val storage = ConfigFixEvent(ConfigStorageClass.STORAGE, 1005, JsonParser.parseString(
			"""{"sh-rift-extras":{"n":3},"sh-todos":{"t":["a"]},"sh-ender-node":{"s":1},"sh-dragon-profit":{"s":2},
			"sh-slayer-profit":{"s":3},"unrelated":{"u":0}}""").asJsonObject)
		ModuleMigrations.fix(storage) { AllRegisteredConfigs.holders }
		assertEquals(JsonParser.parseString("""{"unrelated":{"u":0},"rift-extras":{"n":3},"todos":{"t":["a"]},
			"ender-node-profit":{"s":1},"dragon-profit":{"s":2},"slayer-profit":{"s":3}}"""), storage.data)
		val storageAgain = ConfigFixEvent(ConfigStorageClass.STORAGE, 1005, storage.data.deepCopy())
		ModuleMigrations.fix(storageAgain) { AllRegisteredConfigs.holders }
		assertEquals(storage.data, storageAgain.data)
	}

	@Test
	fun chatFilterIdsAreRenamedInCode() {

		val root = generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.first { java.io.File(it, "src/main/kotlin").isDirectory }
		val src = root.toPath().resolve("src/main/kotlin/features/plus")
		val text = java.nio.file.Files.walk(src).use { s -> s.toList().filter { it.toString().endsWith(".kt") }.joinToString("\n") { java.nio.file.Files.readString(it) } }
		for (old in listOf("sh-frozen-treasure", "sh-far-deaths")) assertTrue("\"$old\"" !in text, old)
		assertTrue("ChatFilters.Filter(\"frozen-treasure\"" in text)
		assertTrue("ChatFilters.Filter(\"far-deaths\"" in text)
	}
}
