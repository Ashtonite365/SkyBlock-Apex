// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

class ClientStartedEvent
	: ApexEvent() {
	companion object Companion : ApexEventBus<ClientStartedEvent>()
}
