// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.museum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MilestonesMenuSettings
import dev.ashtonite365.apex.features.menus.museum.MuseumMenu
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.museum.MuseumInventoryAccess
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MergePolicy
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath

class MuseumInventoryAccessTest {
	private val target = SettingPath.parse("misc.museum-inventory.enabled")

	@Test
	fun defaultsOnAndPlainToggle() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		val opt = MuseumInventoryAccess.TConfig.sortedOptions.single()
		assertEquals("museum-inventory", MuseumInventoryAccess.TConfig.name)
		assertEquals("enabled", opt.propertyName)
		assertEquals(true, opt.default())
		assertEquals(RiskLevel.NONE, opt.riskLevel)
		assertFalse(opt.labelText.string.startsWith("⚠"))
	}

	@Test
	fun shownInMiscRenderTab() {
		val map = MigrationMap.bundled()
		val e = map["museum-inventory.enabled"]!!
		assertEquals(MainSection.MISC, e.newPath.main)
		assertEquals("museum-inventory", e.newPath.subsection)
		assertEquals("render", e.group)
		assertEquals(map["fullbright.enabled"]!!.group, e.group)
	}

	@Test
	fun milestonesInventoryPanelOptionIsMergedIntoIt() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		assertTrue(MilestonesMenuSettings.sortedOptions.none { it.propertyName == "panel-inventory" })
		val map = MigrationMap.bundled()
		assertEquals(null, map["custom-menu-museum-milestones.panel-inventory"])
		val rule = map.duplicates.single { it.target == target }
		assertEquals(listOf("museum-inventory.enabled", "custom-menu-museum-milestones.panel-inventory"), rule.oldKeys)
		assertEquals(MergePolicy.FIRST_SAVED, rule.policy)
		val m = ConfigMigrator(map)

		val off = m.migrate(Json.parseToJsonElement("""{"custom-menu-museum-milestones":{"panel-inventory":false}}""").jsonObject)
		assertEquals(JsonPrimitive(false), m.lookup(off.tree, target))
		assertTrue(off.unmapped.isEmpty())

		val both = m.migrate(Json.parseToJsonElement("""{"custom-menu-museum-milestones":{"panel-inventory":false},"museum-inventory":{"enabled":true}}""").jsonObject)
		assertEquals(JsonPrimitive(true), m.lookup(both.tree, target))
	}

	@Test
	fun panelSitsAtTheBottomBelowTheContent() {
		for ((vx, vy, vw, vh) in listOf(listOf(130, 10, 380, 340), listOf(0, 6, 380, 258), listOf(50, 20, 200, 300))) {
			val pad = 6
			val bottom = MuseumInventoryAccess.contentBottom(vy, vh, pad, true)
			val (ix, iy) = MuseumInventoryAccess.panelOrigin(vx, vy, vw, vh, pad)
			assertEquals(vy + vh - pad, iy + InventoryPanel.HEIGHT, "panel ends at the menu's bottom padding")
			assertEquals(bottom + 4, iy, "4 px between the content and the panel")
			assertEquals(vx + vw / 2, ix + InventoryPanel.WIDTH / 2, "centred")

			for (row in 0..3) for (col in 0..8) {
				val (sx, sy) = InventoryPanel.itemPos(row, col, ix, iy)
				assertTrue(sy - 1 >= bottom && sy + 17 <= vy + vh - pad, "row $row col $col")
				assertTrue(sx - 1 >= vx && sx + 17 <= vx + vw, "row $row col $col")
			}
		}
		assertEquals(250 + InventoryPanel.RESERVED, MuseumInventoryAccess.menuHeight(250, true))
		assertEquals(250, MuseumInventoryAccess.menuHeight(250, false))
		assertEquals(100 - 6, MuseumInventoryAccess.contentBottom(0, 100, 6, false))
	}

	@Test
	fun onlyWithTheOptionAndARealPlayerInventory() {
		assertTrue(MuseumInventoryAccess.shows(true, 90))
		assertFalse(MuseumInventoryAccess.shows(false, 90))
		assertFalse(MuseumInventoryAccess.shows(true, 27))
	}

	@Test
	fun museumMenuGrowsByThePanel() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		MuseumInventoryAccess.TConfig.explicitDefaultLoad()
		assertEquals(380 to 250 + InventoryPanel.RESERVED, MuseumMenu.defaultSize)
	}

	@Test
	fun failuresAreLoggedOnceAndSwallowed() {
		val logs = mutableListOf<String>()
		repeat(3) {
			assertFalse(MuseumInventoryAccess.guard("test-panel", { m, _ -> logs += m }) { error("boom") })
		}
		assertEquals(1, logs.size)
		assertTrue(logs.single().startsWith("[ApexMuseumInv] test-panel failed"), logs.single())
		assertTrue(MuseumInventoryAccess.guard("test-ok", { m, _ -> logs += m }) {})
	}
}
