// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

data class HandledScreenPushREIEvent(val screen: AbstractContainerScreen<*>,
		val rectangles: MutableList<Rectangle> = mutableListOf()) : ApexEvent() {
	companion object Companion : ApexEventBus<HandledScreenPushREIEvent>()

	fun block(rectangle: Rectangle) = rectangles.plusAssign(rectangle)
}
