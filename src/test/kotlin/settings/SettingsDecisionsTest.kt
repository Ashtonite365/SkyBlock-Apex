// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MergePolicy
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsPlacement

class SettingsDecisionsTest {
	private val map = MigrationMap.bundled()

	@Test
	fun nearbyCreaturesOptionsLiveInSeaCreatures() {
		for (k in listOf("nearby-creatures-rare-first", "nearby-creatures-separate")) {
			assertEquals(SettingPath.parse("fishing.sea-creatures.$k"), map["fishing-helper.$k"]!!.newPath)
			assertEquals(SettingPath.parse("fishing.sea-creatures.$k"),
				SettingsPlacement.place("fishing-helper", k, "FISHING", map) { null }.path)
		}
	}

	@Test
	fun trevorHasNoTimersCopy() {
		assertNull(map["timers-overlay.trevor"])
		assertEquals(listOf("farming"), map.entries.filter { it.oldConfig == "trevor" }.map { it.newPath.main.id }.distinct())
	}

	@Test
	fun skyMallMergeNeverTurnsASavedOnOff() {
		val rule = map.duplicates.single { it.target == SettingPath.parse("timers.mining.show-sky-mall") }
		assertEquals(MergePolicy.ANY_TRUE, rule.policy)
		val m = ConfigMigrator(map)
		val res = m.migrate(Json.parseToJsonElement("""{"mining-event-timers":{"enabled":false},"timers-overlay":{"show-sky-mall":true}}""").jsonObject)
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, SettingPath.parse("timers.mining.show-sky-mall")))
	}
}
