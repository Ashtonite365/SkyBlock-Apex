// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ChatPeek {
	@Config
	object TConfig : ManagedConfig("chat-peek", Category.CHAT) {
		val peekKey by keyBindingWithDefaultUnbound("peek-key")
	}

	@JvmStatic
	fun isPeeking(): Boolean = Client.screen == null && TConfig.peekKey.button.isBound() && TConfig.peekKey.isPressed()
}
