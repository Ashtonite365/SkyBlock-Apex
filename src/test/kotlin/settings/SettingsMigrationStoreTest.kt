// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsMigrationStore
import dev.ashtonite365.apex.settings.SettingsRegistry

class SettingsMigrationStoreTest {
	@TempDir
	lateinit var tmp: Path

	private val date = LocalDate.of(2026, 10, 16)
	private val bundled = MigrationMap.bundled()
	private fun store(map: MigrationMap = bundled) = SettingsMigrationStore(tmp.resolve("settings-rewrite"), map, { date })
	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
	private fun read(p: Path) = Json.parseToJsonElement(Files.readString(p)).jsonObject
	private fun snapshot(): Map<String, String> = Files.walk(tmp).use { s ->
		s.filter { Files.isRegularFile(it) }.toList().associate { tmp.relativize(it).toString() to Files.readString(it) }
	}

	private val old = json(
		"""{"chat-tabs":{"enabled":false},
		"misc-extras":{"volume-boost":true,"boost-master":150,"boost-music":40},
		"puzzle-solvers":{"spirit-leap-keybinds":false,"spirit-leap-key-1":{"keyCode":71},"spirit-leap-key-healer":{"keyCode":72}},
		"gap-extras":{"thunder-bottle":false},"plus-misc":{"charge-bottle":true},
		"removed-holder":{"whatever":[1,2,3]}}"""
	)

	private fun leaves(o: JsonObject, prefix: String = ""): Map<String, JsonElement> = o.flatMap { (k, v) ->
		if (v is JsonObject && v.isNotEmpty()) leaves(v, "$prefix$k.").toList() else listOf("$prefix$k" to v)
	}.toMap()

	@Test
	fun firstLoadWritesBackupBeforeTreeAndKeepsOldInput() {
		val s = store()
		assertEquals(old, s.onLoad(old))
		val backup = tmp.resolve("settings-rewrite").resolve(SettingsMigrationStore.backupName(date))
		assertEquals(listOf(backup, s.treeFile), s.lastWrites, "backup must be written first")
		assertEquals("config.pre-rewrite-2026-10-16.json", backup.fileName.toString())
		assertEquals(old, read(backup))
		assertFalse(Files.list(tmp).use { l -> l.anyMatch { it.toString().endsWith(".json") } }, "nothing may land in the old config folder")
	}

	@Test
	fun backupIsNeverOverwritten() {
		val s = store()
		s.onLoad(old)
		val backup = tmp.resolve("settings-rewrite").resolve(SettingsMigrationStore.backupName(date))
		Files.delete(s.treeFile)
		s.onLoad(json("""{"chat-tabs":{"enabled":true}}"""))
		assertEquals(old, read(backup))
	}

	@Test
	fun noValueIsLost() {
		val s = store()
		s.onLoad(old)
		val tree = read(s.treeFile)
		assertEquals(old, tree[ConfigMigrator.LEGACY_KEY])
		val m = ConfigMigrator(bundled)
		assertEquals(JsonPrimitive(false), m.lookup(tree, SettingPath.parse("misc.chat-window.chat-tabs.enabled")))
		assertEquals(JsonPrimitive(true), m.lookup(tree, SettingPath.parse("misc.item-helpers.thunder-bottle")))

		val legacy = leaves(tree[ConfigMigrator.LEGACY_KEY]!!.jsonObject)
		leaves(old).forEach { (k, v) -> assertEquals(v, legacy[k], k) }

		s.onSave(JsonObject(old - "removed-holder"))
		assertEquals(old["removed-holder"], read(s.treeFile)[ConfigMigrator.LEGACY_KEY]!!.jsonObject["removed-holder"])

		s.onSave(json("""{"chat-tabs":{"enabled":true}}"""))
		val t2 = read(s.treeFile)
		assertEquals(JsonPrimitive(true), m.lookup(t2, SettingPath.parse("misc.chat-window.chat-tabs.enabled")))
		assertEquals(JsonPrimitive(150), m.lookup(t2, SettingPath.parse("misc.sounds-particles.boost-master")))
	}

	@Test
	fun idempotentLoadAndSave() {
		val s = store()
		val loaded1 = s.onLoad(old)
		val after1 = snapshot()
		val loaded2 = s.onLoad(loaded1)
		assertEquals(loaded1, loaded2)
		assertEquals(after1, snapshot())
		assertTrue(s.lastWrites.isEmpty())
		s.onSave(loaded2)
		s.onSave(loaded2)
		assertTrue(s.lastWrites.isEmpty(), "second save must not write")
		assertEquals(after1, snapshot())

		assertEquals(JsonPrimitive(false), loaded2["gap-extras"]!!.jsonObject["thunder-bottle"])

		assertEquals(loaded1, store().onLoad(loaded1))
		assertEquals(after1, snapshot())
	}

	@Test
	fun treeEditsAreHandedBackToOldHolders() {
		val s = store()
		s.onLoad(old)
		val tree = read(s.treeFile)
		val misc = tree["misc"]!!.jsonObject
		val edited = JsonObject(tree + ("misc" to JsonObject(misc + ("sounds-particles" to JsonObject(
			misc["sounds-particles"]!!.jsonObject + ("boost-music" to JsonPrimitive(90))
		)))))
		Files.writeString(s.treeFile, edited.toString())
		val loaded = s.onLoad(old)
		assertEquals(JsonPrimitive(90), loaded["misc-extras"]!!.jsonObject["boost-music"])
		assertEquals(JsonPrimitive(150), loaded["misc-extras"]!!.jsonObject["boost-master"])
	}

	@Test
	fun brokenTreeIsKeptAsideAndRemigrated() {
		val s = store()
		Files.createDirectories(s.dir)
		Files.writeString(s.treeFile, "{not json")
		assertEquals(old, s.onLoad(old))
		assertEquals(old, read(s.treeFile)[ConfigMigrator.LEGACY_KEY])
		assertTrue(Files.list(s.dir).use { l -> l.anyMatch { it.fileName.toString().startsWith("settings-v2.json.broken-") } })
	}

	@Test
	fun prefixRulesForRuntimeKeys() {

		assertEquals("misc.sounds-particles.boost-master", bundled.resolve("misc-extras.boost-master").toString())
		assertEquals("misc.sounds-particles.boost-weather", bundled.resolve("misc-extras.boost-weather").toString())
		assertEquals("dungeons.spirit-leap.spirit-leap-key-1", bundled.resolve("puzzle-solvers.spirit-leap-key-1").toString())
		assertEquals("dungeons.spirit-leap.spirit-leap-key-healer", bundled.resolve("puzzle-solvers.spirit-leap-key-healer").toString())

		assertEquals("misc.sounds-particles.volume-boost", bundled.resolve("misc-extras.volume-boost").toString())
		assertEquals("dungeons.spirit-leap.spirit-leap-keybinds", bundled.resolve("puzzle-solvers.spirit-leap-keybinds").toString())
		assertNull(bundled.resolve("misc-extras.boost-"))
		assertEquals(listOf("puzzle-solvers.spirit-leap-key-3"), bundled.oldKeysFor(SettingPath.parse("dungeons.spirit-leap.spirit-leap-key-3")))

		val res = ConfigMigrator(bundled).migrate(old)
		assertEquals(listOf("removed-holder.whatever"), res.unmapped)
		assertEquals(json("""{"keyCode":72}"""), ConfigMigrator(bundled).lookup(res.tree, SettingPath.parse("dungeons.spirit-leap.spirit-leap-key-healer")))

		assertThrows<IllegalArgumentException> { MigrationMap.parse("a.bx\tmisc.a.bx\ttoggle\na.b*\tmisc.a.b*\ttoggle") }
		assertThrows<IllegalArgumentException> { MigrationMap.parse("a.b*\tmisc.a.b\ttoggle") }
	}

	@Test
	fun h1KeybindPrefixRules() {

		(1..9).forEach { assertEquals("gui.wardrobe-keybinds.slot-$it", bundled.resolve("wardrobe-keybinds.slot-$it").toString()) }
		assertEquals(listOf("wardrobe-keybinds.slot-4"), bundled.oldKeysFor(SettingPath.parse("gui.wardrobe-keybinds.slot-4")))
		assertEquals("gui.wardrobe-keybinds.next-page", bundled.resolve("wardrobe-keybinds.next-page").toString())
		val res = ConfigMigrator(bundled).migrate(json("""{"wardrobe-keybinds":{"slot-1":{"keyCode":49},"slot-9":{"keyCode":57}}}"""))
		assertTrue(res.unmapped.isEmpty())
		assertEquals(json("""{"keyCode":57}"""), ConfigMigrator(bundled).lookup(res.tree, SettingPath.parse("gui.wardrobe-keybinds.slot-9")))

		SettingsRegistry.default(bundled).get(MainSection.GUI, "wardrobe-keybinds")!!

		(1..12).forEach {
			assertEquals("gui.loadout-keybinds.loadout-$it", bundled.resolve("loadout-keybinds.loadout-$it").toString())
			assertEquals(listOf("loadout-keybinds.loadout-$it"), bundled.oldKeysFor(SettingPath.parse("gui.loadout-keybinds.loadout-$it")))
		}
		(1..9).forEach {
			assertEquals("gui.pet-keybinds.pet-$it", bundled.resolve("pet-keybinds.pet-$it").toString())
			assertEquals(listOf("pet-keybinds.pet-$it"), bundled.oldKeysFor(SettingPath.parse("gui.pet-keybinds.pet-$it")))
		}

		assertEquals("gui.loadout-keybinds.loadout-keybinds", bundled.resolve("loadout-keybinds.loadout-keybinds").toString())
		assertEquals("gui.pet-keybinds.pet-keybinds", bundled.resolve("pet-keybinds.pet-keybinds").toString())
		assertNull(bundled.resolve("pet-keybinds.pet-x1"))
		assertNull(bundled.resolve("pet-keybinds.pet-"))
		assertEquals(emptyList<String>(), bundled.oldKeysFor(SettingPath.parse("gui.pet-keybinds.pet-abc")))
		val m2 = ConfigMigrator(bundled).migrate(json("""{"pet-keybinds":{"pet-keybinds":true,"pet-3":{"keyCode":51}},"loadout-keybinds":{"loadout-12":{"keyCode":70}}}"""))
		assertTrue(m2.unmapped.isEmpty())
		assertEquals(json("""{"keyCode":70}"""), ConfigMigrator(bundled).lookup(m2.tree, SettingPath.parse("gui.loadout-keybinds.loadout-12")))
		assertEquals(JsonPrimitive(true), ConfigMigrator(bundled).lookup(m2.tree, SettingPath.parse("gui.pet-keybinds.pet-keybinds")))

		MigrationMap.parse("a.pet-keybinds\tmisc.a.pet-keybinds\ttoggle\na.pet-#\tmisc.a.pet-#\ttoggle")
		assertThrows<IllegalArgumentException> { MigrationMap.parse("a.pet-2\tmisc.a.pet-2\ttoggle\na.pet-#\tmisc.a.pet-#\ttoggle") }
		assertThrows<IllegalArgumentException> { MigrationMap.parse("a.pet-keybinds\tmisc.a.pet-keybinds\ttoggle\na.pet-*\tmisc.a.pet-*\ttoggle") }
		assertThrows<IllegalArgumentException> { MigrationMap.parse("a.pet-#\tmisc.a.pet-*\ttoggle") }
	}

	@Test
	fun miscChunkSubsectionsAreGroupedAndClaimedOnce() {

		val groups = setOf("general", "chat", "chat-commands", "combat", "rift", "slayers", "events", "crimson-isle", "foraging", "render", "misc", "")
		val misc = bundled.entries.filter { it.newPath.main == MainSection.MISC }
		assertTrue(misc.size > 400, "misc has ${misc.size} entries")
		misc.forEach { assertTrue(it.group in groups, "${it.oldKey} has group '${it.group}'") }
		val reg = SettingsRegistry.default(bundled)
		reg.subsections(MainSection.MISC).forEach { assertTrue(it.group in groups, "${it.path} group '${it.group}'") }
		misc.forEach { reg.claim(it.newPath, it.oldConfig) }

		assertEquals(listOf("gap-extras.thunder-bottle", "plus-misc.charge-bottle"), bundled.oldKeysFor(SettingPath.parse("misc.item-helpers.thunder-bottle")))
		assertEquals(listOf("diana.rare-mob-alert", "gap-misc.inquisitor-alert"), bundled.oldKeysFor(SettingPath.parse("misc.diana.rare-mob-alert")))
	}
}
