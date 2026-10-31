// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

data class PartyMessageReceivedEvent(val from: ProcessChatEvent, val message: String, val name: String) : ApexEvent() {
	companion object Companion : ApexEventBus<PartyMessageReceivedEvent>()
}
