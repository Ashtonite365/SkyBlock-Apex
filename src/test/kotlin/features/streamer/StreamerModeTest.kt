// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.streamer

import java.nio.file.Path
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.features.streamer.StreamerMode
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingsMigrationStore

class StreamerModeTest {
	@TempDir
	lateinit var tmp: Path

	private val obf = StreamerMode.filterLobbyNames("mini73A")

	@Test
	fun unsplitIdsUnchangedBehaviour() {
		assertEquals(listOf("Sending to server ${obf}R..."), StreamerMode.filterSegments(listOf("Sending to server mini73AR...")))
		assertEquals(listOf(" Server: ", StreamerMode.filterLobbyNames("mini90J")), StreamerMode.filterSegments(listOf(" Server: ", "mini90J")))
		assertEquals(listOf("no ids here"), StreamerMode.filterSegments(listOf("no ids here")))
	}

	@Test
	fun idSplitAcrossSegmentsIsHidden() {

		val sidebar = StreamerMode.filterSegments(listOf("10/18/26 m7", "", "3AR"))
		assertEquals("10/18/26 ${StreamerMode.filterLobbyNames("m73A")}R", sidebar.joinToString(""))
		assertFalse(sidebar.joinToString("").contains("m73A"))
		val chat = StreamerMode.filterSegments(listOf("Sending to server ", "mini", "73AR..."))
		assertEquals("Sending to server ${obf}R...", chat.joinToString(""))
		assertEquals(3, chat.size)
		assertNotEquals("m73A", StreamerMode.filterLobbyNames("m73A"))
	}

	@Test
	fun enabledSurvivesSaveAndReload() {
		val c = StreamerMode.TConfig
		c.explicitDefaultLoad()
		assertFalse(c.enabled)
		c.loadFrom(Unit, Json.parseToJsonElement("""{"streamer-mode":{"enabled":true,"chat":true,"scoreboard":true}}""").jsonObject)
		assertTrue(c.enabled)
		val saved = c.saveTo(Unit)
		assertTrue(saved["streamer-mode"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)

		val store = SettingsMigrationStore(tmp.resolve("settings-rewrite"), MigrationMap.bundled(), { LocalDate.of(2026, 10, 18) })
		store.onSave(saved)
		val loaded = store.onLoad(saved)
		c.explicitDefaultLoad()
		c.loadFrom(Unit, loaded)
		assertTrue(c.enabled)
		c.explicitDefaultLoad()
	}
}
