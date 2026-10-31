// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.screens.Screen

data class ScreenChangeEvent(val old
	: Screen?, val new: Screen?) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<ScreenChangeEvent>()

	var overrideScreen: Screen? = null
}
