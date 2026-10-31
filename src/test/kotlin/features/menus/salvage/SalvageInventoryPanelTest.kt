// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.salvage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.h6.HubLayout
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.salvage.MalikView
import dev.ashtonite365.apex.features.menus.salvage.SalvageMenu
import dev.ashtonite365.apex.features.menus.salvage.SalvageView
import dev.ashtonite365.apex.features.menus.support.InventoryPanel

class SalvageInventoryPanelTest {
	@Test fun salvageOptsIntoTheSharedPanelOnly() {

		assertNotNull(SalvageView::class.java.getDeclaredMethod("showsInventory", HubModel::class.java))
		assertTrue(runCatching { MalikView::class.java.getDeclaredMethod("showsInventory", HubModel::class.java) }.isFailure)
		assertEquals(HubView::class.java, HubView::class.java.getDeclaredMethod("showsInventory", HubModel::class.java).declaringClass)
		assertEquals(230 + InventoryPanel.RESERVED, SalvageMenu.defaultSize.second)
	}

	@Test fun everyPlayerSlotIsAPanelCell() {
		val origin = 100 to 200
		val placed = (54 until 90).map { InventoryPanel.slotPlacement(it, 90, origin, 0, 0) }
		assertTrue(placed.all { it != null })
		assertEquals(36, placed.toSet().size)
		assertEquals(null, InventoryPanel.slotPlacement(40, 90, origin, 0, 0))
	}

	@Test fun onScreenAtCommonGuiScales() {
		val (dw, dh) = SalvageMenu.defaultSize
		for ((sw, sh) in listOf(960 to 540, 640 to 360, 480 to 270, 683 to 384, 455 to 256, 1280 to 720)) {
			val b = MenuBounds.centred(sw, sh, dw, dh)
			assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= sw && b.y + b.h <= sh, "$sw x $sh: $b")
			val bottom = b.y + b.h - 22 - InventoryPanel.RESERVED
			val panelTop = bottom + 2; val panelEnd = panelTop + InventoryPanel.HEIGHT
			val ix = b.x + (b.w - InventoryPanel.WIDTH) / 2
			assertTrue(panelTop >= b.y + 22, "$sw x $sh: panel under the header")
			assertTrue(panelEnd <= HubLayout.navTop(b.y, b.h), "$sw x $sh: panel above the nav row")
			assertTrue(ix >= b.x && ix + InventoryPanel.WIDTH <= b.x + b.w, "$sw x $sh: panel inside horizontally")
		}
	}
}
