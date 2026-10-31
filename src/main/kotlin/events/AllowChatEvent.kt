// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.bareText

data class AllowChatEvent(val text
	: Component) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<AllowChatEvent>()

	val bareText: String = text.bareText
}
