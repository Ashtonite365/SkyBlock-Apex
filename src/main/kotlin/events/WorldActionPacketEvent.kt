// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.events

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Block

sealed class WorldActionPacketEvent : ApexEvent() {
	companion object : ApexEventBus<WorldActionPacketEvent>()

	data class BlockAction(val pos: BlockPos, val block: Block, val type: Int, val param: Int) : WorldActionPacketEvent()

	data class ItemTaken(val item: Entity, val collectorId: Int, val amount: Int) : WorldActionPacketEvent()

	data class EntityStatus(val entity: Entity, val status: Byte) : WorldActionPacketEvent()
}
