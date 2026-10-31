// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings.regroup

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
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
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.gui.config.storage.FreshInstallDefaults
import dev.ashtonite365.apex.settings.LayoutMoves
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingsMigrationStore
import dev.ashtonite365.apex.settings.SettingsMigrationStore.Companion.LAYOUT_KEY
import dev.ashtonite365.apex.settings.SettingsMigrationStore.Companion.LAYOUT_VERSION

class Regroup1006Test {
	@TempDir
	lateinit var tmp: Path

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
	private fun at(tree: JsonObject, path: String) = path.split('.').fold(tree as Any?) { o, k -> (o as? JsonObject)?.get(k) }

	@Test fun bundledMovesParseAndArePrefixAware() {
		val moves = LayoutMoves.bundled
		assertTrue(moves.size > 700, "layout moves bundled: ${moves.size}")
		assertEquals("misc.sounds-particles.boost-music", SettingsMigrationStore.moveOf("misc.misc-extras.boost-music", moves))
		assertEquals("dungeons.spirit-leap.spirit-leap-key-healer",
			SettingsMigrationStore.moveOf("dungeons.puzzle-solvers.spirit-leap-key-healer", moves))
		assertNull(SettingsMigrationStore.moveOf("nowhere.at-all.key", moves))
		val digits = mapOf("a.b.k-#" to "c.d.k-#")
		assertEquals("c.d.k-12", SettingsMigrationStore.moveOf("a.b.k-12", digits))
		assertNull(SettingsMigrationStore.moveOf("a.b.k-x", digits), "# takes digits only")
	}

	@Test fun relayoutMovesLeavesKeepsTheRestAndIsStampedOnce() {
		val (from, to) = LayoutMoves.bundled.entries.first { !it.key.endsWith("*") && !it.key.endsWith("#") && !it.key.startsWith("misc.") }
		val (fm, fs, fk) = from.split('.', limit = 3)
		val tree = json("""{"_firstRun":{"profile":"x"},"$fm":{"$fs":{"$fk":7}},"zz":{"kept":{"leaf":true}},
			"misc":{"misc-extras":{"boost-music":40}}}""")
		val moved = SettingsMigrationStore.relayout(tree, LayoutMoves.bundled)
		assertEquals(JsonPrimitive(7), at(moved, to))
		assertNull(at(moved, from).takeIf { from != to })
		assertEquals(JsonPrimitive(true), at(moved, "zz.kept.leaf"))
		assertEquals(JsonPrimitive(40), at(moved, "misc.sounds-particles.boost-music"))
		assertEquals(json("""{"profile":"x"}"""), moved["_firstRun"], "meta kept")
		assertEquals(JsonPrimitive(LAYOUT_VERSION), moved[LAYOUT_KEY])
		assertSame(moved, SettingsMigrationStore.relayout(moved, LayoutMoves.bundled), "a stamped tree is not moved again")
	}

	@Test fun a1005TreeEditSurvivesTheUpgrade() {
		val date = LocalDate.of(2026, 10, 29)
		val dir = tmp.resolve("settings-rewrite")
		val old = json("""{"misc-extras":{"volume-boost":true,"boost-master":150,"boost-music":40}}""")
		val s1 = SettingsMigrationStore(dir, MigrationMap.bundled(), { date })
		s1.onLoad(old)

		val tree = Json.parseToJsonElement(Files.readString(s1.treeFile)).jsonObject
		val old1005 = JsonObject(tree.filterKeys { it != LAYOUT_KEY && it != "misc" } +
			("misc" to json("""{"misc-extras":{"volume-boost":true,"boost-master":150,"boost-music":90}}""")))
		Files.writeString(s1.treeFile, old1005.toString())
		val loaded = SettingsMigrationStore(dir, MigrationMap.bundled(), { date }).onLoad(old)
		assertEquals(JsonPrimitive(90), loaded["misc-extras"]!!.jsonObject["boost-music"], "tree edit handed back after the move")
		val after = Json.parseToJsonElement(Files.readString(s1.treeFile)).jsonObject
		assertEquals(JsonPrimitive(LAYOUT_VERSION), after[LAYOUT_KEY])
		assertEquals(JsonPrimitive(90), at(after, "misc.sounds-particles.boost-music"))
		assertNull(at(after, "misc.misc-extras.boost-music"))
	}

	@Test fun holdersOnlyFolderIsNotFresh() {
		val folder = tmp.resolve("config/apex")
		assertTrue(FreshInstallDefaults.isFresh(folder), "no folder: fresh")
		Files.createDirectories(folder)
		Files.writeString(folder.resolve("hud.json"), "{}")
		assertFalse(FreshInstallDefaults.isFresh(folder))
		assertFalse(FreshInstallDefaults.shouldApply(folderExisted = true, marker = null), "upgrade: never applied")
		assertTrue(FreshInstallDefaults.shouldApply(folderExisted = false, marker = null))
		assertFalse(FreshInstallDefaults.shouldApply(folderExisted = false, marker = json("""{"profile":"quiet-hud"}""")), "marker: once only")
	}

	@Test fun markerIsStampedOnceAndKept() {
		val s = SettingsMigrationStore(tmp.resolve("settings-rewrite"), MigrationMap.bundled(), { LocalDate.of(2026, 10, 29) })
		assertFalse(s.markFirstRun(FreshInstallDefaults.PROFILE, 1006), "no tree yet")
		val old = json("""{"misc-extras":{"boost-music":40}}""")
		s.onLoad(old)
		assertTrue(s.markFirstRun(FreshInstallDefaults.PROFILE, 1006))
		assertFalse(s.markFirstRun(FreshInstallDefaults.PROFILE, 1006), "already marked")
		s.onSave(old)
		assertEquals(JsonPrimitive(FreshInstallDefaults.PROFILE), s.firstRun()?.get("profile"), "kept by save")
	}

	@Test fun freshDefaultsCarryTheApprovedLook() {
		val v = FreshInstallDefaults.VALUES.toMap()
		assertEquals(25, v["hud-style.background-opacity"])
		assertEquals("DARK", v["timer-boards.inventory-theme"])
		assertEquals(25, v["timer-boards.inventory-background-opacity"])
	}
}
