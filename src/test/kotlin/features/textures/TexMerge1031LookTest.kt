// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay.StorageTextureStyle
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayGeometry
import dev.ashtonite365.apex.features.menus.style.ForgeMode
import dev.ashtonite365.apex.features.menus.style.InventoryTexture
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleState
import me.shedaniel.math.Rectangle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TexMerge1031LookTest {
	private fun overlaps(a: Rectangle, b: Rectangle) =
		a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

	@Test fun styledControlsNeverOverlapThePlayerInventoryOrFrame() {
		for ((w, h) in listOf(960 to 540, 640 to 360, 1280 to 720, 480 to 270, 854 to 480)) for (cols in 1..4) for (hs in listOf(100, 200, 400, 1000)) {
			val g = StorageOverlayGeometry(w, h, cols, hs, separateControls = true)
			val box = g.controlsBackground()
			assertFalse(overlaps(box, g.playerPanel()), "controls vs player at $w x $h cols=$cols h=$hs: $box ${g.playerPanel()}")
			assertFalse(overlaps(box, g.frame()), "controls vs frame at $w x $h cols=$cols h=$hs")
			assertTrue(box.x + box.width <= g.playerX, "controls left of the player inventory")
			val area = g.controls()
			assertTrue(area.x >= box.x && area.x + area.width <= box.x + box.width && area.y + area.height <= box.y + box.height)
			assertTrue(g.bounds().contains(box))
		}
	}

	@Test fun theOnlyShippedStorageSetIsStyled() {
		assertTrue(StorageTextureStyle.entries.all { it.followsMenuStyle })
	}

	@Test fun classicPlacementUnchanged() {
		val g = StorageOverlayGeometry(960, 540, 3, 400)
		assertEquals(g.playerX - StorageOverlayGeometry.CONTROLS_WIDTH + 3, g.controlsX)
		assertEquals(g.playerY - 5, g.controlsY)
	}

	@Test fun labelColourFollowsTheStyleOnlyWithCustomMenus() {
		val grey = InventoryTexture.VANILLA_LABEL
		val sky = StyleState(style = Style.SKYGLASS)
		assertEquals(StylePalette.SKYGLASS.text, InventoryTexture.labelColourFor(sky, grey))
		assertNotEquals(grey, InventoryTexture.labelColourFor(sky, grey))

		val c = InventoryTexture.labelColourFor(sky, grey)
		assertTrue(((c shr 16 and 255) + (c shr 8 and 255) + (c and 255)) / 3 > 180)
		assertEquals(StylePalette.FORGE_DARK.text, InventoryTexture.labelColourFor(StyleState(style = Style.FORGE, forgeMode = ForgeMode.DARK), grey))

		assertEquals(grey, InventoryTexture.labelColourFor(StyleState(style = Style.SKYGLASS, wrapAll = false), grey))
		assertEquals(grey, InventoryTexture.labelColourFor(StyleState(style = Style.FORGE, wrapAll = false), grey))

		assertEquals(0xFFFFFFFF.toInt(), InventoryTexture.labelColourFor(sky, 0xFFFFFFFF.toInt()))
	}
}
