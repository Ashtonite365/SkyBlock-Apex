// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.bareText

class ActionBarEvent(
	var text: Component,
	val source: String = SOURCE_SYSTEM_CHAT,
) : ApexEvent.Cancellable() {

	val original: Component = text

	val bareText: String get() = text.bareText

	init {
		last = this
	}

	companion object : ApexEventBus<ActionBarEvent>() {
		const val SOURCE_SYSTEM_CHAT = "ClientboundSystemChatPacket overlay=true (Fabric MODIFY_GAME)"
		const val SOURCE_ACTION_BAR_PACKET = "ClientboundSetActionBarTextPacket (Hud.setOverlayMessage)"
		const val SOURCE_OTHER = "Hud.setOverlayMessage (other caller)"

		private var last: ActionBarEvent? = null

		@JvmField
		var pendingSource: String? = null

		@JvmStatic
		fun alreadyHandled(message: Component): Boolean {
			val l = last ?: return false
			return message === l.text || message === l.original
		}

		@JvmStatic
		fun dispatchOverlay(message: Component): Component {
			val source = pendingSource ?: SOURCE_OTHER
			pendingSource = null
			if (alreadyHandled(message) || message.string.isBlank()) return message
			val event = publish(ActionBarEvent(message, source))
			return if (event.cancelled) Component.empty() else event.text
		}
	}
}
