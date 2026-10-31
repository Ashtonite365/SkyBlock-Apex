package dev.ashtonite365.apex.test.features.storage

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageClickLogic
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageClickLogic.PageRect
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageClickLogic.Placement

class StorageOverlayFixTest {
	private val panel = intArrayOf(10, 10, 300, 200)
	private val rects = listOf(
		PageRect(14, 14, 100, 50, "ec1"), PageRect(118, 14, 100, 50, "ec2"), PageRect(14, 68, 100, 50, "bp1"),
	)

	@Test
	fun leftClickOnPageOpensIt() {
		assertEquals("ec2", StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_LEFT, null))
		assertEquals("bp1", StorageClickLogic.pageToOpen(rects, panel, 14.0, 68.0, InputConstants.MOUSE_BUTTON_LEFT, "ec1"))
	}

	@Test
	fun noOpenForOtherButtonsActivePageGapsOrOutsidePanel() {
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_RIGHT, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_LEFT, "ec2"))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 116.0, 30.0, InputConstants.MOUSE_BUTTON_LEFT, null))
		assertNull(StorageClickLogic.pageToOpen(rects, intArrayOf(10, 40, 300, 200), 150.0, 30.0, InputConstants.MOUSE_BUTTON_LEFT, null))
	}

	@Test
	fun onlyPlayerSlotsGoToThePlayerInventory() {
		assertEquals(Placement.Player(3), StorageClickLogic.placeSlot(true, 3, true))
		assertEquals(Placement.Player(35), StorageClickLogic.placeSlot(true, 35, false))

		assertEquals(Placement.Selector(4), StorageClickLogic.placeSlot(false, 4, true))
		assertEquals(Placement.Elsewhere, StorageClickLogic.placeSlot(false, 20, true))
		assertEquals(Placement.Elsewhere, StorageClickLogic.placeSlot(false, 4, false))
	}

	@Test
	fun selectorSlotsDoNotOverlap() {
		val ps = (0..<9).map { StorageClickLogic.selectorPosition(100, 50, it) }
		assertTrue(ps.all { it.first + 18 <= 100 })
		ps.zipWithNext().forEach { (a, b) -> assertTrue(b.second - a.second >= 18) }
	}

	@Test
	fun sdlButtonNumbersOpenOnlyOnLeft() {

		assertEquals("ec2", StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_LEFT, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_RIGHT, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, InputConstants.MOUSE_BUTTON_MIDDLE, null))
		assertNull(StorageClickLogic.pageToOpen(rects, panel, 150.0, 30.0, 0, null))
	}
}
