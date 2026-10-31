// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.VanillaStyleDropdown
import dev.ashtonite365.apex.features.menus.style.CustomMenusPage
import dev.ashtonite365.apex.features.menus.style.InventoryTexture
import dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleChoice
import dev.ashtonite365.apex.features.menus.style.StyleCodec
import dev.ashtonite365.apex.features.menus.style.StyleState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class CustomMenusSwitchTest {
	private val tmp: File = Files.createTempDirectory("apex-switch").toFile()
	private var refreshes = 0
	private var inPlace = 0

	@BeforeEach fun setUp() { MenuStyle.file = File(tmp, "menu-style.json"); MenuStyle.resetForTests(); MenuStyle.refreshScreen = { refreshes++ }; MenuStyle.showVanillaInPlace = { inPlace++ } }
	@AfterEach fun tearDown() { MenuStyle.file = null; MenuStyle.resetForTests(); tmp.deleteRecursively() }

	private val master get() = CustomMenusPage.rows.filterIsInstance<CustomMenusPage.Row.Toggle>().first { it.label == "Custom Menus" }
	private val styleRow get() = CustomMenusPage.rows.filterIsInstance<CustomMenusPage.Row.Cycle>().first { it.label == "Style" }

	@Test fun pageGroupsUnderModuleHeaders() {
		assertTrue(CustomMenusPage.rows.first() is CustomMenusPage.Row.Module)
		assertEquals(listOf("Custom Menus", "Menu Style", "Menu Animations", "Inventory"), CustomMenusPage.rows.filterIsInstance<CustomMenusPage.Row.Module>().map { it.title })
	}

	@Test fun masterSwitchIsTheDropdownsVanilla() {
		assertTrue(master.on(MenuStyle.state))
		master.flip()
		assertFalse(MenuStyle.state.customMenus); assertEquals(StyleChoice.VANILLA, StyleChoice.of(MenuStyle.state))
		assertEquals("Vanilla", styleRow.value(MenuStyle.state))
		assertNull(InventoryTexture.pathFor(MenuStyle.state), "vanilla inventory"); assertNull(InventoryWidgetStyle.look(MenuStyle.state))
		assertFalse(StyleCodec.decode(MenuStyle.file!!.readText()).customMenus, "saved to the one file")
		master.flip()
		assertTrue(MenuStyle.state.customMenus); assertEquals(1, inPlace, "off: dropped in place"); assertEquals(1, refreshes, "only turning back on re-attaches")
	}

	@Test fun menuTopBarDropdownHasVanilla() {
		val bar = StyleBar(); val g = StyleBar.geometry(300, 10, MenuStyle.state)
		assertTrue(bar.click(g, g.dropX + 2.0, g.y + 2.0)); assertTrue(bar.open)
		val vi = StyleChoice.entries.indexOf(StyleChoice.VANILLA)
		assertTrue(bar.click(g, g.dropX + 2.0, g.itemY(vi) + 2.0))
		assertFalse(MenuStyle.state.customMenus)
		assertFalse(master.on(MenuStyle.state), "switch reads what the dropdown wrote")

		val g2 = StyleBar.geometry(300, 10, MenuStyle.state)
		bar.click(g2, g2.dropX + 2.0, g2.y + 2.0); bar.click(g2, g2.dropX + 2.0, g2.itemY(StyleChoice.entries.indexOf(StyleChoice.FORGE)) + 2.0)
		assertTrue(MenuStyle.state.customMenus); assertEquals(Style.FORGE, MenuStyle.state.style)
	}

	@Test fun inventoryDropdownWritesTheSameSetting() {
		val (x, y) = VanillaStyleDropdown.place(100, 50, 176)
		assertEquals(100 + 176 - VanillaStyleDropdown.W, x); assertTrue(y + VanillaStyleDropdown.H <= 50, "above the window, clear of the panel")
		assertEquals(true to true, VanillaStyleDropdown.click(x, y, false, x + 1.0, y + 1.0), "one click opens")
		val vi = StyleChoice.entries.indexOf(StyleChoice.VANILLA)
		assertEquals(true to false, VanillaStyleDropdown.click(x, y, true, x + 1.0, VanillaStyleDropdown.itemY(y, vi) + 1.0))
		assertFalse(MenuStyle.state.customMenus); assertEquals("Vanilla", styleRow.value(MenuStyle.state))
		VanillaStyleDropdown.click(x, y, true, x + 1.0, VanillaStyleDropdown.itemY(y, StyleChoice.entries.indexOf(StyleChoice.SKYGLASS)) + 1.0)
		assertTrue(master.on(MenuStyle.state)); assertEquals(Style.SKYGLASS, MenuStyle.state.style)
		assertEquals(false to false, VanillaStyleDropdown.click(x, y, false, x - 5.0, y + 1.0), "clicks elsewhere are not ours")
	}

	@Test fun dropdownShownWhereNeeded() {
		assertTrue(VanillaStyleDropdown.wanted(true, false, false, true))
		assertTrue(VanillaStyleDropdown.wanted(false, true, true, false), "vanilla-mode chest keeps a way back on")
		assertFalse(VanillaStyleDropdown.wanted(false, true, true, true))
	}

	@Test fun vanillaKeepsTheRememberedStyle() {
		val s = StyleChoice.apply(StyleState(style = Style.FORGE), StyleChoice.VANILLA)
		assertEquals(Style.FORGE, s.style); assertFalse(s.customMenus)
		assertTrue(StyleCodec.decode(StyleCodec.encode(StyleState())).customMenus)
		assertTrue(StyleCodec.decode("{}").customMenus, "old files default to on")
	}

	@Test fun choiceLabelsFitTheBoxes() {

		for (c in StyleChoice.entries) assertTrue(c.label.length * 6 <= VanillaStyleDropdown.W - 14, c.label)
	}

	@Test fun vanillaSwitchesOffInPlaceWithNoPackets() {
		MenuStyle.choose(StyleChoice.VANILLA)
		assertEquals(1, inPlace); assertEquals(0, refreshes, "no re-init / reopen on Vanilla")
		MenuStyle.choose(StyleChoice.VANILLA)
		assertEquals(1, inPlace, "already off: nothing happens")

		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/style/MenuStyle.kt").readText()
		val body = src.substringAfter("var showVanillaInPlace").substringBefore("var refreshScreen")
		assertTrue(body.contains("customGui = null"))
		for (bad in listOf("send", "connection", "Packet", "command", "Command", "resize", "setScreen", "handleInventoryMouseClick", "onClose"))
			assertFalse(body.contains(bad), bad)
	}

	@Test fun chestSwitchWorksAfterTheDropdownPickedVanilla() {
		MenuStyle.choose(StyleChoice.FORGE); MenuStyle.choose(StyleChoice.VANILLA)
		val wrap = CustomMenusPage.rows.filterIsInstance<CustomMenusPage.Row.Toggle>().first { it.label == "Style every chest menu" }
		assertFalse(wrap.on(MenuStyle.state), "shown off: nothing is styled in Vanilla")
		val before = refreshes
		wrap.flip()
		assertTrue(MenuStyle.state.customMenus && MenuStyle.state.wrapAll); assertEquals(Style.FORGE, MenuStyle.state.style)
		assertTrue(refreshes > before, "open screen refreshed")
		wrap.flip()
		assertFalse(MenuStyle.state.wrapAll); assertTrue(MenuStyle.state.customMenus)
		assertEquals(StyleState(style = Style.SKYGLASS, wrapAll = true, customMenus = true), MenuStyle.wrapAllAfter(StyleState(customMenus = false, wrapAll = false), true))
	}

	@Test fun vanillaDropdownOpensAndPicksOnAGenericScreen() {
		MenuStyle.choose(StyleChoice.VANILLA)
		val d = VanillaStyleDropdown.Dropdown({ intArrayOf(100, 100, 176) }, { 800 to 600 })
		d.sync()
		val left = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
		assertTrue(d.claim(d.x + 5.0, d.y + 5.0, left)); assertTrue(d.open, "head opens")
		val r = d.listRect(); val i = StyleChoice.entries.indexOf(StyleChoice.SKYGLASS)
		assertTrue(d.claim(r[0] + 5.0, VanillaStyleDropdown.rowY(r, i) + 3.0, left))
		assertFalse(d.open); assertEquals(StyleChoice.SKYGLASS, StyleChoice.of(MenuStyle.state))
	}
}
