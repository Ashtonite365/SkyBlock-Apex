// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker
import net.minecraft.world.item.ItemStack

data class HotbarItemRenderEvent(val item: ItemStack, val context: GuiGraphicsExtractor, val x: Int, val y: Int,
		val tickDelta: DeltaTracker) : ApexEvent() {
	companion object Companion : ApexEventBus<HotbarItemRenderEvent>()
}
