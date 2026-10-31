// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuMode
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSections
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.exptable.ExpTableMenu
import dev.ashtonite365.apex.features.menus.skyblock.CityProjectMenu
import dev.ashtonite365.apex.features.menus.skyblock.CommunityMenu
import dev.ashtonite365.apex.features.menus.skyblock.PreviousCityProjectsMenu
import dev.ashtonite365.apex.features.menus.support.LegacyMenuSwitches
import dev.ashtonite365.apex.features.menus.workbench.AnvilMenu
import dev.ashtonite365.apex.features.menus.workbench.BrewingStandMenu
import dev.ashtonite365.apex.settings.MigrationMap

class SectionSwitchReconcileTest {
	private val menus: List<Pair<CustomMenu, String>> = listOf(
		AnvilMenu to "crafting", BrewingStandMenu to "crafting",
		CityProjectMenu to "skyblock", PreviousCityProjectsMenu to "skyblock", CommunityMenu to "skyblock")

	private fun load() {
		MenuSections.explicitDefaultLoad()
		for ((m, _) in menus) m.settings.explicitDefaultLoad()
	}
	@BeforeEach fun before() = load()
	@AfterEach fun after() { MenuRegistry.clearForTests(); load() }

	@Suppress("UNCHECKED_CAST")
	private fun setMode(s: MenuSettings, mode: MenuMode) = (s.allOptions["mode"] as dev.ashtonite365.apex.gui.config.ManagedOption<MenuMode>).set(mode)

	@Test fun menusSitInTheirCentralSection() {
		for ((m, g) in menus) assertEquals(g, m.section, m.id)
	}

	@Test fun sectionOffDisablesEveryMenuInItAndPerMenuModeStillWorks() {
		for ((m, g) in menus) {
			assertTrue(MenuRegistry.isOn(m), m.id)
			MenuSections.set(g, false)
			assertFalse(m.enabled, m.id); assertFalse(MenuRegistry.isOn(m), m.id)
			MenuRegistry.clearForTests(); MenuRegistry.register(m)
			MenuSections.set(g, true)
			setMode(m.settings, MenuMode.NORMAL)
			assertFalse(MenuRegistry.isOn(m), "${m.id}: own mode Normal under an ON section")
			setMode(m.settings, MenuMode.APEX)
			assertTrue(MenuRegistry.isOn(m), m.id)
		}
	}

	@Test fun noStandInSwitchIsLeft() {
		for ((m, _) in menus) {
			val ids = m.settings.allOptions.keys
			assertFalse("apex-menus" in ids || ids.any { it.startsWith("section-") }, "${m.id}: $ids")
		}
		val map = MigrationMap.bundled()
		for ((holder, key) in LegacyMenuSwitches.REMOVED) assertNull(map["$holder.$key"], "$holder.$key still mapped")
	}

	@Test fun savedStandInOffBecomesThatMenusNormalMode() {
		val off = JsonObject(mapOf("apex-menus" to JsonPrimitive(false), "width" to JsonPrimitive(300)))
		val r = LegacyMenuSwitches.apply("custom-menu-anvil", off)
		assertEquals(JsonPrimitive("NORMAL"), r["mode"]); assertNull(r["apex-menus"]); assertEquals(JsonPrimitive(300), r["width"])
		val on = LegacyMenuSwitches.apply("custom-menu-community", JsonObject(mapOf("section-community-shop" to JsonPrimitive(true))))
		assertNull(on["mode"]); assertNull(on["section-community-shop"])
		val untouched = JsonObject(mapOf("apex-menus" to JsonPrimitive(false)))
		assertEquals(untouched, LegacyMenuSwitches.apply("custom-menu-bank", untouched))
	}

	@Test fun expTableUsesTheRealTableSwitchOnly() {
		assertEquals(listOf("apex-menus"), dev.ashtonite365.apex.features.menus.exptable.ExpTableMenuGate.SWITCH_KEYS)
		assertNull(ExpTableMenu.section)
	}
}
