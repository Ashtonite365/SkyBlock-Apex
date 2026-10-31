// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.Level

data class UseBlockEvent(val player: Player, val world: Level, val hand: InteractionHand,
		val hitResult: BlockHitResult) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<UseBlockEvent>()
}
