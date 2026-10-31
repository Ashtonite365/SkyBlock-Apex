// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

data class HandledScreenClickEvent(val screen: AbstractContainerScreen<*>, val mouseX: Double, val mouseY: Double,
		val button: Int) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<HandledScreenClickEvent>()
}
