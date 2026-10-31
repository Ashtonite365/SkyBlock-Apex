// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.textures

import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageBackArrow
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageBackChain.Item
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageNavButtons
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay.StorageTextureStyle
import dev.ashtonite365.apex.features.menus.style.InventoryTexture
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@org.junit.jupiter.api.extension.ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)

class Textures1031Test {
	private val menuWithBack = listOf(null, Item("Diamond"), null, Item("§aGo Back", listOf("To Storage")), Item("Close"))
	private val menuWithoutBack = listOf(null, Item("Diamond"), Item("Close"))

	private fun count(items: List<Item?>?): Triple<List<Int>, Int, StorageBackArrow.Action> {
		val clicks = mutableListOf<Int>(); var closes = 0
		val a = StorageBackArrow.press(items, { clicks += it }, { closes++ })
		return Triple(clicks, closes, a)
	}

	@Test fun goBackPresentIsExactlyOneClickNoClose() {
		val (clicks, closes, a) = count(menuWithBack)
		assertEquals(listOf(3), clicks); assertEquals(0, closes); assertEquals(StorageBackArrow.Action.GO_BACK, a)
	}

	@Test fun noGoBackIsExactlyOneCloseNoClick() {
		val (clicks, closes, a) = count(menuWithoutBack)
		assertTrue(clicks.isEmpty()); assertEquals(1, closes); assertEquals(StorageBackArrow.Action.CLOSE, a)
	}

	@Test fun standaloneIsOneClose() {
		val (clicks, closes, _) = count(null)
		assertTrue(clicks.isEmpty()); assertEquals(1, closes)
	}

	@Test fun repeatedPressesNeverMultiply() {
		repeat(5) { val (c, cl, _) = count(menuWithBack); assertEquals(1, c.size + cl) }
		repeat(5) { val (c, cl, _) = count(menuWithoutBack); assertEquals(1, c.size + cl) }
	}

	@Test fun decideMatchesPress() {
		assertEquals(StorageBackArrow.Action.GO_BACK, StorageBackArrow.decide(menuWithBack))
		assertEquals(StorageBackArrow.Action.CLOSE, StorageBackArrow.decide(menuWithoutBack))
		assertEquals(StorageBackArrow.Action.CLOSE, StorageBackArrow.decide(null))
	}

	@Test fun arrowIsAlwaysLeftOfFrameAndHittable() {
		for ((fx, fy) in listOf(40 to 10, 200 to 30, 23 to 0)) {
			val r = StorageBackArrow.rect(fx, fy)
			assertTrue(r[0] + r[2] <= fx, "arrow outside the frame")
			assertTrue(StorageBackArrow.hit(fx, fy, r[0] + 1.0, r[1] + 1.0))
			assertFalse(StorageBackArrow.hit(fx, fy, fx + 1.0, fy + 1.0))
		}

		val back = StorageNavButtons.rect(100, 50, StorageNavButtons.Nav.FIRST)
		val arrow = StorageBackArrow.rect(100, 50)
		assertTrue(arrow[1] + arrow[3] <= back[1])
	}

	@Test fun storageStandardFollowsMenuStyle() {
		assertEquals("storageoverlay/upper_background", StorageTextureStyle.STANDARD.spritePath("upper_background", Style.SKYGLASS))
		assertEquals("storageoverlay/forge/upper_background", StorageTextureStyle.STANDARD.spritePath("upper_background", Style.FORGE))
		assertTrue(StorageTextureStyle.STANDARD.followsMenuStyle)
	}

	@Test fun inventoryTextureFollowsStyleOnlyWithCustomMenus() {
		assertEquals(InventoryTexture.SKYGLASS_PATH, InventoryTexture.pathFor(StyleState(style = Style.SKYGLASS)))
		assertEquals(InventoryTexture.FORGE_PATH, InventoryTexture.pathFor(StyleState(style = Style.FORGE)))
		assertNull(InventoryTexture.pathFor(StyleState(style = Style.FORGE, wrapAll = false)))
		assertNull(InventoryTexture.pathFor(StyleState(style = Style.SKYGLASS, wrapAll = false)))
	}

	private val res = File(File(System.getProperty("apex.projectDir") ?: "."), "src/main/resources/assets/apex/textures/gui")
	private fun png(rel: String): BufferedImage = ImageIO.read(File(res, rel)) ?: error("missing $rel")

	@Test fun newTexturesKeepNamesAndSizes() {
		val sizes = mapOf(
			"sprites/storageoverlay/upper_background.png" to (176 to 222), "sprites/storageoverlay/storage_row.png" to (162 to 18),
			"sprites/storageoverlay/scroll_bar_background.png" to (17 to 18), "sprites/storageoverlay/scroll_bar_knob.png" to (8 to 16),
			"sprites/storageoverlay/player_inventory.png" to (184 to 91), "sprites/storageoverlay/storage_controls.png" to (91 to 184),
			"sprites/inventory_button_background.png" to (18 to 18), "sprites/generic_vanilla_widget.png" to (24 to 41),
			"sprites/widget/button_active.png" to (200 to 20), "sprites/widget/checkbox_checked.png" to (16 to 16),
			"bar.png" to (64 to 64), "circle.png" to (32 to 32), "waypoint_selected.png" to (16 to 16),
			"apex/inventory_skyglass.png" to (256 to 256), "apex/inventory_forge.png" to (256 to 256),
		)
		for ((rel, wh) in sizes) {
			val i = png(rel); assertEquals(wh, i.width to i.height, rel)
			if (rel.startsWith("sprites/storageoverlay/")) {
				val f = png(rel.replace("storageoverlay/", "storageoverlay/forge/")); assertEquals(wh, f.width to f.height, "forge $rel")
			}
		}
	}

	@Test fun forgeCornersPokeOutAndSkyglassIsTranslucent() {
		val forge = png("sprites/storageoverlay/forge/upper_background.png")
		assertEquals(255, forge.getRGB(0, 0) ushr 24)
		assertEquals(0, forge.getRGB(20, 0) ushr 24)
		val glass = png("sprites/storageoverlay/upper_background.png")
		val a = glass.getRGB(88, 111) ushr 24
		assertTrue(a in 60..220, "Skyglass body is translucent, alpha=$a")
	}
}
