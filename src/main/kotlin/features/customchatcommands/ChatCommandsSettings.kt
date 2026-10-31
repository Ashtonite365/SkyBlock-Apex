// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.customchatcommands

import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ChatCommandsSettings {
	@Config
	object TConfig : ManagedConfig("chat-commands", Category.CHAT) {
		@Suppress("unused")
		val openCustom: Unit by button("open-custom") { CustomChatCommandsUI.open() }
	}
}
