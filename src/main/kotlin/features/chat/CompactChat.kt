// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object CompactChat {
	@Config
	object TConfig : ManagedConfig("compact-chat", Category.CHAT) {
		val enabled by toggle("enabled") { false }
		val mode by choice("mode") { Mode.TIME_WINDOW }
		val windowSeconds by integer("window-seconds", 5, 600) { 60 }
		val skipClickable by toggle("skip-clickable") { true }
	}

	enum class Mode : StringRepresentable {
		CONSECUTIVE, TIME_WINDOW, UNLIMITED;

		override fun getSerializedName() = name.lowercase()
	}

	private val counterSuffix = Regex(" \\(x\\d+\\)$")
	private val dividers = Regex("^[-▬=━─ ]{10,}$")

	private fun key(text: Component) = text.bareText.replace(counterSuffix, "").trim()

	private fun isClickable(text: Component): Boolean =
		text.style.clickEvent != null || text.toFlatList().any { it.style.clickEvent != null }

	@JvmStatic
	fun compact(incoming: Component, messages: MutableList<GuiMessage>, now: Int): Component {
		if (!TConfig.enabled) return incoming
		val k = key(incoming)
		if (k.isBlank() || dividers.matches(k)) return incoming
		if (TConfig.skipClickable && isClickable(incoming)) return incoming

		val candidates = when (TConfig.mode) {
			Mode.CONSECUTIVE -> messages.take(1)
			Mode.TIME_WINDOW -> messages.takeWhile { now - it.addedTime() <= TConfig.windowSeconds * 20 }
			Mode.UNLIMITED -> messages
		}
		val previous = candidates.firstOrNull { key(it.content()) == k } ?: return incoming
		val count = Regex("\\(x(\\d+)\\)$").find(previous.content().bareText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
		messages.remove(previous)
		return incoming.copy().append(Component.literal(" §7(x${count + 1})"))
	}

}
