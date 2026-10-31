// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.multiplayer.ClientPacketListener

data class JoinServerEvent(val networkHandler: ClientPacketListener, val packetSender: PacketSender) : ApexEvent() {
	companion object Companion : ApexEventBus<JoinServerEvent>()
}
