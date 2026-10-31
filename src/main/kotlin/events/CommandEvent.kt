// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.commands.CommandBuildContext
import dev.ashtonite365.apex.commands.AnyCaseLiteral
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.literal
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.util.data.ManagedConfig

private typealias LiteralBody = AnyCaseLiteral.Builder<DefaultSource>.() -> Unit

data class CommandEvent(val dispatcher: CommandDispatcher<DefaultSource>, val ctx: CommandBuildContext,
		val serverCommands: CommandDispatcher<*>?) : ApexEvent() {
	companion object Companion : ApexEventBus<CommandEvent>()

	fun register(name: String, block: LiteralBody): LiteralCommandNode<DefaultSource> =
		dispatcher.register(literal(name, block))

	fun deleteCommand(name: String): Unit {
		for (tree in listOfNotNull<CommandDispatcher<*>>(dispatcher, serverCommands)) {
			tree.root.children.removeIf { node -> node.name == name }
		}
	}

	data class SubCommand(val builder: AnyCaseLiteral.Builder<DefaultSource>,
			val commandRegistryAccess: CommandBuildContext) : ApexEvent() {
		companion object Companion : ApexEventBus<SubCommand>()

		fun register(category: ManagedConfig.Category, name: String, description: String? = null, block: LiteralBody) {
			dev.ashtonite365.apex.commands.ApexCommands.record(category, name, description)
			this.builder.onLiteral(name, block)
		}

		fun addSubcommand(name: String, category: ManagedConfig.Category, block: LiteralBody): Unit =
			register(category, name, description = null, block = block)

		@Deprecated("Pass a category, or use register(category, name, description)")
		fun addSubcommand(name: String, block: LiteralBody): Unit = addSubcommand(name, ManagedConfig.Category.GENERAL, block)
	}
}
