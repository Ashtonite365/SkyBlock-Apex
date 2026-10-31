package dev.ashtonite365.apex.test.features.experiments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.experiments.ExperimentTableMigration
import dev.ashtonite365.apex.features.experiments.ExperimentationTable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertSame
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.util.data.ManagedConfig

class ExperimentAutoSolveTest {

	@Test
	fun noAutoSolveOptions() {
		val names = ExperimentationTable.allOptions.keys
		assertTrue(names.none { it.contains("auto-solve") }, names.toString())
		assertTrue(ExperimentTableMigration.MAP.keys.none { it.contains("auto-solve") })
	}

	@Test
	fun oneSectionInMiscellaneous() {
		assertEquals(ManagedConfig.Category.MISC, ExperimentationTable.category)
		assertEquals("experimentation-table", ExperimentationTable.name)

		assertTrue(ExperimentationTable.sortedOptions.all { SettingsTabs.mainSectionOf(it) == MainSection.EXPERIMENTATION })
		assertNull(SettingsTabs.map["apex-chronomatron.enabled"]); assertNull(SettingsTabs.map["experiment-solvers.enabled"])
		assertTrue(ManagedConfig.Category.entries.none { it.name.startsWith("EXPERIMENT") })

		val ids = ExperimentationTable.sortedOptions.map { it.propertyName }
		assertEquals(listOf("apex-menus", "enabled", "block-wrong-clicks"), ids.take(3))
		val group = ids.drop(3).map { it.substringBefore('-') }
		assertEquals(listOf("superpairs", "chronomatron", "ultrasequencer"), group.distinct())
		assertTrue(ids.containsAll(listOf("chronomatron-debug-log", "ultrasequencer-debug-log", "chronomatron-current-colour")))
		assertTrue(ids.none { it.endsWith("-enabled") || it.endsWith("-block-wrong-clicks") })

		assertEquals(ids.drop(3).toSet() - ExperimentTableMigration.NEW_SWITCHES, ExperimentTableMigration.MAP.keys)
		assertEquals(ExperimentTableMigration.NEW_SWITCHES, ids.filter { it == "apex-menus" || it.endsWith("-apex-menus") }.toSet())
		assertEquals(setOf("enabled", "block-wrong-clicks"), ExperimentTableMigration.ANY_OF.keys)
	}

	@Test
	fun migratesOldSectionsOnce() {
		val old = buildJsonObject {
			putJsonObject("apex-chronomatron") { put("enabled", true); put("auto-solve", true); put("show-next", false); put("debug-log", false); put("current-colour", "0:255:1:2:3") }
			putJsonObject("experiment-solvers") { put("enabled", true); put("block-wrong-clicks", true); put("sequencer-numbers", false); put("ultra-rare-book-alert", false) }
			putJsonObject("other") { put("x", 1) }
		}
		val m = ExperimentTableMigration.apply(old)["experimentation-table"]!!.jsonObject
		assertEquals(JsonPrimitive(true), m["enabled"]); assertNull(m["chronomatron-auto-solve"])
		assertEquals(JsonPrimitive(false), m["chronomatron-show-next"])
		assertEquals(JsonPrimitive(false), m["chronomatron-debug-log"]); assertEquals(JsonPrimitive("0:255:1:2:3"), m["chronomatron-current-colour"])
		assertEquals(JsonPrimitive(true), m["block-wrong-clicks"])
		assertEquals(JsonPrimitive(false), m["ultrasequencer-numbers"]); assertEquals(JsonPrimitive(false), m["superpairs-ultra-rare-book-alert"])
		assertNull(m["ultrasequencer-auto-solve"])

		val again = buildJsonObject {
			putJsonObject("experimentation-table") { put("chronomatron-show-next", true) }
			putJsonObject("apex-chronomatron") { put("show-next", false) }
		}
		assertEquals(JsonPrimitive(true), ExperimentTableMigration.apply(again)["experimentation-table"]!!.jsonObject["chronomatron-show-next"])
		val nothing = buildJsonObject { put("x", 1) }
		assertSame(nothing, ExperimentTableMigration.apply(nothing))
	}

	@Test
	fun sharedTogglesAreOnIfAnyOldValueWasOn() {
		fun m(b: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = ExperimentTableMigration.apply(buildJsonObject(b))["experimentation-table"]!!.jsonObject
		val a = m {
			putJsonObject("experimentation-table") {
				put("superpairs-enabled", false); put("ultrasequencer-enabled", true); put("chronomatron-enabled", false)
				put("superpairs-block-wrong-clicks", false); put("chronomatron-block-wrong-clicks", true)
			}
		}
		assertEquals(JsonPrimitive(true), a["enabled"]); assertEquals(JsonPrimitive(true), a["block-wrong-clicks"])
		ExperimentTableMigration.DROP.forEach { assertNull(a[it], it) }
		val b = m { putJsonObject("apex-chronomatron") { put("enabled", false) }; putJsonObject("experiment-solvers") { put("enabled", false); put("block-wrong-clicks", false) } }
		assertEquals(JsonPrimitive(false), b["enabled"]); assertEquals(JsonPrimitive(false), b["block-wrong-clicks"])
		val c = m { putJsonObject("apex-chronomatron") { put("block-wrong-clicks", true) }; putJsonObject("experiment-solvers") { put("block-wrong-clicks", false) } }
		assertEquals(JsonPrimitive(true), c["block-wrong-clicks"]); assertNull(c["enabled"])

		val d = m { putJsonObject("experimentation-table") { put("enabled", false); put("chronomatron-enabled", true) } }
		assertEquals(JsonPrimitive(false), d["enabled"]); assertNull(d["chronomatron-enabled"])
	}
}
