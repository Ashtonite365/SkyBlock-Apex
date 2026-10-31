// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.bareText

data class ProcessChatEvent(val text
	: Component, val wasExternallyCancelled: Boolean) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<ProcessChatEvent>()

	val bareText: String = text.bareText

	val nameHeuristic: String? = bareText.indexOf(':').takeIf { it >= 0 }?.let { colon ->
		val start = bareText.lastIndexOf(' ', colon) + 1
		bareText.substring(start, colon).ifEmpty { null }
	}

	init { if (wasExternallyCancelled) cancel() }
}
