package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsLayout
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsScreen

class SettingsModuleHeadersTest {
	private val pages by lazy { SettingsTabs.pages(AllRegisteredConfigs.configs) }

	@Test fun runsKeepFirstAppearanceOrderAndGroupInterleavedItems() {
		val runs = StyledSettingsScreen.moduleRuns(listOf("a.1", "b.1", "a.2", "c.1")) { it.substringBefore('.') }
		assertEquals(listOf("a" to listOf("a.1", "a.2"), "b" to listOf("b.1"), "c" to listOf("c.1")), runs)
		assertEquals(1, StyledSettingsScreen.moduleRuns(listOf("x.1", "x.2")) { it.substringBefore('.') }.size, "one module: no headers")
	}

	@Test fun texturesAndFarTerrainSplitsIntoItsTwoModules() {
		val view = pages.flatMap { it.subsections }.single { it.subsection.main.name == "MISC" && it.subsection.id == "textures-terrain" }
		val runs = StyledSettingsScreen.moduleRuns(view.items) { it.element.name }
		assertEquals(listOf("connected-textures", "apex-far-terrain"), runs.map { it.first })
		assertEquals(setOf("connected-glass", "connected-blocks", "connected-far-terrain"), runs[0].second.map { it.propertyName }.toSet())
		assertTrue(runs[1].second.map { it.propertyName }.containsAll(listOf("enabled", "unlimited-distance", "distance", "debug", "debug-chunk-map", "debug-hud")))
	}

	@Test fun everyMultiModuleCardGetsHeadersAndNoSettingIsLost() {
		for (v in pages.flatMap { it.subsections }) {
			val runs = StyledSettingsScreen.moduleRuns(v.items) { it.element.name }
			assertEquals(v.items.toSet(), runs.flatMap { it.second }.toSet(), v.subsection.id)
			assertEquals(v.items.size, runs.sumOf { it.second.size }, v.subsection.id)
		}
		assertTrue(StyledSettingsLayout.MODULE_H > StyledSettingsLayout.MODULE_GAP)
	}
}
