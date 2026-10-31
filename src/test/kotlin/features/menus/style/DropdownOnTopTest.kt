// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.style

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.VanillaStyleDropdown
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.RecordingCanvas
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleChoice
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.style.*

class DropdownOnTopTest {
	private val tmp: java.io.File = java.nio.file.Files.createTempDirectory("apex-dropdown").toFile()
	@org.junit.jupiter.api.BeforeEach fun setUp() { MenuStyle.file = java.io.File(tmp, "menu-style.json"); MenuStyle.resetForTests(); MenuStyle.refreshScreen = {} }
	@org.junit.jupiter.api.AfterEach fun tearDown() { MenuStyle.file = null; MenuStyle.resetForTests(); tmp.deleteRecursively() }
	private fun inside(r: IntArray, sw: Int, sh: Int) = r[0] >= 0 && r[1] >= 0 && r[0] + r[2] <= sw && r[1] + r[3] <= sh

	@Test fun vanillaListStaysOnScreen() {
		val (sw, sh) = 480 to 270

		val (x, y) = VanillaStyleDropdown.place(152, 60, 176)
		val r = VanillaStyleDropdown.listRect(x, y, sw, sh)
		assertTrue(inside(r, sw, sh)); assertEquals(y + VanillaStyleDropdown.H, r[1])
		assertEquals(StyleChoice.entries.size * VanillaStyleDropdown.ITEM_H + 2, r[3], "every row fits, none cut")

		val f = VanillaStyleDropdown.listRect(x, sh - 14, sw, sh)
		assertTrue(inside(f, sw, sh)); assertTrue(f[1] + f[3] <= sh - 14, "flipped above")

		val e = VanillaStyleDropdown.listRect(sw - 20, 10, sw, sh)
		assertTrue(inside(e, sw, sh))

		val skyI = StyleChoice.entries.indexOf(StyleChoice.SKYGLASS)
		assertEquals(true to false, VanillaStyleDropdown.click(x, sh - 14, true, f[0] + 2.0, VanillaStyleDropdown.rowY(f, skyI) + 1.0, sw, sh))
	}

	@Test fun vanillaListIsItsOwnTopLayer() {
		val c = RecordingCanvas()
		val r = VanillaStyleDropdown.listRect(10, 10, 300, 200)
		VanillaStyleDropdown.paintList(c, r, StyleState(customMenus = false), 0, 0)
		assertEquals("L", c.ops.first(), "starts a new layer above the chest, slots and items")
		StyleChoice.entries.forEach { ch -> assertTrue(c.ops.any { it.startsWith("T ") && it.endsWith(" ${ch.label}") }, ch.label) }

		val look = VanillaStyleDropdown.listLook(StyleState(customMenus = false))
		assertNotEquals(look.bg, look.text)

		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/VanillaStyleDropdown.kt").readText()
		val widgetPass = src.substringAfter("override fun extractWidgetRenderState").substringBefore("fun drawLate")
		assertFalse(widgetPass.contains("paintList") || widgetPass.contains("StyleChoice.entries"), "rows not drawn in the widget layer")
		assertTrue(src.contains("DropdownLayer.attach(screen, d)"), "drawn in Apex's after-screen pass")

		for (st in listOf(StyleState(customMenus = false), StyleState(), StyleState(style = dev.ashtonite365.apex.features.menus.style.Style.FORGE)))
			assertEquals(0xFF, VanillaStyleDropdown.listLook(st).bg ushr 24, "$st")
	}

	@Test fun layerOrder() {
		val L = dev.ashtonite365.apex.features.menus.DropdownLayer.Layer.entries.map { it.name }
		assertEquals(listOf("GUI", "DROPDOWN", "ITEM_LIST_OVERLAY", "WIKI_OVERLAY", "TOOLTIP"), L)
		val ran = mutableListOf<String>()
		dev.ashtonite365.apex.features.menus.DropdownLayer.runApexPass({ ran += "gui" }, { ran += "dropdown" })
		assertEquals(listOf("gui", "dropdown"), ran)
		val apex = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/Apex.kt").readText()

		assertTrue(apex.contains("addPhaseOrdering(dev.ashtonite365.apex.features.menus.DropdownLayer.PHASE, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE)"))
		assertTrue(apex.contains("after.register(dev.ashtonite365.apex.features.menus.DropdownLayer.PHASE"))

		val tip = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/util/render/TooltipTopLayer.kt").readText()
		assertTrue(tip.contains("postponed and run once, after every after-screen overlay"))

		val c = RecordingCanvas(); val bar = StyleBar()
		val g = StyleBar.geometry(300, 10, StyleState(), 200)
		bar.click(g, g.dropX + 1.0, g.y + 1.0)
		bar.drawList(c, dev.ashtonite365.apex.features.menus.style.StylePalette.of(StyleState()), StyleState(), g, 0, 0)
		assertEquals("L", c.ops.first())
		val fills = c.ops.filter { it.startsWith("F ") }
		val face = Integer.toHexString(dev.ashtonite365.apex.features.menus.style.StylePalette.of(StyleState()).raised or (0xFF shl 24))
		assertTrue(fills.any { it.endsWith(" $face") }, "opaque face $face in $fills")
	}

	@Test fun styleBarListFlipsInsideTheScreen() {
		val s = StyleState()
		val sh = 200
		val low = StyleBar.geometry(300, sh - 16, s, sh)
		val r = StyleBar().listRect(low)
		assertTrue(r[1] >= 0 && r[1] + r[3] <= sh, r.toList().toString())
		assertTrue(r[1] + r[3] <= low.y, "above the button")
		val top = StyleBar.geometry(300, 10, s, sh)
		assertEquals(top.y + top.h + 2, top.itemY(0), "below when there is room")
		assertEquals(StyleBar.geometry(300, 10, s), top)
	}

	@Test fun rendererFollowsActiveStyle() {
		val V = VanillaStyleDropdown
		val sky = StyleState(); val forge = StyleState(style = Style.FORGE); val forgeLight = forge.copy(forgeMode = ForgeMode.LIGHT)
		val vanilla = StyleState(customMenus = false)
		assertEquals(VanillaStyleDropdown.Renderer.STYLE_BAR, V.rendererFor(sky)); assertEquals(VanillaStyleDropdown.Renderer.STYLE_BAR, V.rendererFor(forge))
		assertEquals(VanillaStyleDropdown.Renderer.PLAIN, V.rendererFor(vanilla)); assertNull(V.paletteFor(vanilla))
		for (st in listOf(sky, forge, forgeLight)) {
			val p = StylePalette.of(st)
			assertEquals(p, V.paletteFor(st))

			val c = RecordingCanvas(); V.paintHead(c, p, "Skyglass", 10, 10, 64, 12, hover = false, open = false)
			val look = StyleWidgets.buttonLook(p, StyleWidgets.press(false, false, false))
			val pill = RecordingCanvas(); Shapes.pill(pill, 10, 10, 64, 12, look.fill, look.border)
			assertEquals(pill.ops, c.ops.filter { !it.startsWith("T ") }, "$st head is the StyleBar pill")
			assertTrue(c.ops.filter { it.startsWith("T ") }.all { it.split(" ")[3] == Integer.toHexString(look.text) }, "$st head text in the style's colour")
			assertFalse(c.ops.any { it.endsWith(Integer.toHexString(0xFFC6C6C6.toInt())) }, "$st not the plain grey box")

			val rc = RecordingCanvas(); V.paintList(rc, V.listRect(10, 10), st, 0, 0)
			assertEquals("L", rc.ops.first())
			assertEquals(0xFF, V.listLook(st).bg ushr 24); assertEquals(p.text, V.listLook(st).text)
		}

		val pc = RecordingCanvas(); V.paintHead(pc, null, "Vanilla", 10, 10, 64, 12, false, false)
		assertTrue(pc.ops.any { it.endsWith(Integer.toHexString(0xFFC6C6C6.toInt())) })

		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/VanillaStyleDropdown.kt").readText()
		assertTrue(src.substringAfter("override fun extractWidgetRenderState").substringBefore("fun drawLate").contains("paintHead(") )
		assertTrue(src.substringAfter("override fun extractWidgetRenderState").substringBefore("fun drawLate").contains("paletteFor(st)"))

		assertNull(JeiStyle.paletteState(JeiStyleState(JeiLook.VANILLA)))
		assertEquals(Style.SKYGLASS, JeiStyle.paletteState(JeiStyleState(JeiLook.SKYGLASS))!!.style)
		assertEquals(ForgeMode.LIGHT, JeiStyle.paletteState(JeiStyleState(JeiLook.FORGE, forgeDark = false))!!.forgeMode)
		val jei = dev.ashtonite365.apex.test.testutil.RepoFile("src/compat/jei/java/dev/ashtonite365/apex/compat/jei/style/JeiStyleDraw.kt").readText()
		assertTrue(jei.contains("VanillaStyleDropdown.paintHead(") && jei.contains("VanillaStyleDropdown.paintRows("))
	}
}
