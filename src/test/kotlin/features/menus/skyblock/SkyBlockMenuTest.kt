// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuMode
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.skyblock.SbTile
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenu
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuParser
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuSettings
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkyBlockMenuTest {
	private val d = RealDumpFixtures.load("skyblock-menu")
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test
	fun realDumpParsesEveryTileAtItsDumpSlot() {
		val m = SkyBlockMenuParser.parse(d.title, d.slots)!!
		for (t in SbTile.entries) assertEquals(t.dumpSlot, m.tiles[t]?.first, t.name)
	}

	@Test
	fun secondRealDumpSameTiles() {
		val d2 = RealDumpFixtures.load("skyblock-menu-0922")
		val m = SkyBlockMenuParser.parse(d2.title, d2.slots)!!
		for (t in SbTile.entries) assertEquals(t.dumpSlot, m.tiles[t]?.first, t.name)
		assertEquals("24.3 avg", m.tiles[SbTile.SKILLS]?.second)
	}

	@Test
	fun liveInfoFromLore() {
		val m = SkyBlockMenuParser.parse(d.title, d.slots)!!
		assertEquals("24.2 avg", m.tiles[SbTile.SKILLS]?.second)
		assertEquals("1/2", m.tiles[SbTile.PROFILES]?.second)
		assertEquals("Griffin", m.tiles[SbTile.PETS]?.second)
		assertEquals("[122]", m.tiles[SbTile.LEVELING]?.second)
		assertEquals("7 Hours", m.tiles[SbTile.BANK]?.second)
		assertEquals("0d 12h 50m 33s", m.tiles[SbTile.COOKIE]?.second)
		assertEquals("76.7%", m.tiles[SbTile.COLLECTIONS]?.second)
	}

	@Test
	fun mismatchFallsBackToVanilla() {
		assertNull(SkyBlockMenuParser.parse("Storage", d.slots))
		assertNull(SkyBlockMenuParser.parse(d.title, d.slots.filter { it.slot != 48 }), "missing Profile Management")
		assertNull(SkyBlockMenuParser.parse(d.title, emptyList()), "window still filling")
	}

	@Test
	fun registryResolution() {
		MenuRegistry.register(object : CustomMenu by SkyBlockMenu { override val enabled = false })
		assertTrue(MenuRegistry.resolve("§aSkyBlock Menu") { null } is MenuRegistry.Resolution.Vanilla)
		MenuRegistry.clearForTests()
		MenuRegistry.register(object : CustomMenu by SkyBlockMenu { override val enabled = true })
		assertTrue(MenuRegistry.resolve("SkyBlock Menu") { null } is MenuRegistry.Resolution.Fallback)
		assertTrue(MenuRegistry.resolve("SkyBlock Menu Extra") { null } is MenuRegistry.Resolution.None)
	}

	@Test
	fun settingsDefaultsAndReset() {
		assertEquals(MenuMode.APEX, SkyBlockMenuSettings.allOptions.getValue("mode").default())
		assertEquals(true, SkyBlockMenuSettings.allOptions.getValue("panel-live-info").default())
		assertFalse(SkyBlockMenuSettings.allOptions.values.any { it.riskLevel.isRisky }, "no auto-clicks in this menu")
	}
}
