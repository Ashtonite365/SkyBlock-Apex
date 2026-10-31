// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

data class WorldMouseMoveEvent(val deltaX
	: Double, val deltaY: Double) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<WorldMouseMoveEvent>()
}
