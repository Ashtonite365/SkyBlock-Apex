// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.RiskDefaults
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry

class SettingsRewriteTest {
	private val small = MigrationMap.parse(
		"""
		# comment
		timers-overlay.show-golden-fish	timers.fishing.show-golden-fish	toggle	fishing
		fishing-helper.golden-fish-timer	fishing.fishing-helper.golden-fish-timer	toggle
		kuudra-info.hp-hud	dungeons.kuudra-info.hp-hud	position	kuudra
		chat-tabs.enabled	misc.chat-tabs.enabled	toggle	chat
		""".trimIndent(),
		"fishing-helper.golden-fish-timer,timers-overlay.show-golden-fish\ttimers.fishing.show-golden-fish\tANY_TRUE",
	)

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

	@Test
	fun sectionsAreInSpecOrderWithMiscLast() {
		assertEquals(listOf("timers", "gui", "hud", "waypoints", "dungeons", "secrets", "mining", "farming", "fishing", "custom_menus", "experimentation", "misc"),
			MainSection.entries.map { it.id })
	}

	@Test
	fun pathParsing() {
		val p = SettingPath.parse("dungeons.kuudra-info.hp-hud")
		assertEquals(MainSection.DUNGEONS, p.main)
		assertEquals("kuudra-info", p.subsection)
		assertEquals("dungeons.kuudra-info.hp-hud", p.toString())
		assertEquals("boss.enabled", SettingPath.parse("timers.dungeon.boss.enabled").key)
		assertThrows<IllegalArgumentException> { SettingPath.parse("nope.x.y") }
		assertThrows<IllegalArgumentException> { SettingPath.parse("timers.x") }
	}

	@Test
	fun registryHomesLinksAndDuplicateClaims() {
		val reg = SettingsRegistry.default(small)
		SettingsRegistry.SPEC_TIMERS.forEach { assertNotNull(reg.get(MainSection.TIMERS, it)) }
		val kuudra = reg.get(MainSection.DUNGEONS, "kuudra-info")!!
		assertEquals("kuudra", kuudra.group)
		reg.link(MainSection.TIMERS, kuudra)
		assertEquals(listOf(kuudra), reg.linked(MainSection.TIMERS))
		assertThrows<IllegalArgumentException> { reg.link(MainSection.DUNGEONS, kuudra) }
		val path = SettingPath.parse("timers.fishing.show-golden-fish")
		reg.claim(path, "timers-overlay")
		reg.claim(path, "timers-overlay")
		assertThrows<IllegalArgumentException> { reg.claim(path, "fishing-helper") }
		assertThrows<IllegalArgumentException> { reg.register(MainSection.DUNGEONS, "kuudra-info", "other") }
	}

	@Test
	fun riskDefaults() {
		assertTrue(RiskDefaults.defaultFor(MainSection.CUSTOM_MENUS, isAutoClick = true))
		MainSection.entries.filter { it != MainSection.CUSTOM_MENUS }
			.forEach { assertFalse(RiskDefaults.defaultFor(it, isAutoClick = true)) }
		assertFalse(RiskDefaults.defaultFor(MainSection.CUSTOM_MENUS, isAutoClick = false))
	}

	@Test
	fun migrationKeepsEveryValue() {
		val old = json("""{"kuudra-info":{"hp-hud":{"x":3,"y":4}},"chat-tabs":{"enabled":false,"brand-new":7},"unknown":{"a":1}}""")
		val res = ConfigMigrator(small).migrate(old)
		val m = ConfigMigrator(small)
		assertEquals(json("""{"x":3,"y":4}"""), m.lookup(res.tree, SettingPath.parse("dungeons.kuudra-info.hp-hud")))
		assertEquals(JsonPrimitive(false), m.lookup(res.tree, SettingPath.parse("misc.chat-tabs.enabled")))
		assertEquals(listOf("chat-tabs.brand-new", "unknown.a"), res.unmapped)
		assertEquals(old, res.tree[ConfigMigrator.LEGACY_KEY])
		assertEquals(JsonPrimitive(ConfigMigrator.VERSION), res.tree[ConfigMigrator.VERSION_KEY])
	}

	@Test
	fun migrationIsIdempotent() {
		val m = ConfigMigrator(small)
		val once = m.migrate(json("""{"chat-tabs":{"enabled":true}}""")).tree
		val twice = m.migrate(once)
		assertTrue(twice.alreadyMigrated)
		assertEquals(once, twice.tree)
	}

	@Test
	fun duplicatesMergeIntoOneSetting() {
		val m = ConfigMigrator(small)
		val res = m.migrate(json("""{"fishing-helper":{"golden-fish-timer":true},"timers-overlay":{"show-golden-fish":false}}"""))
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, SettingPath.parse("timers.fishing.show-golden-fish")))
		assertEquals(null, m.lookup(res.tree, SettingPath.parse("fishing.fishing-helper.golden-fish-timer")))
		assertEquals(1, res.conflicts.size)
		assertTrue(res.unmapped.isEmpty())
	}

	@Test
	fun bundledMapIsValid() {
		val map = MigrationMap.bundled()
		assertTrue(map.entries.size > 1000, "map has ${map.entries.size} entries")
		assertEquals(18, map.duplicates.size)

		for (rule in map.duplicates) {
			map.entries.filter { it.newPath == rule.target }
				.forEach { assertTrue(it.oldKey in rule.oldKeys, "duplicate target ${rule.target} is also the row of ${it.oldKey}") }
		}
		val known = map.entries.map { it.oldKey }.toSet() + map.duplicates.flatMap { it.oldKeys }
		for (rule in map.derived) rule.inputs.forEach { assertTrue(it.oldKey in known || map.resolve(it.oldKey) != null, "derived input ${it.oldKey}") }
		val reg = SettingsRegistry.default(map)
		MainSection.entries.forEach { assertTrue(reg.subsections(it).isNotEmpty(), "$it has no subsections") }

		assertNull(map["custom-menu-bank.return-to-main"]); assertNull(map["custom-menu-bank.chain-presets"])
		assertEquals(MainSection.CUSTOM_MENUS, map.entries.first { it.oldKey.startsWith("custom-menu-bank.") }.newPath.main)

		val old = JsonObject(map.entries.groupBy { it.oldConfig }.mapValues { (_, es) ->
			JsonObject(es.associate { it.oldOption to JsonPrimitive(it.oldKey) })
		})
		val res = ConfigMigrator(map).migrate(old)
		assertTrue(res.unmapped.isEmpty())
		val dupKeys = map.duplicates.flatMap { it.oldKeys }.toSet()
		for (e in map.entries) if (e.oldKey !in dupKeys && map.derived.none { it.target == e.newPath })
			assertEquals(JsonPrimitive(e.oldKey), ConfigMigrator(map).lookup(res.tree, e.newPath), e.oldKey)
	}
}
