// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.network.protocol.Packet

data class OutgoingPacketEvent(val packet
	: Packet<*>) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<OutgoingPacketEvent>()
}
