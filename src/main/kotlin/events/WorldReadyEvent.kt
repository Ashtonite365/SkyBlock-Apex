// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

class WorldReadyEvent
	: ApexEvent() {
	companion object Companion : ApexEventBus<WorldReadyEvent>()
}
