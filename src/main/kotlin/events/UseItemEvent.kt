// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level

data class UseItemEvent(val playerEntity
	: Player, val world: Level, val hand: InteractionHand) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<UseItemEvent>()

	val item: ItemStack = this.playerEntity.getItemInHand(hand)
}
