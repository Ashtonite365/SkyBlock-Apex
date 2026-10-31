// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.ClickHandler
import dev.ashtonite365.apex.settings.H1Sections
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.RiskDefaults
import dev.ashtonite365.apex.settings.SettingsRegistry
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs

class H1SectionsTest {
	private val map = MigrationMap.bundled()
	private val mine by lazy { AllRegisteredConfigs.configs.filter { it.name in H1Sections.HOLDERS } }

	private fun dynamic(old: String) = H1Sections.DYNAMIC_PREFIXES.any { old.startsWith(it) }

	@Test
	fun everyHolderIsLoadedAndOwnedByOneMain() {
		val names = mine.map { it.name }.toSet()
		assertEquals(H1Sections.HOLDERS.keys - (H1Sections.PENDING_HOLDERS - names), names, "C1 holders missing at runtime")

		val before = dev.ashtonite365.apex.settings.LayoutMoves.bundled.entries.associate { (o, n) -> n to o }
		fun mainBefore(e: dev.ashtonite365.apex.settings.MigrationEntry) =
			before[e.newPath.toString()]?.let { SettingPath.parse(it).main } ?: e.newPath.main
		for (e in H1Sections.entries(map)) {
			assertEquals(H1Sections.HOLDERS[e.oldConfig], mainBefore(e), "${e.oldKey} -> ${e.newPath}")
		}

		val strangers = map.entries.filter { mainBefore(it) in H1Sections.MAINS && it.oldConfig !in H1Sections.HOLDERS }
		assertTrue(strangers.isEmpty(), "Rows in C1 sections from other chunks: ${strangers.map { it.oldKey }}")
	}

	@Test
	fun everyLiveOptionIsMapped() {
		val missing = mine.flatMap { c -> c.allOptions.values.filter { it.handler !is ClickHandler }.map { "${c.name}.${it.propertyName}" } }
			.filter { map[it] == null && !dynamic(it) }
		assertTrue(missing.isEmpty(), "Options without a migration row (value would be lost): $missing")
	}

	@Test
	fun claimsHaveNoDuplicatesAndGroupsAreKnown() {
		val registry = SettingsRegistry.default(map)
		H1Sections.claimAll(registry, map)
		for (s in registry.subsections(MainSection.GUI)) assertTrue(s.group in H1Sections.GUI_GROUPS, "GUI ${s.id}: group '${s.group}'")
		for (s in registry.subsections(MainSection.CUSTOM_MENUS)) assertTrue(s.group in H1Sections.CUSTOM_MENU_GROUPS, "Custom Menus ${s.id}: group '${s.group}'")
	}

	@Test
	fun riskDefaultsFollowTheRule() {
		for (c in mine) for (o in c.allOptions.values) {
			if (!o.riskLevel.isRisky) continue
			val e = map["${c.name}.${o.propertyName}"] ?: continue
			val autoClick = e.kind == "customMenuAutoClick"
			assertEquals(RiskDefaults.defaultFor(e.newPath.main, autoClick), o.default(), "${e.oldKey} (${e.newPath}) default")
		}
	}
}
