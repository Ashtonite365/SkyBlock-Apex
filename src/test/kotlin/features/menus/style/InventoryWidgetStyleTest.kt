// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.ApexInventoryButton
import dev.ashtonite365.apex.features.menus.style.ForgeMode
import dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InventoryWidgetStyleTest {
	private val sky = StyleState(style = Style.SKYGLASS)
	private val dark = StyleState(style = Style.FORGE, forgeMode = ForgeMode.DARK)
	private val light = StyleState(style = Style.FORGE, forgeMode = ForgeMode.LIGHT)
	private val off = StyleState(wrapAll = false)

	@Test fun lookComesFromThePalette() {
		for (s in listOf(dark, light)) {
			val p = StylePalette.of(s); val l = InventoryWidgetStyle.look(s)!!
			assertEquals(listOf(p.buttonEdge, p.buttonFill, p.accent, p.shadow, p.text, p.textDim), listOf(l.line, l.fill, l.hi, l.lo, l.text, l.textDim), "$s")
			val h = InventoryWidgetStyle.look(s, hovered = true)!!
			assertEquals(p.accent, h.line); assertEquals(p.buttonHover, h.fill)
		}

		assertNull(InventoryWidgetStyle.look(sky)); assertNull(InventoryWidgetStyle.look(sky, hovered = true))
		assertNotEquals(InventoryWidgetStyle.look(dark), InventoryWidgetStyle.look(light))
	}

	@Test fun plainLookWithCustomMenusOff() {
		assertNull(InventoryWidgetStyle.look(off))
		assertEquals(-1, InventoryWidgetStyle.textColourFor(off, -1))
		assertEquals(0xFF808080.toInt(), InventoryWidgetStyle.textColourFor(off, 0xFF808080.toInt()))
		assertEquals(ApexInventoryButton.frameRects(0, 0, false).map { it.toList() }, ApexInventoryButton.styledFrameRects(0, 0, false, off).map { it.toList() })
	}

	@Test fun effectPanelTextFollowsStyle() {
		assertEquals(-1, InventoryWidgetStyle.textColourFor(sky, -1), "Skyglass effect text as in bed7e11b")
		for (s in listOf(dark, light)) {
			val p = StylePalette.of(s)
			assertEquals(p.text, InventoryWidgetStyle.textColourFor(s, -1))
			assertEquals(p.textDim, InventoryWidgetStyle.textColourFor(s, 0xFF808080.toInt()))
		}
	}

	@Test fun apexButtonUsesTheSharedFrame() {
		for (s in listOf(dark, light)) for (hv in listOf(false, true)) {
			val shared = InventoryWidgetStyle.frameRects(5, 7, ApexInventoryButton.W, ApexInventoryButton.H, InventoryWidgetStyle.look(s, hv)!!)
			assertEquals(shared.map { it.toList() }, ApexInventoryButton.styledFrameRects(5, 7, hv, s).map { it.toList() })
		}
	}

	@Test fun frameCoversWidgetOnlyInPaletteColours() {
		val l = InventoryWidgetStyle.look(dark)!!
		val r = InventoryWidgetStyle.frameRects(10, 20, 120, 32, l)
		val allowed = setOf(l.line, l.fill, l.hi, l.lo)
		for (f in r) { assert(f[4] in allowed); assert(f[0] >= 10 && f[2] <= 130 && f[1] >= 20 && f[3] <= 52) }
	}

	@Test fun skyglassApexButtonAsInBed7e11b() {
		for (hv in listOf(false, true)) {
			val p = StylePalette.of(sky)
			val c = intArrayOf(if (hv) p.borderHover else p.buttonEdge, if (hv) p.buttonHover else p.buttonFill, p.rimHighlight, p.shadow)
			val old = ApexInventoryButton.frameRects(5, 7, hv).mapIndexed { i, r -> r.copyOf().also { it[4] = when (i) { in 0..3 -> c[0]; 4 -> c[1]; 5, 6 -> c[2]; else -> c[3] } } }
			assertEquals(old.map { it.toList() }, ApexInventoryButton.styledFrameRects(5, 7, hv, sky).map { it.toList() })
		}
		val f = ApexInventoryButton.styledFrameRects(5, 7, false, dark).map { it.toList() }
		assertNotEquals(ApexInventoryButton.styledFrameRects(5, 7, false, sky).map { it.toList() }, f, "Forge uses the Forge frame")
	}

	@Test fun vanillaModeSideWidgetsUseTheVanillaPanelNoCyan() {
		val v = StyleState(customMenus = false)
		val l = InventoryWidgetStyle.frameLook(v)!!
		assertEquals(InventoryWidgetStyle.VANILLA, l)
		for (c in listOf(l.line, l.fill, l.hi, l.lo)) {
			val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
			assertEquals(r, g, "grey, no tint"); assertEquals(g, b, "grey, no tint"); assertEquals(255, (c ushr 24))
		}
		assertNull(InventoryWidgetStyle.frameLook(sky), "Skyglass keeps its own sprites")
		assertEquals(InventoryWidgetStyle.look(dark), InventoryWidgetStyle.frameLook(dark))
	}
}
