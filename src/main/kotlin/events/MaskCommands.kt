// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import com.mojang.brigadier.CommandDispatcher

data class MaskCommands(val dispatcher
	: CommandDispatcher<*>) : ApexEvent() {
	companion object Companion : ApexEventBus<MaskCommands>()

	fun mask(name: String): Unit {
		val children = dispatcher.root.children
		children.removeIf { node -> name.equals(node.name, true) }
	}
}
