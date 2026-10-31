// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.debug

import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TestUtil
import dev.ashtonite365.apex.util.collections.InstanceList
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder

class DebugLogger(val tag: String) {
	init { registry.add(this) }

	fun isEnabled() = TestUtil.isInTest || (EnabledLogs.isLoaded && EnabledLogs.data.contains(tag))

	fun log(text: String) = log(fun() = text)

	fun log(text: () -> String) = if (isEnabled()) Client.showChat(Component.literal(text())) else Unit

	@Config
	object EnabledLogs : DataHolder<MutableSet<String>>(serializer(), "DebugLogs", { mutableSetOf() })

	companion object {
		private val registry = InstanceList<DebugLogger>("DebugLogger")
		val allInstances get() = registry
	}
}
