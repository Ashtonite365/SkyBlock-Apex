// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import dev.ashtonite365.apex.features.menus.DropdownInput
import dev.ashtonite365.apex.features.menus.VanillaStyleDropdown
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleChoice
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.test.testutil.RepoFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DropdownInputTest {
	private val tmp: java.io.File = java.nio.file.Files.createTempDirectory("apex-dropin").toFile()
	@BeforeEach fun setUp() { MenuStyle.file = java.io.File(tmp, "menu-style.json"); MenuStyle.resetForTests(); MenuStyle.refreshScreen = {}; DropdownInput.resetForTests() }
	@AfterEach fun tearDown() { MenuStyle.file = null; MenuStyle.resetForTests(); DropdownInput.resetForTests(); tmp.deleteRecursively() }

	private val head = intArrayOf(100, 1, 66, 12)
	private val rows = (0 until 3).map { intArrayOf(100, 1 + 12 * (it + 1), 66, 12) }

	@Test fun openListConsumesEveryPress() {

		for (i in rows.indices) for ((dx, dy) in listOf(0 to 0, 65 to 11, 30 to 6)) {
			val o = DropdownInput.decide(true, head, rows, rows[i][0] + dx + 0.0, rows[i][1] + dy + 0.0, 0)
			assertEquals(DropdownInput.Outcome(true, false, i), o, "row $i +$dx,$dy")
		}

		assertEquals(DropdownInput.Outcome(true, false, null), DropdownInput.decide(true, head, rows, 10.0, 200.0, 0))

		assertEquals(DropdownInput.Outcome(true, false, null), DropdownInput.decide(true, head, rows, 110.0, 20.0, 1))

		assertEquals(DropdownInput.Outcome(true, false, null), DropdownInput.decide(true, head, rows, 110.0, 5.0, 0))
	}

	@Test fun closedOnlyTakesTheHead() {
		assertEquals(DropdownInput.Outcome(true, true, null), DropdownInput.decide(false, head, rows, 110.0, 5.0, 0))
		assertEquals(DropdownInput.Outcome(false, false, null), DropdownInput.decide(false, head, rows, 110.0, 20.0, 0), "rows are not there while closed")
		assertEquals(DropdownInput.Outcome(false, false, null), DropdownInput.decide(false, head, rows, 110.0, 5.0, 1))
	}

	@Test fun claimedPressSwallowsItsRelease() {
		assertFalse(DropdownInput.press(null, 1.0, 1.0, 0))
		assertFalse(DropdownInput.release(0), "nothing claimed: the release goes through")
	}

	@Test fun vanillaDropdownOpenNeverLeaks() {
		val (x, y) = VanillaStyleDropdown.place(152, 60, 176)
		assertEquals(true to false, VanillaStyleDropdown.click(x, y, true, 1.0, 1.0), "outside an open list: consumed, closed")
		assertEquals(false to false, VanillaStyleDropdown.click(x, y, false, 1.0, 1.0), "closed: not ours")
		val r = VanillaStyleDropdown.listRect(x, y)
		val i = StyleChoice.entries.indexOf(StyleChoice.FORGE)
		assertEquals(true to false, VanillaStyleDropdown.click(x, y, true, r[0] + 3.0, VanillaStyleDropdown.rowY(r, i) + 2.0))
		assertEquals(StyleChoice.FORGE, StyleChoice.of(MenuStyle.state))
	}

	@Test fun styleBarOpenNeverLeaks() {
		val bar = StyleBar(); val g = StyleBar.geometry(300, 10, StyleState(), 200)
		assertTrue(bar.click(g, g.dropX + 1.0, g.y + 1.0)); assertTrue(bar.open)
		assertTrue(bar.click(g, 1.0, 199.0), "outside: consumed"); assertFalse(bar.open)
	}

	@Test fun wiredBeforeTheScreenAndOverlays() {
		val mixin = RepoFile("src/main/java/dev/ashtonite365/apex/mixins/DropdownInputFirstPatch.java").readText()
		assertTrue(mixin.contains("method = \"onButton\", at = @At(\"HEAD\"), cancellable = true"))
		assertTrue(mixin.contains("DropdownInput.release(button)") && mixin.contains("ci.cancel()"))
		val layer = RepoFile("src/main/kotlin/features/menus/DropdownLayer.kt").readText()
		assertTrue(layer.contains("d.claim(mx, my, button)") && layer.contains("session.chrome.bar.open"))
		val jei = RepoFile("src/compat/jei/java/dev/ashtonite365/apex/compat/jei/style/JeiStyleDraw.kt").readText()
		assertTrue(jei.contains("DropdownInput.register(") && !jei.contains("allowMouseClick"))

		assertTrue(jei.contains("fun rows(panelRight: Int): List<IntArray> = JeiLook.entries.indices.map { row(panelRight, it) }"))
	}

	@Test fun vanillaModeDropdownTakesTheRealLeftButton() {
		val left = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
		val right = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT
		assertTrue(VanillaStyleDropdown.isLeftPress(left))
		assertFalse(VanillaStyleDropdown.isLeftPress(right))
		if (left != 0) assertFalse(VanillaStyleDropdown.isLeftPress(0), "the packet value 0 is not the mouse-event left button")

		val (x, y) = 10 to 10
		val r = VanillaStyleDropdown.listRect(x, y)
		val (ours, open) = VanillaStyleDropdown.click(x, y, true, r[0] + 5.0, VanillaStyleDropdown.rowY(r, 1) + 2.0)
		assertTrue(ours); assertFalse(open)
		val src = RepoFile("src/main/kotlin/features/menus/VanillaStyleDropdown.kt").readText()
		val claim = src.substringAfter("fun claim(").substringBefore("override fun mouseClicked")
		assertTrue(claim.contains("isLeftPress(button)"))
		assertFalse(claim.contains("button != 0"), "no packet-value comparison on a raw mouse button")
	}
}
