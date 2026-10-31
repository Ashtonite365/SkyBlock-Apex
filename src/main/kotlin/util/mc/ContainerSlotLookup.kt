package dev.ashtonite365.apex.util.mc

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot

object ContainerSlotLookup {

	data class SlotKey(val index: Int, val inPlayerInventory: Boolean)

	private var indexedScreen: Screen? = null
	private var indexed: Map<SlotKey, Slot> = emptyMap()

	private fun keyOf(slot: Slot) = SlotKey(slot.containerSlot, slot.container is Inventory)

	fun Screen.slotMap(): Map<SlotKey, Slot> {
		if (indexedScreen === this) return indexed
		val container = this as? AbstractContainerScreen<*> ?: return emptyMap()
		val map = LinkedHashMap<SlotKey, Slot>()
		for (slot in container.menu.slots) map[keyOf(slot)] = slot
		indexedScreen = this
		indexed = map
		return map
	}

	fun Screen.slotNumbered(index: Int, playerInventory: Boolean): Slot? = slotMap()[SlotKey(index, playerInventory)]
}
