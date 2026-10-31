// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewStyle
import dev.ashtonite365.apex.features.menus.MenuChrome
import dev.ashtonite365.apex.features.menus.style.CloseGhost
import dev.ashtonite365.apex.features.menus.style.Frost
import dev.ashtonite365.apex.features.menus.style.MenuAnim
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.Press
import dev.ashtonite365.apex.features.menus.style.RecordingCanvas
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleAnim
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsLayout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Round2StyleTest {
	@AfterEach fun reset() { MenuStyle.file = null; MenuStyle.resetForTests() }

	private fun alpha(c: Int) = c ushr 24
	private fun lum(c: Int) = (0.2126 * ((c shr 16) and 255) + 0.7152 * ((c shr 8) and 255) + 0.0722 * (c and 255))

	@Test fun skyglassHasNoBackdropTintForgeKeepsItsOwn() {
		assertTrue(StylePalette.SKYGLASS.clearOutside); assertEquals(0, alpha(StylePalette.SKYGLASS.backdrop))
		assertFalse(StylePalette.of(StyleState(style = Style.FORGE)).clearOutside)
	}

	@Test fun panelTintIsSmokyLowAlphaNotMilky() {
		val p = StylePalette.SKYGLASS
		for (c in listOf(p.window, p.card, p.raised, p.well)) { assertTrue(alpha(c) <= 0x66, Integer.toHexString(c)) ; assertTrue(lum(c) < 60, Integer.toHexString(c)) }
		assertTrue(p.textShadow); assertTrue(lum(p.text) > 200)
	}

	@Test fun buttonsAreSlightlyFrosted() {
		val p = StylePalette.SKYGLASS
		for (c in listOf(p.buttonFill, p.buttonHover, p.buttonPressed)) { assertTrue(alpha(c) in 0x10..0x50, Integer.toHexString(c)); assertTrue(lum(c) > 200) }
		val r = RecordingCanvas(); StyleWidgets.button(r, p, 0, 0, 40, 14, "x", Press.NORMAL)
		assertTrue(r.ops.none { it.startsWith("B") })
	}

	@Test fun frostIsRequestedOnlyAtPanelRects() {
		val r = RecordingCanvas(); StyleWidgets.card(r, StylePalette.SKYGLASS, 10, 20, 100, 50)
		assertEquals(listOf("B 10 20 100 50 ${StylePalette.SKYGLASS.cardRadius}"), r.ops.filter { it.startsWith("B") })
		val f = RecordingCanvas(); StyleWidgets.card(f, StylePalette.of(StyleState(style = Style.FORGE)), 10, 20, 100, 50)
		assertTrue(f.ops.none { it.startsWith("B") })
		val n = RecordingCanvas(); StyleWidgets.card(n, StylePalette.SKYGLASS, 10, 20, 100, 50, frosted = false)
		assertTrue(n.ops.none { it.startsWith("B") })
	}

	@Test fun frostStripsStayInsideTheRoundedRect() {
		val s = Frost.strips(10, 10, 60, 30, 5)
		assertTrue(s.isNotEmpty())
		for (a in s) assertTrue(a[0] >= 10 && a[1] >= 10 && a[2] <= 70 && a[3] <= 40, a.joinToString())
		assertFalse(Frost.enabled(0)); assertTrue(Frost.enabled(1))
		assertEquals(0.4, MenuChrome.frostFade(MenuAnim.FADE, 0.4)); assertEquals(1.0, MenuChrome.frostFade(MenuAnim.GLITCH, 0.4))
	}

	@Test fun overlaysAreHeavyFrostInALaterLayer() {
		val p = StylePalette.SKYGLASS
		assertTrue(alpha(p.overlay) >= 0x90 && alpha(p.overlay) > alpha(p.card) * 3)
		val r = RecordingCanvas(); StyleWidgets.overlaySurface(r, p, 0, 0, 50, 40, 4)
		assertEquals("L", r.ops.first())
		assertTrue(r.ops.any { it.startsWith("F") && it.endsWith(Integer.toHexString(p.overlay)) })
	}

	@Test fun openDropdownSwallowsEveryPress() {
		val bar = StyleBar(); val g = StyleBar.geometry(200, 10, MenuStyle.state)
		assertTrue(bar.click(g, g.dropX + 2.0, g.y + 2.0)); assertTrue(bar.open)
		assertTrue(bar.click(g, 5.0, 150.0))
		assertFalse(bar.open)
		assertFalse(bar.click(g, 5.0, 150.0))
	}

	@Test fun chipStripClampsWheelsAndKeepsSelectionVisible() {
		val S = StyledSettingsLayout.Strip
		val widths = List(10) { 50 }; val total = S.total(widths); val view = 200
		assertEquals(0, S.clamp(-30, total, view)); assertEquals(total - view, S.clamp(9999, total, view))
		assertEquals(S.STEP, S.wheel(0, 0.0, -1.0, total, view))
		assertEquals(S.STEP * 2, S.wheel(0, -2.0, 1.0, total, view))
		val lefts = S.lefts(widths)
		val sc = S.ensureVisible(0, lefts[8], 50, total, view)
		assertTrue(lefts[8] >= sc && lefts[8] + 50 <= sc + view)
		assertEquals(null, S.visible(250, 40, 0, 200))
		assertEquals(190 to 10, S.visible(190, 40, 0, 200))
		assertFalse(S.moreLeft(0)); assertTrue(S.moreRight(0, total, view))
		assertTrue(StyledSettingsLayout.inRect(intArrayOf(0, 0, 10, 10), 5.0, 5.0))
	}

	@Test fun storagePagesUseTheStyle() {
		val r = RecordingCanvas(); StorageOverviewStyle.pageCard(r, StylePalette.SKYGLASS, 0, 0, 100, 60, false)
		assertTrue(r.ops.any { it.startsWith("B") })
		assertEquals(StylePalette.SKYGLASS.well, StorageOverviewStyle.cellFill(StylePalette.SKYGLASS))
	}

	@Test fun glitchBoxesAreTwoToFiveTimesAndJumpUpToThree() {
		val boxes = StyleAnim.glitchBoxes(42, 300, 200)
		assertTrue(boxes.size > 10)
		for (b in boxes) {
			assertTrue(b.sx in 2.0..5.0 && b.sy in 2.0..5.0)
			assertTrue(b.w <= (StyleAnim.GLITCH_BASE_W * 5)); assertTrue(b.h <= StyleAnim.GLITCH_BASE_H * 5)
			if (b.x + b.w < 300) assertTrue(b.w >= StyleAnim.GLITCH_BASE_W * 2)
			assertTrue(b.jumps.size in 0..3)
		}
		assertEquals(300 * 200, boxes.sumOf { it.w * it.h })
		assertEquals(boxes, StyleAnim.glitchBoxes(42, 300, 200))
		assertTrue(boxes != StyleAnim.glitchBoxes(43, 300, 200))
	}

	@Test fun glitchSettlesFullyAndCloseIsTheReverse() {
		val boxes = StyleAnim.glitchBoxes(5, 200, 100)
		assertTrue(boxes.all { StyleAnim.glitchState(it, 1.0, 999).settled })
		val r = RecordingCanvas(); StyleWidgets.cover(r, MenuAnim.GLITCH, 0xFF000000.toInt(), 0, 0, 200, 100, 1.0, 5, 999)
		assertTrue(r.ops.isEmpty())
		assertTrue(boxes.any { !StyleAnim.glitchState(it, 0.0, 0).settled })

		for (v in listOf(0.2, 0.5, 0.9)) for (b in boxes) assertEquals(StyleAnim.glitchState(b, v, 0).settled, StyleAnim.glitchState(b, v, 0).settled)
		val g = RecordingCanvas(); CloseGhost.draw(g, MenuAnim.GLITCH, StylePalette.SKYGLASS, 0, 0, 200, 100, 0.0, 5, 0)
		assertTrue(g.ops.isEmpty())
		val s = StyleState(anim = MenuAnim.GLITCH)
		assertEquals((230 * 1.4).toLong(), StyleAnim.openMs(s)); assertTrue(StyleAnim.openMs(s) > 230)
		assertTrue(StyleAnim.closeMs(s) > 110); assertEquals(0, StyleAnim.openMs(s.copy(animate = false)))
	}

	@Test fun glitchBoxesJumpBeforeSettling() {
		val b = StyleAnim.glitchBoxes(9, 400, 300).first { it.jumps.size == 3 && it.settle > 0.3 && it.jumps.last() != (0 to 0) }
		val late = StyleAnim.glitchState(b, b.settle * 0.95, 0)
		assertFalse(late.settled); assertEquals(b.x + b.jumps.last().first, late.x)
		assertEquals(b.x, StyleAnim.glitchState(b, b.settle * 0.1, 0).x)
	}

	@Test fun apexButtonDocksToThePanelEdgeAndFollowsTheWindow() {

		val B = dev.ashtonite365.apex.features.menus.ApexInventoryButton
		for (left in listOf(100, 177)) for (top in listOf(17, 40)) {
			val (x, y) = B.dock(left, top)
			assertEquals(left + 176 - 7, x + 20, "right edge on the inner panel edge"); assertEquals(top + 61, y, "recipe button row")
			assertTrue(x >= left + 104 + 20 + 2, "clear of the recipe button (when shown)")
			assertTrue(x >= left + 134, "clear of the 2x2 crafting grid (x 97..134)")
			assertTrue(y >= top + 55 && y + 18 <= top + 83, "below the crafting output, above the main inventory (y 84)")
		}
	}

	@Test fun apexButtonFrameFollowsTheMenuStyle() {
		val B = dev.ashtonite365.apex.features.menus.ApexInventoryButton
		val sky = StyleState(style = Style.SKYGLASS); val forge = StyleState(style = Style.FORGE); val off = StyleState(wrapAll = false)
		assertEquals(B.frameRects(0, 0, false).map { it.toList() }, B.styledFrameRects(0, 0, false, off).map { it.toList() }, "custom menus off = vanilla greys")
		val s = B.styledFrameRects(0, 0, false, sky); val f = B.styledFrameRects(0, 0, false, forge)
		assertTrue(s[4][4] != f[4][4], "Skyglass and Forge fills differ")
		assertEquals(StylePalette.of(sky).buttonFill, s[4][4]); assertEquals(StylePalette.of(forge).buttonFill, f[4][4])
		for (r in s + f) assertTrue(r[0] >= 0 && r[2] <= 20 && r[1] >= 0 && r[3] <= 18)
	}

	@Test fun recipeBookHiddenWithJeiOrRei() {
		val R = dev.ashtonite365.apex.features.menus.RecipeViewers
		assertEquals(listOf("jei", "roughlyenoughitems"), R.MOD_IDS)
		assertTrue(R.anyLoaded { it == "jei" }); assertTrue(R.anyLoaded { it == "roughlyenoughitems" })
		assertFalse(R.anyLoaded { it == "emi" }); assertFalse(R.anyLoaded { error("loader") })
		assertTrue(R.hideRecipeBook(false, true)); assertTrue(R.hideRecipeBook(true, false)); assertFalse(R.hideRecipeBook(false, false))
	}

	@Test fun apexButtonClickOpensApexAndToggleHidesIt() {
		val B = dev.ashtonite365.apex.features.menus.ApexInventoryButton
		var opened = 0; val old = B.open; B.open = { opened++ }
		try {
			var win = 100 to 17
			val b = dev.ashtonite365.apex.features.menus.ApexInventoryButton.Button { win }
			assertEquals(249, b.x); assertEquals(78, b.y); assertTrue(b.visible)
			b.onPress(net.minecraft.client.input.KeyEvent(0, 0, 0)); assertEquals(1, opened)
			win = 177 to 17; b.sync(); assertEquals(326, b.x)
			MenuStyle.update { it.copy(inventoryButton = false) }; b.sync()
			assertFalse(b.visible); b.onPress(net.minecraft.client.input.KeyEvent(0, 0, 0)); assertEquals(1, opened)
		} finally { B.open = old; MenuStyle.update { it.copy(inventoryButton = true) } }
	}

	@Test fun apexIconIsTheWhole32pxLogoAtTheRecipeButtonsSize() {
		val B = dev.ashtonite365.apex.features.menus.ApexInventoryButton
		for (name in listOf("apex_logo.png", "apex_logo_hover.png")) {
			val img = javax.imageio.ImageIO.read(javaClass.getResourceAsStream("/assets/apex/textures/gui/$name")!!)
			assertEquals(B.TEX, img.width); assertEquals(B.TEX, img.height)
			assertEquals(0, (img.getRGB(0, 0) ushr 24))
		}
		assertEquals(20, B.W); assertEquals(18, B.H)
		val r = B.iconRect(100, 50)
		assertEquals(listOf(103, 52, 117, 66), r.toList())
		val f = B.frameRects(100, 50, false); assertTrue(f.all { it[0] >= 100 && it[2] <= 120 && it[1] >= 50 && it[3] <= 68 })
		assertTrue(B.frameRects(100, 50, true)[0][4] != f[0][4])
		assertTrue(B.sprite(true) != B.sprite(false))

		val src = java.io.File("src/main/kotlin/features/menus/ApexInventoryButton.kt").takeIf { it.exists() }?.readText()
		if (src != null) assertFalse(src.contains("extractDefaultSprite("))
	}

	@Test fun settingsOpensOneAndAHalfTimesSlower() {
		for (a in MenuAnim.entries) {
			val s = StyleState(anim = a)
			assertEquals((StyleAnim.openMs(s) * 1.5).toLong(), StyleAnim.settingsOpenMs(s))
			assertTrue(StyleAnim.settingsOpenReveal(s, StyleAnim.openMs(s)) < 1.0)
			assertEquals(1.0, StyleAnim.settingsOpenReveal(s, StyleAnim.settingsOpenMs(s) + 1))
		}
		assertEquals(230, StyleAnim.openMs(StyleState()))
	}
}
