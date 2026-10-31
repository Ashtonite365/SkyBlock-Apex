// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.customchatcommands

import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client

@Serializable
data class ChatCommand(val command: String) {
	val label: Component get() = Component.literal("/$command")

	fun execute() { Client.runCommand(command) }
}

@Serializable
data class ChatCommandWheel(
	val keyBinding: SavedKeyBinding = SavedKeyBinding.unbound(),
	val commands: List<ChatCommand> = listOf(),
)
