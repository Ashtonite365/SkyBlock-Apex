package dev.ashtonite365.apex.test.features.menus.support

import net.minecraft.world.inventory.ContainerInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.h6.BagsParser

class InventoryPanelTest {
	@Test fun clicksForwardAsNormalContainerClicks() {
		assertEquals(0 to ContainerInput.PICKUP, InventoryPanel.route(0, false))
		assertEquals(1 to ContainerInput.PICKUP, InventoryPanel.route(1, false))
		assertEquals(0 to ContainerInput.QUICK_MOVE, InventoryPanel.route(0, true))
		assertEquals(1 to ContainerInput.QUICK_MOVE, InventoryPanel.route(1, true))
		assertEquals(0 to ContainerInput.PICKUP, InventoryPanel.route(7, false))
	}

	@Test fun slotsMapToTheRealPlayerInventorySlots() {
		val chest = InventoryPanel.chestSlots(54 + 36)
		assertEquals(54, chest)
		assertEquals(54, InventoryPanel.slotIndex(0, 0, chest))
		assertEquals(80, InventoryPanel.slotIndex(2, 8, chest))
		assertEquals(81, InventoryPanel.slotIndex(3, 0, chest))
		assertEquals(89, InventoryPanel.slotIndex(3, 8, chest))
		assertEquals(27, InventoryPanel.slotIndex(0, 0, InventoryPanel.chestSlots(27 + 36)))
		assertEquals(0 to 58, InventoryPanel.offset(3, 0))
		assertEquals(36, (0..3).flatMap { r -> (0..8).map { c -> InventoryPanel.slotIndex(r, c, chest) } }.toSet().size)
	}

	@Test fun whichBagPagesShowTheInventory() {
		assertFalse(BagsParser.acceptsItems("Your Bags"))
		for (t in listOf("Accessory Bag (1/2)", "Quiver", "Potion Bag", "Sack of Sacks", "Mining Sack")) assertTrue(BagsParser.acceptsItems(t), t)
	}
}
