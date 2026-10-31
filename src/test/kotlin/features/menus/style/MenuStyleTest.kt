// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.MenuChrome
import dev.ashtonite365.apex.features.menus.MenuInputGuard
import dev.ashtonite365.apex.features.menus.MenuInputHost
import dev.ashtonite365.apex.features.menus.MenuInputRouter
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper
import dev.ashtonite365.apex.features.menus.style.CloseGhost
import dev.ashtonite365.apex.features.menus.style.ForgeMode
import dev.ashtonite365.apex.features.menus.style.MenuAnim
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.RecordingCanvas
import dev.ashtonite365.apex.features.menus.style.Shapes
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleAnim
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleCodec
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.style.StyledChestLayout
import dev.ashtonite365.apex.features.menus.style.WardrobeLayout
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsLayout
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.inventory.ContainerInput
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

@org.junit.jupiter.api.extension.ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)

class MenuStyleTest {
	private val tmp: File = Files.createTempDirectory("apex-style").toFile()
	private val oldSound = MenuPress.sound

	@AfterEach fun reset() { MenuPress.sound = oldSound; MenuStyle.file = null; MenuStyle.resetForTests(); MenuPress.resetForTests(); MenuInputGuard.resetForTests() }

	@Test fun defaultsAreSkyglassAndForgeDark() {
		val s = StyleState()
		assertEquals(Style.SKYGLASS, s.style); assertEquals(ForgeMode.DARK, s.forgeMode)
		assertFalse(s.dark)
		assertTrue(s.copy(style = Style.FORGE).dark)
		assertEquals(StyleState(), StyleCodec.decode(null)); assertEquals(StyleState(), StyleCodec.decode("not json"))
	}

	@Test fun codecRoundTrips() {
		for (st in Style.entries) for (m in ForgeMode.entries) for (a in MenuAnim.entries) for (b in listOf(true, false)) {
			val s = StyleState(st, m, b, a, !b, b)
			assertEquals(s, StyleCodec.decode(StyleCodec.encode(s)))
		}
	}

	@Test fun styleAndForgeModePersistInOneSharedFile() {
		val f = File(tmp, "menu-style.json"); MenuStyle.file = f; MenuStyle.resetForTests()
		assertEquals(Style.SKYGLASS, MenuStyle.state.style)
		MenuStyle.setStyle(Style.FORGE)
		MenuStyle.toggleForgeMode()
		assertTrue(f.isFile)

		MenuStyle.resetForTests()
		assertEquals(Style.FORGE, MenuStyle.state.style); assertEquals(ForgeMode.LIGHT, MenuStyle.state.forgeMode)
		assertEquals(StylePalette.FORGE_LIGHT, MenuStyle.palette)
		MenuStyle.toggleForgeMode(); MenuStyle.resetForTests()
		assertEquals(StylePalette.FORGE_DARK, MenuStyle.palette)
		MenuStyle.setStyle(Style.SKYGLASS); MenuStyle.resetForTests()
		assertEquals(StylePalette.SKYGLASS, MenuStyle.palette)

		assertEquals(StylePalette.SKYGLASS.theme(), MenuStyle.palette.theme())
	}

	@Test fun styleBarPressIsSwallowedAndChangesStyle() {
		MenuStyle.file = File(tmp, "bar.json"); MenuStyle.resetForTests()
		val bar = StyleBar()
		val g = StyleBar.geometry(300, 10, MenuStyle.state)
		assertFalse(bar.click(g, 5.0, 5.0))
		assertTrue(bar.click(g, g.dropX + 2.0, g.y + 2.0)); assertTrue(bar.open)
		assertTrue(bar.click(g, g.dropX + 2.0, g.itemY(Style.FORGE.ordinal) + 2.0))
		assertEquals(Style.FORGE, MenuStyle.state.style); assertFalse(bar.open)
		val g2 = StyleBar.geometry(300, 10, MenuStyle.state)
		assertTrue(g2.switchX >= 0)
		assertTrue(bar.click(g2, g2.switchX + 3.0, g2.y + 3.0)); assertEquals(ForgeMode.LIGHT, MenuStyle.state.forgeMode)
	}

	@Test fun chromeBarSitsAboveTheMenuOrInsideIt() {
		assertEquals(400 to (100 - StyleBar.H - 3), MenuChrome.barAnchor(100, 100, 300))
		assertEquals(396 to 6, MenuChrome.barAnchor(100, 2, 300))
	}

	@Test fun animationTimings() {
		val s = StyleState()
		assertEquals(230, StyleAnim.openMs(s)); assertEquals(110, StyleAnim.closeMs(s))
		assertEquals(120, StyleAnim.closeMs(s.copy(style = Style.FORGE)))
		assertEquals(0, StyleAnim.openMs(s.copy(animate = false))); assertEquals(0, StyleAnim.closeMs(s.copy(animate = false)))
		val r = s.copy(reduceMotion = true, anim = MenuAnim.GLITCH)
		assertEquals(80, StyleAnim.openMs(r)); assertEquals(0, StyleAnim.closeMs(r)); assertEquals(MenuAnim.FADE, StyleAnim.kind(r))
		assertEquals(0.0, StyleAnim.openReveal(s, 0)); assertEquals(1.0, StyleAnim.openReveal(s, 230))
		assertTrue(StyleAnim.openReveal(s, 115) > 0.5)
		assertEquals(1.0, StyleAnim.closeVisible(s, 0)); assertEquals(0.0, StyleAnim.closeVisible(s, 110))
		assertEquals(StyleAnim.glitchRects(7, 40, 200, 100, 0.3), StyleAnim.glitchRects(7, 47, 200, 100, 0.3))
		assertTrue(StyleAnim.glitchRects(7, 40, 200, 100, 0.3) != StyleAnim.glitchRects(7, 60, 200, 100, 0.3))
		assertTrue(StyleAnim.glitchRects(7, 40, 200, 100, 1.0).isEmpty())
		for (g in StyleAnim.glitchRects(3, 0, 200, 100, 0.0)) assertTrue(g.x >= 0 && g.y >= 0 && g.x + g.w <= 200 && g.y + g.h <= 100)
	}

	@Test fun closeGhostCircleClosesIn() {
		fun area(v: Double) = RecordingCanvas().also { CloseGhost.draw(it, MenuAnim.CIRCLE, StylePalette.SKYGLASS, 0, 0, 200, 100, v, 1, 0) }
			.ops.sumOf { op -> op.split(' ').let { (it[3].toInt() - it[1].toInt()) * (it[4].toInt() - it[2].toInt()) } }
		assertTrue(area(1.0) > area(0.6)); assertTrue(area(0.6) > area(0.2)); assertEquals(0, area(0.0))
	}

	@Test fun roundedShapesStayInsideTheirRect() {
		val c = RecordingCanvas()
		Shapes.roundFrame(c, 10, 10, 40, 20, 5, 0xFF112233.toInt(), 0xFF445566.toInt())
		for (op in c.ops) { val p = op.split(' '); assertTrue(p[1].toInt() >= 10 && p[2].toInt() >= 10 && p[3].toInt() <= 50 && p[4].toInt() <= 30, op) }
		assertTrue(Shapes.cornerInset(5, 0) > 0); assertEquals(0, Shapes.cornerInset(5, 5))
	}

	@Test fun menusGrowWhenTheInventoryIsHidden() {
		val withInv = StyledChestLayout.build(640, 360, 6, withInventory = true)
		val noInv = StyledChestLayout.build(640, 360, 6, withInventory = false)
		assertEquals(1f, withInv.scale); assertTrue(noInv.scale > 1f)
		assertTrue(noInv.cellSize > withInv.cellSize)
		assertTrue(noInv.cellScreen(8).x + noInv.cellSize - noInv.cellScreen(0).x > 9 * 18)
		assertEquals(null, noInv.inventory)
		val (ix, iy) = withInv.inventory!!
		assertTrue(iy > withInv.cellScreen(53).y)
		assertTrue(ix >= withInv.frame.x && iy + StyledChestLayout.INV_H <= withInv.frame.y + withInv.frame.h + 4)
		assertFalse(StyledChestLayout.usesInventory("SkyBlock Menu")); assertTrue(StyledChestLayout.usesInventory("Ender Chest (1/9)"))
	}

	@Test fun wardrobeArmourFillsMostOfTheMenu() {
		val f = WardrobeLayout.frame(640, 360)
		val k = WardrobeLayout.INVENTORY_SCALE
		val IP = dev.ashtonite365.apex.features.menus.support.InventoryPanel
		val top = f.y + 22; val bottom = f.y + f.h - 22 - (IP.scaledHeight(k) + 4)
		val a = WardrobeLayout.armour(f.x, f.w, top, bottom)
		assertTrue(a.scale > 1.2f, "armour scale ${a.scale}")
		assertTrue(a.width >= f.w * 0.5, "armour ${a.width} of ${f.w}")

		assertTrue(WardrobeLayout.ARMOUR_SHRINK in 0.80f..0.85f)
		assertTrue(k in 1.25f..1.35f)
		val invX = f.x + (f.w - 9 * Math.round(18 * k)) / 2; val invY = bottom + 2
		assertTrue(invX >= f.x && invX + 9 * Math.round(18 * k) <= f.x + f.w)
		assertTrue(invY + IP.scaledHeight(k) <= f.y + f.h - 18, "inventory above Close/Next")
		assertTrue(a.buttonAt(0).second + WardrobeLayout.BUTTON < invY, "buttons clear of the Inventory label")
		assertEquals(Triple(invX + 2 * 23, invY + 10 + 3 * 23 + 5, 23), IP.scaledCell(3, 2, invX, invY, k))
		assertTrue(a.buttonAt(0).second + WardrobeLayout.BUTTON <= bottom)

		assertEquals(a.cell * 0.72f / 16f, a.itemScale, 0.001f)
		val (ix, iy) = a.itemPos(100, 100); val icon = 16 * a.itemScale
		assertTrue(ix > 100 && ix + icon <= 100 + a.cell && iy + icon <= 100 + a.cell)
		assertTrue(icon / a.cell in 0.70f..0.75f)
		assertEquals(26, WardrobeLayout.BUTTON)
		for (col in 0 until 9) { val (bx, _) = a.buttonAt(col); val (cx, _) = a.cellAt(0, col)
			assertTrue(kotlin.math.abs((cx + a.cell / 2) - (bx + WardrobeLayout.BUTTON / 2)) <= 1) }
		assertTrue(a.gridX >= f.x && a.gridX + a.width <= f.x + f.w)

		assertEquals(1f, WardrobeLayout.armour(0, 100, 0, 50).scale)
	}

	@Test fun everyPointOfAStyledGridHitsAtMostOneRealSlot() {
		for (withInv in listOf(false, true)) {
			val l = StyledChestLayout.build(640, 360, 6, withInv)
			val s = l.cellScale
			val cells = (0 until 54).associateWith { GenericWrapper.itemPos(l.cellScreen(it).x, l.cellScreen(it).y, s) }
			var y = l.gridY.toDouble() - 3
			while (y < l.gridY + 6 * l.pitch + 3) {
				var x = l.gridX.toDouble() - 3
				while (x < l.gridX + 9 * l.pitch + 3) {
					val hits = (0 until 54).filter { InventoryPanel.hitsSlot(it, 90, null, cells[it]!!.first, cells[it]!!.second, x, y, cells, s) }
					assertTrue(hits.size <= 1, "($x,$y) hits $hits")
					x += 0.25
				}
				y += 0.25
			}

			for (i in 0 until 54) { val c = l.cellScreen(i)
				assertEquals(i, InventoryPanel.cellAt(null, 90, c.x + c.w / 2.0, c.y + c.h / 2.0, cells, 1f) { s }) }
		}

		val a = WardrobeLayout.armour(0, 460, 30, 230)
		val cells = (0 until 36).associateWith { a.cellAt(it / 9, it % 9).let { (fx, fy) -> a.itemPos(fx, fy) } }
		for (i in 0 until 36) { val (fx, fy) = a.cellAt(i / 9, i % 9)
			assertEquals(i, InventoryPanel.cellAt(null, 90, fx + a.cell / 2.0, fy + a.cell / 2.0, cells, 1f) { a.itemScale }) }
	}

	@Test fun buttonAndModifiersArePreserved() {
		assertEquals(0 to ContainerInput.PICKUP, InventoryPanel.route(0, shift = false))
		assertEquals(1 to ContainerInput.PICKUP, InventoryPanel.route(1, shift = false))
		assertEquals(0 to ContainerInput.QUICK_MOVE, InventoryPanel.route(0, shift = true))
		assertEquals(1 to ContainerInput.QUICK_MOVE, InventoryPanel.route(1, shift = true))
	}

	@Test fun aPressOnAWrappedCellGoesToVanillaOnceAndNeverToApex() {
		var apexSends = 0
		val host = object : MenuInputHost {
			override val hiddenNow = false
			override fun overShowButton(mx: Double, my: Double) = false
			override fun showMenu() {}
			override fun overPanelCell(mx: Double, my: Double) = true
			override fun inView(mx: Double, my: Double) = true
			override fun viewPress(click: MouseButtonEvent) { apexSends++ }
			override fun viewRelease(click: MouseButtonEvent) {}
			override fun viewScroll(mx: Double, my: Double, amount: Double) {}
		}
		val router = MenuInputRouter(host)
		MenuPress.resetForTests(); MenuPress.sound = {}; MenuPress.overRealMenu = { true }
		for (button in 0..2) {
			MenuInputGuard.rawPress()
			assertTrue(router.press(MouseButtonEvent(50.0, 50.0, MouseButtonInfo(button, 0))), "button $button passes to vanilla")
			router.release(MouseButtonEvent(50.0, 50.0, MouseButtonInfo(button, 0)))
			MenuInputGuard.end()
		}
		assertEquals(0, apexSends)
	}

	@Test fun genericWrapperCoversAnUnknownMenu() {
		assertTrue(GenericWrapper.eligible(isChest = true, inSkyBlock = true, enabled = true, denied = false, vanillaOwned = false, claimed = false))
		assertFalse(GenericWrapper.eligible(true, false, true, false, false, false))
		assertFalse(GenericWrapper.eligible(true, true, true, true, false, false))
		assertFalse(GenericWrapper.eligible(true, true, true, false, false, true))
		assertFalse(GenericWrapper.eligible(false, true, true, false, false, false))
		assertFalse(GenericWrapper.eligible(true, true, false, false, false, false))
		assertEquals(6, GenericWrapper.rowsOf(90)); assertEquals(3, GenericWrapper.rowsOf(63)); assertEquals(1, GenericWrapper.rowsOf(45))

		assertEquals(dev.ashtonite365.apex.features.menus.MenuRegistry.Resolution.None,
			dev.ashtonite365.apex.features.menus.MenuRegistry.resolve("Totally Unknown Menu 1031") { null })
	}

	@Test fun settingsDockWidensOnHover() {
		assertEquals(StyledSettingsLayout.DOCK, StyledSettingsLayout.dockWidth(true, 0))
		assertEquals(StyledSettingsLayout.DOCK_WIDE, StyledSettingsLayout.dockWidth(true, 130))
		assertTrue(StyledSettingsLayout.dockWidth(true, 65) in 29 until 120)
		assertEquals(StyledSettingsLayout.DOCK, StyledSettingsLayout.dockWidth(false, 130))
		assertTrue(StyledSettingsLayout.matches("pet xp", "Pet XP display", "")); assertFalse(StyledSettingsLayout.matches("bazaar", "Pets", ""))
	}

	private fun alpha(argb: Int) = argb ushr 24

	@Test fun skyglassSurfacesAreSeeThrough() {
		val p = StylePalette.SKYGLASS
		for ((n, c) in listOf("backdrop" to p.backdrop, "window" to p.window, "card" to p.card, "raised" to p.raised,
				"well" to p.well, "border" to p.border, "buttonFill" to p.buttonFill, "buttonHover" to p.buttonHover))
			assertTrue(alpha(c) < 0xFF, "$n alpha ${alpha(c)}")
		assertTrue(alpha(p.backdrop) < 0x80)
		assertTrue(p.blurWorld)
	}

	private fun coverage(ops: List<String>, px: Int, py: Int): Double {
		var a = 0.0
		for (op in ops) {
			val t = op.split(' '); if (t[0] != "F") continue
			if (px >= t[1].toInt() && px < t[3].toInt() && py >= t[2].toInt() && py < t[4].toInt()) {
				val s = java.lang.Long.parseLong(t[5], 16).toInt().ushr(24) / 255.0
				a = s + a * (1 - s)
			}
		}
		return a
	}

	@Test fun skyglassCardStaysTranslucentOnceComposited() {
		val c = RecordingCanvas()
		dev.ashtonite365.apex.features.menus.style.StyleWidgets.card(c, StylePalette.SKYGLASS, 10, 10, 100, 60)
		val a = coverage(c.ops, 60, 40)
		assertTrue(a < 0.8, "card centre composites to $a")
	}

	@Test fun translucentFrameDrawsBorderAsRingNotUnderTheFill() {
		val c = RecordingCanvas()
		val fill = 0x66FFFFFF; val border = 0x99FFFFFF.toInt()
		Shapes.roundFrame(c, 10, 10, 40, 20, 5, fill, border, glass = true)
		val fillHex = Integer.toHexString(fill); val borderHex = Integer.toHexString(border)
		for (x in 10 until 50) for (y in 10 until 30) {
			val hits = c.ops.map { it.split(' ') }.filter { x >= it[1].toInt() && x < it[3].toInt() && y >= it[2].toInt() && y < it[4].toInt() }
			assertTrue(hits.size <= 1, "pixel $x,$y drawn ${hits.size} times")
		}

		fun at(x: Int, y: Int) = c.ops.map { it.split(' ') }.firstOrNull { x >= it[1].toInt() && x < it[3].toInt() && y >= it[2].toInt() && y < it[4].toInt() }?.get(5)
		assertEquals(borderHex, at(30, 10)); assertEquals(borderHex, at(30, 29)); assertEquals(borderHex, at(10, 20)); assertEquals(borderHex, at(49, 20))
		assertEquals(fillHex, at(30, 20))

		assertEquals(null, at(10, 10))
	}

	@Test fun opaqueFrameKeepsTheOldDraw() {
		val c = RecordingCanvas()
		Shapes.roundFrame(c, 10, 10, 40, 20, 5, 0xFF112233.toInt(), 0xFF445566.toInt())
		val ref = RecordingCanvas()
		Shapes.round(ref, 10, 10, 40, 20, 5, 0xFF445566.toInt()); Shapes.round(ref, 11, 11, 38, 18, 4, 0xFF112233.toInt())
		assertEquals(ref.ops, c.ops)
	}

	@Test fun skyglassRequestsFrostForgeDoesNot() {
		assertTrue(MenuChrome.wantsBlur(StylePalette.SKYGLASS.blurWorld, inGameUi = true, blurriness = 5))
		assertFalse(MenuChrome.wantsBlur(StylePalette.FORGE_DARK.blurWorld, true, 5))
		assertFalse(MenuChrome.wantsBlur(StylePalette.FORGE_LIGHT.blurWorld, true, 5))
		assertFalse(MenuChrome.wantsBlur(true, true, 0))
		assertFalse(MenuChrome.wantsBlur(true, false, 5))
	}

	@Test fun forgePaletteUnchanged() {
		assertEquals(0xFF1A1410.toInt(), StylePalette.FORGE_DARK.window); assertEquals(0xFF241B15.toInt(), StylePalette.FORGE_DARK.card)
		assertEquals(0xFFF4E9DC.toInt(), StylePalette.FORGE_LIGHT.window); assertFalse(StylePalette.FORGE_DARK.blurWorld)
	}

	@Test fun settingsCardOutlineLeavesRowsVisible() {
		for (p in listOf(StylePalette.SKYGLASS, StylePalette.FORGE_DARK, StylePalette.FORGE_LIGHT)) {
			val c = RecordingCanvas()
			Shapes.ring(c, 20, 40, 300, 120, p.cardRadius, p.border)

			for (x in 21 + p.cardRadius until 319 - p.cardRadius step 7) for (y in 41 + p.cardRadius until 159 - p.cardRadius step 5)
				assertTrue(c.ops.none { val t = it.split(' '); x >= t[1].toInt() && x < t[3].toInt() && y >= t[2].toInt() && y < t[4].toInt() },
					"${p.style}: outline covers row pixel $x,$y")
		}
	}
}
