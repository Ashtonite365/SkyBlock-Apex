package dev.ashtonite365.apex.util.render

import me.shedaniel.math.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChrome
import dev.ashtonite365.apex.features.menus.style.StylePalette

@org.junit.jupiter.api.extension.ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class SidePanelStyleTest {
	@Test fun customMenusOffKeepsThePlainLookExactly() {
		assertSame(SidePanel.LIGHT, SidePanel.paletteFor(false, SidePanel.LIGHT) { error("style not read when off") })
		assertSame(SidePanel.DARK, SidePanel.paletteFor(false, SidePanel.DARK) { StylePalette.SKYGLASS })
		assertFalse(SidePanel.LIGHT.themed)
	}

	@Test fun customMenusOnFollowsTheSelectedStyle() {
		for (sp in listOf(StylePalette.SKYGLASS, StylePalette.FORGE_DARK, StylePalette.FORGE_LIGHT)) {
			val p = SidePanel.paletteFor(true, SidePanel.LIGHT) { sp }
			assertTrue(p.themed)
			assertEquals(sp.card, p.fill); assertEquals(sp.border, p.rim); assertEquals(sp.text, p.text)
			assertEquals(sp.blurWorld, p.frost)
		}
	}

	@Test fun panelDocksBesideMenuAndItsTopBar() {

		val (right, barY) = MenuChrome.barAnchor(100, 60, 200)
		val anchor = SidePanel.union(listOf(Rectangle(100, 60, 200, 120), Rectangle(right - 60, barY, 60, 14)))!!
		assertEquals(barY, anchor.y)
		assertEquals(300, anchor.x + anchor.width)
		assertTrue(anchor.y < 60)
	}
}
