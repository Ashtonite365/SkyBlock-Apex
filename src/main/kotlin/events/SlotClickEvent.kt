// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

data class SlotClickEvent(val slot: Slot, val stack: ItemStack, val button: Int, val actionType: ContainerInput) : ApexEvent() {
	companion object Companion : ApexEventBus<SlotClickEvent>()
}
