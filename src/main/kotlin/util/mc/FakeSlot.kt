package dev.ashtonite365.apex.util.mc

import util.mc.FakeInventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot

class FakeSlot(shown: ItemStack, left: Int, top: Int) : Slot(FakeInventory(shown), ONLY_SLOT, left, top) {
	private companion object {

		const val ONLY_SLOT = 0
	}
}
