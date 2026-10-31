// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.data

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.gui.hud.editor.HudEditors
import dev.ashtonite365.apex.util.data.LegacyDataMigration

class KeyMappingMigrationTest {
	@TempDir
	lateinit var game: Path

	private val old = "key.jarvis.open-gui-editor"
	private val new = "key.apex.open-gui-editor"

	@Test fun renameTargetsTheRegisteredKey() {
		assertEquals(new, HudEditors.OPEN_KEY_NAME)
		assertEquals(mapOf(old to new), LegacyDataMigration.KEY_MAPPING_RENAMES)
	}

	@Test fun oldBindingMovesToTheNewName() {
		val lines = listOf("version:4671", "key_key.jump:key.keyboard.space", "key_$old:key.keyboard.f8", "soundCategory_master:1.0")
		assertEquals(listOf("version:4671", "key_key.jump:key.keyboard.space", "key_$new:key.keyboard.f8", "soundCategory_master:1.0"),
			LegacyDataMigration.migrateOptionsLines(lines))
	}

	@Test fun unboundAndModifierValuesAreKeptAsWritten() {
		assertEquals(listOf("key_$new:key.keyboard.unknown"), LegacyDataMigration.migrateOptionsLines(listOf("key_$old:key.keyboard.unknown")))
		assertEquals(listOf("key_$new:key.mouse.4:SHIFT"), LegacyDataMigration.migrateOptionsLines(listOf("key_$old:key.mouse.4:SHIFT")))
	}

	@Test fun existingNewBindingWins() {
		val lines = listOf("key_$new:key.keyboard.f9", "key_$old:key.keyboard.f8")
		assertNull(LegacyDataMigration.migrateOptionsLines(lines), "nothing to change when the new name is already saved")
	}

	@Test fun nothingToMigrate() {
		assertNull(LegacyDataMigration.migrateOptionsLines(listOf("key_key.jump:key.keyboard.space", "fov:0.0")))
		assertNull(LegacyDataMigration.migrateOptionsLines(emptyList()))
		assertNull(LegacyDataMigration.migrateOptionsLines(listOf("lastServer:$old")), "only key_ lines are bindings")
	}

	@Test fun fileIsRewrittenInPlaceOnce() {
		val options = game.resolve("options.txt")
		Files.write(options, listOf("version:4671", "key_$old:key.keyboard.right.shift", "key_key.chat:key.keyboard.t"))
		assertTrue(LegacyDataMigration.migrateOptionsFile(game))
		assertEquals(listOf("version:4671", "key_$new:key.keyboard.right.shift", "key_key.chat:key.keyboard.t"), Files.readAllLines(options))
		assertFalse(Files.exists(game.resolve("options.txt.apex-migrating")), "no temp file left behind")
		assertFalse(LegacyDataMigration.migrateOptionsFile(game), "second launch changes nothing")
	}

	@Test fun missingOptionsFileIsANoOp() {
		assertFalse(LegacyDataMigration.migrateOptionsFile(game))
		assertFalse(Files.exists(game.resolve("options.txt")))
	}
}
