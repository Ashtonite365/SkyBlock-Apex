// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.data

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.util.data.LegacyDataMigration

class LegacyDataMigrationTest {
	@TempDir
	lateinit var game: Path

	private fun write(rel: String, text: String) = game.resolve(rel).also { Files.createDirectories(it.parent); Files.writeString(it, text) }
	private fun read(rel: String) = Files.readString(game.resolve(rel))
	private fun json(rel: String) = Json.parseToJsonElement(read(rel)).jsonObject
	private fun snapshot(dir: String): Map<String, String> = Files.walk(game.resolve(dir)).use { s ->
		s.filter { Files.isRegularFile(it) }.toList().associate { game.resolve(dir).relativize(it).toString() to Files.readString(it) }
	}

	private val keybind = """{"keyCode":75,"shift":false,"ctrl":true,"alt":false}"""

	private fun legacyInstall() {
		write("config/firmament/config.version", "1000")
		write("config/firmament/skyhanni-misc.json", """{"skyhanni-misc":{"charge-bottle":true,"timer":12}}""")
		write("config/firmament/pet-keybinds.json", """{"pet-keybinds":{"unequip":$keybind,"pet-1":$keybind}}""")
		write("config/firmament/hud-style.json", """{"hud-style":{"bar-style":"skyhanni_glossy","panel-style":"firmament","timer-style":"rounded_glow"}}""")
		write("config/firmament/stat-bars.json", """{"stat-bars":{"health-style":"sba_classic","mana-style":"odin_accent","xp-style":"global"}}""")
		write("config/firmament/storage-overlay.json", """{"storage-overlay":{"texture-style":"NEU_DARK"}}""")
		write("config/firmament/storage/apex-waypoints.json", """{"apex-waypoints":{"version":1}}""")
		write("config/firmament/profiles/00000000-0000-0000-0000-000000000000/neu-misc.json", """{"neu-misc":{"x":1}}""")
		write("config/firmament/settings-rewrite/settings-v2.json", """{"version":2,"misc":{"skyhanni-misc":{"charge-bottle":true}},"_legacy":{"skyhanni-misc.old":1}}""")
		write("config/firmament/broken.json", "{not json")
		write(".firmament/repo-extracted/items/HYPERION.json", """{"itemid":"minecraft:iron_sword"}""")
		write(".firmament/loaded-repo-sha.txt", "abc")
		write(".firmament/skyhanni-repo/SkyHanni-REPO-main.zip", "zipbytes")
	}

	@Test
	fun copiesConfigAndDataWithRenamedKeys() {
		legacyInstall()
		LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		LegacyDataMigration.migrate(game.resolve(".firmament"), game.resolve(".apex"))

		assertEquals("1000", read("config/apex/config.version"))

		assertFalse(Files.exists(game.resolve("config/apex/skyhanni-misc.json")))
		assertEquals(true, json("config/apex/plus-misc.json")["plus-misc"]!!.jsonObject["charge-bottle"]!!.jsonPrimitive.content.toBoolean())

		val kb = json("config/apex/pet-keybinds.json")["pet-keybinds"]!!.jsonObject
		assertEquals(Json.parseToJsonElement(keybind), kb["unequip"])
		assertEquals(Json.parseToJsonElement(keybind), kb["pet-1"])

		val hud = json("config/apex/hud-style.json")["hud-style"]!!.jsonObject
		assertEquals(JsonPrimitive("glossy"), hud["bar-style"])
		assertEquals(JsonPrimitive("standard"), hud["panel-style"])
		assertEquals(JsonPrimitive("rounded_glow"), hud["timer-style"])
		val bars = json("config/apex/stat-bars.json")["stat-bars"]!!.jsonObject
		assertEquals(JsonPrimitive("classic"), bars["health-style"])
		assertEquals(JsonPrimitive("accent"), bars["mana-style"])
		assertEquals(JsonPrimitive("global"), bars["xp-style"])
		assertEquals(JsonPrimitive("CLASSIC_DARK"), json("config/apex/storage-overlay.json")["storage-overlay"]!!.jsonObject["texture-style"])

		assertTrue(Files.exists(game.resolve("config/apex/storage/apex-waypoints.json")))
		assertTrue(Files.exists(game.resolve("config/apex/profiles/00000000-0000-0000-0000-000000000000/core-misc.json")))
		val tree = json("config/apex/settings-rewrite/settings-v2.json")
		assertTrue("plus-misc" in tree["misc"]!!.jsonObject)
		assertTrue("plus-misc.old" in tree["_legacy"]!!.jsonObject)

		assertEquals("{not json", read("config/apex/broken.json"))

		assertEquals("""{"itemid":"minecraft:iron_sword"}""", Json.parseToJsonElement(read(".apex/repo-extracted/items/HYPERION.json")).toString())
		assertEquals("abc", read(".apex/loaded-repo-sha.txt"))
		assertEquals("zipbytes", read(".apex/plus-repo/community-repo-main.zip"))
	}

	@Test
	fun neverTouchesTheOldFolders() {
		legacyInstall()
		val oldConfig = snapshot("config/firmament")
		val oldData = snapshot(".firmament")
		LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		LegacyDataMigration.migrate(game.resolve(".firmament"), game.resolve(".apex"))
		assertEquals(oldConfig, snapshot("config/firmament"))
		assertEquals(oldData, snapshot(".firmament"))
	}

	@Test
	fun neverOverwritesExistingApexFiles() {
		legacyInstall()
		write("config/apex/pet-keybinds.json", """{"pet-keybinds":{}}""")
		write(".apex/loaded-repo-sha.txt", "new")
		val r = LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		LegacyDataMigration.migrate(game.resolve(".firmament"), game.resolve(".apex"))
		assertEquals("""{"pet-keybinds":{}}""", read("config/apex/pet-keybinds.json"))
		assertEquals("new", read(".apex/loaded-repo-sha.txt"))
		assertTrue(r.skippedExisting.any { it.endsWith("pet-keybinds.json") })
		assertTrue(Files.exists(game.resolve("config/apex/plus-misc.json")))
	}

	@Test
	fun runsOnlyOnce() {
		legacyInstall()
		assertTrue(LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex")).ran)

		Files.delete(game.resolve("config/apex/plus-misc.json"))
		val second = LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		assertFalse(second.ran)
		assertFalse(Files.exists(game.resolve("config/apex/plus-misc.json")))
		assertTrue(Files.exists(game.resolve("config/apex/" + LegacyDataMigration.MARKER)))
	}

	@Test
	fun noLegacyFolderIsANoOp() {
		val r = LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		assertFalse(r.ran)
		assertFalse(Files.exists(game.resolve("config/apex")))
	}

	@Test
	fun loadTimeMigrationKeepsNewKeysAndIsIdempotent() {
		val obj = Json.parseToJsonElement("""{"skyhanni-misc":{"a":1},"plus-misc":{"a":2},"neu-misc":{"b":3},"hud-style":{"bar-style":"sba_pill"}}""").jsonObject
		val once = LegacyDataMigration.migrateJsonObject(obj)
		assertEquals(Json.parseToJsonElement("""{"plus-misc":{"a":2},"hud-style":{"bar-style":"pill"},"core-misc":{"b":3}}"""), once)
		assertEquals(once, LegacyDataMigration.migrateJsonObject(once))

		val other = Json.parseToJsonElement("""{"chat":{"mode":"firmament"}}""").jsonObject
		assertEquals(other, LegacyDataMigration.migrateJsonObject(other))
	}

	@Test
	fun renamedHoldersMatchTheBundledSettingsMap() {
		val map = LegacyDataMigration::class.java.getResourceAsStream("/assets/apex/settings/migration-map.tsv")!!.reader().readText()
		for ((old, new) in LegacyDataMigration.KEY_RENAMES) {
			assertTrue(map.lineSequence().any { it.startsWith("$new.") }, "holder $new is in the settings map")
			assertFalse(map.lineSequence().any { it.startsWith("$old.") }, "old holder $old is gone from the settings map")
		}
	}

	@Test
	fun removedFarTerrainIslandSavesAreDropped() {

		assertFalse(LegacyDataMigration.KEY_RENAMES.values.any { it == "far-terrain" })
		val map = dev.ashtonite365.apex.settings.MigrationMap.bundled()
		for (k in listOf("island-saves", "bundled-hub-terrain", "revisit-fix")) {
			assertEquals(null, map.resolve("far-terrain.$k"))
			assertTrue(dev.ashtonite365.apex.settings.ConfigMigrator.isRemoved("far-terrain.$k"))
		}
		assertFalse(dev.ashtonite365.apex.settings.ConfigMigrator.isRemoved("apex-far-terrain.enabled"))
	}

	@Test
	fun runOnceUsesTheGameFolder() {
		legacyInstall()
		LegacyDataMigration.runFor(game)
		assertTrue(Files.exists(game.resolve("config/apex/plus-misc.json")))
		assertTrue(Files.exists(game.resolve(".apex/repo-extracted/items/HYPERION.json")))
	}

	@Test
	fun keybindsOfRenamedHoldersSurvive() {
		write("config/firmament/skyhanni-garden-warps.json", """{"skyhanni-garden-warps":{"barn-key":$keybind,"home-key":$keybind}}""")
		write("config/firmament/settings-rewrite/settings-v2.json", """{"version":2,"farming":{"skyhanni-garden-warps":{"barn-key":$keybind},"skyhanni-plots":{"toggle-borders-key":$keybind}}}""")
		LegacyDataMigration.migrate(game.resolve("config/firmament"), game.resolve("config/apex"))
		val kb = Json.parseToJsonElement(keybind)
		val holder = json("config/apex/plus-garden-warps.json")["plus-garden-warps"]!!.jsonObject
		assertEquals(kb, holder["barn-key"])
		assertEquals(kb, holder["home-key"])
		val farming = json("config/apex/settings-rewrite/settings-v2.json")["farming"]!!.jsonObject
		assertEquals(kb, farming["plus-garden-warps"]!!.jsonObject["barn-key"])
		assertEquals(kb, farming["plus-plots"]!!.jsonObject["toggle-borders-key"])
	}
}
