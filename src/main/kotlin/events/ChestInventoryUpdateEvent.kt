// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.util.Client

sealed class ChestInventoryUpdateEvent : ApexEvent() {

	val inventory = Client.screen

	data class Single(val stack: ItemStack, val slot: Int) : ChestInventoryUpdateEvent()

	data class Multi(val contents: List<ItemStack>) : ChestInventoryUpdateEvent()

	companion object Companion : ApexEventBus<ChestInventoryUpdateEvent>()
}
