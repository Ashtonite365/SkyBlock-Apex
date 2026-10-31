// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.events

import net.minecraft.world.item.ItemStack

sealed class PlayerInventoryUpdate : ApexEvent() {

	abstract fun getOrNull(index: Int): ItemStack?

	data class Single(val menuSlot: Int, val stack: ItemStack) : PlayerInventoryUpdate() {
		override fun getOrNull(index: Int): ItemStack? = stack.takeIf { index == menuSlot }
	}

	data class Multi(val stacks: List<ItemStack>) : PlayerInventoryUpdate() {
		override fun getOrNull(index: Int): ItemStack? = if (index in stacks.indices) stacks[index] else null
	}

	companion object Companion : ApexEventBus<PlayerInventoryUpdate>()
}
