// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.Connection

data class ServerConnectedEvent(val connection: Connection) : ApexEvent() {
	companion object Companion : ApexEventBus<ServerConnectedEvent>() {
		init  {
			val onInit = ClientPlayConnectionEvents.Init { listener: ClientPacketListener, _: Minecraft ->
				publishSync(ServerConnectedEvent(listener.connection))
			}
			ClientPlayConnectionEvents.INIT.register(onInit)
		}
	}
}
