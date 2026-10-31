// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

data class TickEvent(val tickCount
	: Int) : ApexEvent() {
	companion object Companion : ApexEventBus<TickEvent>()
}
