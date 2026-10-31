// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import dev.ashtonite365.apex.features.inventory.LockIcons

import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay.LockKind
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleState
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LockIconsTest {
	private val dir = File(File(System.getProperty("apex.projectDir") ?: "."), "src/main/resources/assets/apex/textures/gui/sprites")

	@Test fun `icons are 6px, about a third of the 16px slot`() {
		val p = LockIcons.place(0, 0)
		assertEquals(6, p.size)
		for (k in LockKind.entries) for (s in Style.entries) {
			val f = File(dir, "${k.base}_${s.id}.png"); assertTrue(f.isFile, "$f")
			val img = ImageIO.read(f); assertEquals(6, img.width, "$f"); assertEquals(6, img.height, "$f")
			var opaque = 0
			for (x in 0 until 6) for (y in 0 until 6) if ((img.getRGB(x, y) ushr 24) > 0) opaque++
			assertTrue(opaque >= 12, "$f looks empty")
		}
		assertFalse(File(dir, "slot_locked.png").exists()); assertFalse(File(dir, "uuid_locked.png").exists())
	}

	@Test fun `bottom-left corner, 1px inside the slot, clear of the stack count`() {
		for ((x, y) in listOf(0 to 0, 8 to 84, 100 to 37)) {
			val p = LockIcons.place(x, y, 16)
			assertEquals(x + 1, p.x); assertEquals(y + 16 - 1 - 6, p.y)
			assertTrue(p.x >= x && p.y >= y && p.x + p.size <= x + 16 && p.y + p.size <= y + 16)
			assertTrue(p.x + p.size <= x + 8, "left half: the count is drawn right-aligned")
			assertTrue(p.y >= y + 8, "bottom half")
		}
		for (slot in listOf(8, 12, 18, 24, 32)) {
			val p = LockIcons.place(10, 20, slot)
			assertTrue(p.x > 10 && p.y + p.size < 20 + slot && p.x + p.size <= 10 + slot / 2 + 1 && p.y >= 20 + slot / 2 - 1, "slot $slot: $p")
		}
	}

	@Test fun `follows the menu style, Skyglass when custom menus are off`() {
		assertEquals("apex:slot_locked_skyglass", LockIcons.sprite(LockKind.SLOT, StyleState(style = Style.SKYGLASS)))
		assertEquals("apex:slot_locked_forge", LockIcons.sprite(LockKind.SLOT, StyleState(style = Style.FORGE)))
		assertEquals("apex:uuid_locked_skyglass", LockIcons.sprite(LockKind.UUID, StyleState(style = Style.SKYGLASS)))
		assertEquals("apex:uuid_locked_forge", LockIcons.sprite(LockKind.UUID, StyleState(style = Style.FORGE)))
		for (s in Style.entries) for (k in LockKind.entries)
			assertEquals("apex:${k.base}_skyglass", LockIcons.sprite(k, StyleState(style = s, wrapAll = false)))
	}
}
