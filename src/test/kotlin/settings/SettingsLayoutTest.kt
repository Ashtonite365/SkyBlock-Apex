// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.settings.LayoutItem
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsLayout
import dev.ashtonite365.apex.settings.SettingsPlacement

class SettingsLayoutTest {
	private val map = MigrationMap.parse(
		"""
		test-angler.cast-timer	timers.fishing.test-angler.cast-timer	toggle	fishing
		test-angler.bite-alert	fishing.test-angler.bite-alert	toggle
		chat-tabs.enabled	misc.chat-tabs.enabled	toggle	chat
		kuudra-splits.enabled	dungeons.kuudra-splits.enabled	toggle	kuudra
		dungeon-map.enabled	dungeons.dungeon-map.enabled	toggle
		""".trimIndent()
	)

	@Test
	fun exactRowWins() {
		val p = SettingsPlacement.place("test-angler", "cast-timer", "FISHING", map) { null }
		assertEquals(SettingPath.parse("timers.fishing.test-angler.cast-timer"), p.path)
	}

	@Test
	fun dynamicKeyFollowsItsConfig() {
		val sib = SettingsPlacement.siblings(map)
		val p = SettingsPlacement.place("chat-tabs", "tab-3", "CHAT", map) { sib[it] }
		assertEquals(MainSection.MISC, p.path.main)
		assertEquals("chat-tabs", p.path.subsection)
		assertEquals("chat", p.group)
	}

	@Test
	fun unknownConfigUsesOldCategoryRule() {
		assertEquals(MainSection.GUI, SettingsPlacement.place("new-thing", "x", "AUCTION_BAZAAR", map) { null }.path.main)
		val misc = SettingsPlacement.place("new-chat", "x", "CHAT", map) { null }
		assertEquals(MainSection.MISC, misc.path.main); assertEquals("chat", misc.group)
		val menu = SettingsPlacement.place("custom-menu-foo", "mode", "CUSTOM_MENUS", map) { null }
		assertEquals("custom_menus.foo", menu.subsectionPath)
		assertEquals("custom_menus.general", SettingsPlacement.place("custom-menus", "automation", "CUSTOM_MENUS", map) { null }.subsectionPath)
	}

	@Test
	fun pagesGroupsAndLinks() {
		val items = listOf(
			LayoutItem("test-angler", "cast-timer", "FISHING", "cast"),
			LayoutItem("test-angler", "bite-alert", "FISHING", "bite"),
			LayoutItem("chat-tabs", "enabled", "CHAT", "tabs"),
			LayoutItem("kuudra-splits", "enabled", "KUUDRA", "ks"),
			LayoutItem("dungeon-map", "enabled", "DUNGEONS", "map"),
		)
		val pages = SettingsLayout.pages(items, map, links = listOf(MainSection.TIMERS to "dungeons.kuudra-splits"))
		assertEquals(MainSection.entries.toList(), pages.map { it.main }.distinct())
		val dungeons = pages.filter { it.main == MainSection.DUNGEONS }
		assertEquals(listOf("", "kuudra"), dungeons.map { it.group })
		assertEquals("DUNGEONS", dungeons[1].parentIdentifier)
		assertEquals(listOf("ks"), dungeons[1].subsections.single().items)

		val fishingTimers = pages.single { it.main == MainSection.TIMERS && it.group == "fishing" }
		assertEquals(listOf("cast"), fishingTimers.subsections.single { it.subsection.id == "fishing" }.items)
		val timers = pages.single { it.main == MainSection.TIMERS && it.group.isEmpty() }
		assertEquals(listOf("dungeons.kuudra-splits"), timers.links.map { it.path })
		assertTrue(pages.filter { it.main == MainSection.TIMERS }.flatMap { it.subsections }.none { it.subsection.id == "kuudra-splits" })
		val chat = pages.single { it.main == MainSection.MISC && it.group == "chat" }
		assertEquals("apex.settings.misc.group.chat", chat.translationKey)
		assertEquals("apex.settings.group.chat", chat.fallbackTranslationKey)
	}

	@Test
	fun bundledMapLaysOutEveryRow() {
		val bundled = MigrationMap.bundled()
		val items = bundled.entries.map { LayoutItem(it.oldConfig, it.oldOption, "MISC", it.oldKey) }
		val shown = SettingsLayout.pages(items, bundled).flatMap { p -> p.subsections.flatMap { it.items } }
		assertEquals(items.size, shown.size)
		assertEquals(items.map { it.value }.toSet(), shown.toSet())
	}

	@Test
	fun prettyLabels() {
		assertEquals("Tab List", SettingsLayout.pretty("tab-list"))
	}
}
