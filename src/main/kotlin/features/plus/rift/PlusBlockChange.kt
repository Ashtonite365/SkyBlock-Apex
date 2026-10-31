// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.util.Client

data class PlusBlockChangeEvent(val pos: BlockPos, val old: BlockState, val new: BlockState) : ApexEvent() {

	val oldName: String get() = BuiltInRegistries.BLOCK.getKey(old.block).path
	val newName: String get() = BuiltInRegistries.BLOCK.getKey(new.block).path

	companion object : ApexEventBus<PlusBlockChangeEvent>()
}

object PlusBlockChange {
	@JvmStatic
	fun fire(pos: BlockPos, new: BlockState) {
		val old = Client.level?.getBlockState(pos) ?: return
		if (old == new) return
		PlusBlockChangeEvent.publish(PlusBlockChangeEvent(pos, old, new))
	}
}
