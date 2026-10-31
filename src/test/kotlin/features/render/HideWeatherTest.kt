// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.render.HideWeather
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap

class HideWeatherTest {
	@Test
	fun skySeesClearWeatherOnlyWhenEnabled() {
		assertEquals(0F, HideWeather.visibleLevel(true, 1F))
		assertEquals(0.7F, HideWeather.visibleLevel(false, 0.7F))
	}

	@Test
	fun defaultOffPlainToggle() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		val opt = HideWeather.TConfig.sortedOptions.single()
		assertEquals("hide-weather", HideWeather.TConfig.name)
		assertEquals("enabled", opt.propertyName)
		assertEquals(false, opt.default())
		assertEquals(RiskLevel.NONE, opt.riskLevel)
		assertFalse(opt.labelText.string.startsWith("⚠"))
	}

	@Test
	fun inRenderTabNextToNoFog() {
		val map = MigrationMap.bundled()
		val e = map["hide-weather.enabled"]!!
		assertEquals(MainSection.MISC, e.newPath.main)
		assertEquals("render", e.group)
		assertEquals(map["no-fog.crimson-isle"]!!.group, e.group)
	}
}
