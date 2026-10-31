// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.bareText

data class ModifyChatEvent(val originalText
	: Component) : ApexEvent() {
	companion object Companion : ApexEventBus<ModifyChatEvent>()

	var bareText: String = originalText.bareText; private set

	var replaceWith: Component = originalText
		set(replacement) {
			bareText = replacement.bareText
			field = replacement
		}
}
