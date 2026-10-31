// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

data class HandledScreenForegroundEvent(val screen: AbstractContainerScreen<*>, val context: GuiGraphicsExtractor,
		val mouseX: Int, val mouseY: Int, val delta: Float) : ApexEvent() {
	companion object Companion : ApexEventBus<HandledScreenForegroundEvent>()
}
