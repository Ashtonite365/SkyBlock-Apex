// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

data class ScreenRenderPostEvent(val screen: Screen, val mouseX: Int, val mouseY: Int, val tickDelta: Float,
		val drawContext: GuiGraphicsExtractor) : ApexEvent() {
	companion object Companion : ApexEventBus<ScreenRenderPostEvent>()
}
