// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import dev.ashtonite365.apex.util.ServerLocation

data class SkyblockServerUpdateEvent(val previousLocation
	: ServerLocation?, val currentLocation: ServerLocation?) : ApexEvent() {
	companion object Companion : ApexEventBus<SkyblockServerUpdateEvent>()
}
