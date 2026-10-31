// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.item.ItemStack

data class SuperpairsCardFlipEvent(val slot: Int, val stack: ItemStack) : ApexEvent() {
	companion object : ApexEventBus<SuperpairsCardFlipEvent>()
}
