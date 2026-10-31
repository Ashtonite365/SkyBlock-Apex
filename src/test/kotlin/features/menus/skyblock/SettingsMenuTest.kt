// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.SettingFlag
import dev.ashtonite365.apex.features.menus.skyblock.SettingsMenu
import dev.ashtonite365.apex.features.menus.skyblock.SettingsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SettingsMenuTest {
	private val d = RealDumpFixtures.load("sb-settings")

	@Test
	fun realDumpCategories() {
		val m = SettingsParser.parse(d.title, d.slots)!!
		assertEquals(listOf(10, 12, 14, 16, 28, 30, 32, 34, 43, 50), m.entries.map { it.slot })
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
		val personal = m.entries.first { it.name == "Personal Settings" }
		assertEquals(SettingFlag("Double Tap to Drop", true), personal.flags.first())
		assertEquals(SettingFlag("Inventory Full Notifications", false), personal.flags[4])
		assertEquals("5/7 on", personal.summary)
		assertEquals(listOf("General settings related to your", "experience."), personal.description)
		assertNull(m.entries.first { it.name == "Island Settings" }.summary)
	}

	@Test
	fun mismatchFallsBack() {
		assertNull(SettingsParser.parse("Fast Travel", d.slots))
		assertNull(SettingsParser.parse(d.title, d.slots.filter { it.slot != 48 }), "no Go Back to the SkyBlock Menu = not SkyBlock's page")
		assertNull(SettingsParser.parse(d.title, emptyList()))
		assertFalse(SettingsParser.TITLE.matches("Settings ➜ Personal"))
	}

	@Test
	fun panelsCollapse() {
		SettingsMenu.expanded.clear()
		assertFalse(SettingsMenu.isOpen("Personal Settings", false))
		SettingsMenu.toggle("Personal Settings")
		assertTrue(SettingsMenu.isOpen("Personal Settings", false))
		assertFalse(SettingsMenu.isOpen("Personal Settings", true))
		SettingsMenu.toggle("Personal Settings")
		assertFalse(SettingsMenu.isOpen("Personal Settings", false))
	}
}
