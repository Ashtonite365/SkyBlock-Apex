// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.style.ForgeMode
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.support.MenuText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StyleBarIconAndLabelTest {
	private val icon = 8

	@Test fun iconClearOfDropdownAndToggleInBothForgeModes() {
		for (m in ForgeMode.entries) {
			val s = StyleState(style = Style.FORGE, forgeMode = m)
			val g = StyleBar.geometry(400, 10, s)
			assertEquals(StyleBar.DROP_W_FORGE, g.dropW)
			assertTrue(g.dropW > StyleBar.DROP_W, "Forge dropdown is a little longer")
			val ix = StyleBar.iconX(g)
			assertTrue(ix >= g.dropX + g.dropW + 3, "gap after the dropdown ($m)")
			assertTrue(ix + icon <= g.switchX - 1, "gap before the toggle ($m)")
			assertEquals(400, g.switchX + StyleBar.SWITCH_W)
			assertEquals(StyleBar().width(s), 400 - g.dropX)
			val tip = StyleBar.switchTooltip(g, s, g.switchX + 3.0, g.y + 3.0)
			assertEquals("Forge: " + (if (m == ForgeMode.DARK) "Dark" else "Light") + " (click to switch)", tip)
			assertEquals(tip, StyleBar.switchTooltip(g, s, ix + 1.0, g.y + 3.0), "the icon shows it too")
		}
		val sky = StyleState(style = Style.SKYGLASS)
		val g = StyleBar.geometry(400, 10, sky)
		assertEquals(-1, g.switchX); assertNull(StyleBar.switchTooltip(g, sky, 395.0, 12.0))
	}

	private val px: (String) -> Int = { it.length * 6 }

	@Test fun labelsWrapWholeWords() {
		val f = MenuText.fitLabel("Auctions Browser", 60, px)
		assertEquals(listOf("Auctions", "Browser"), f.lines); assertEquals(1f, f.scale)
		val g = MenuText.fitLabel("Manage Auctions", 66, px)
		assertEquals(listOf("Manage", "Auctions"), g.lines)
		assertEquals(listOf("Close"), MenuText.fitLabel("Close", 60, px).lines)
	}

	@Test fun longWordScalesDownNeverCut() {
		val f = MenuText.fitLabel("Superlongwordlabel", 60, px)
		assertTrue(f.scale < 1f)
		assertEquals("Superlongwordlabel", f.lines.joinToString(" "))
		assertTrue(f.lines.all { px(it) * f.scale <= 60 })
		val one = MenuText.fitLabel("Auctions Browser", 60, px, maxH = 9)
		assertEquals(1, one.lines.size, "only one line fits: scaled instead"); assertTrue(px(one.lines[0]) * one.scale <= 60)
	}

	@Test fun everyWordKept() {
		for (t in listOf("Manage Auctions", "Auctions Browser", "View Recently Sold Auctions Now")) for (w in listOf(30, 50, 80))
			assertEquals(t, MenuText.fitLabel(t, w, px).lines.joinToString(" "))
	}
}
