// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.commands

import com.mojang.brigadier.arguments.StringArgumentType.string
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.data.ManagedConfig

object ApexCommands {
	const val ROOT = "apex"

	val ALIASES = mapOf("ae" to "edit", "join" to "join", "dh" to "dh", "waypoint" to "waypoint")

	fun <S> registerAlias(dispatcher: com.mojang.brigadier.CommandDispatcher<S>, alias: String, target: String): Boolean {
		val node = dispatcher.root.getChild(ROOT)?.getChild(target) ?: return false

		val b = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal<S>(alias).requires(node.requirement)
		node.command?.let { b.executes(it) }
		node.children.forEach { b.then(it) }
		dispatcher.register(b)
		return true
	}

	data class Entry(val category: ManagedConfig.Category, val name: String, val description: String?)

	val entries = linkedMapOf<String, Entry>()

	fun record(category: ManagedConfig.Category, name: String, description: String?) {
		entries[name.lowercase()] = Entry(category, name, description)
	}

	fun helpLines(category: ManagedConfig.Category? = null): List<Component> {
		val lines = mutableListOf<Component>(Component.literal("§6§lSkyBlock Apex commands §7(/$ROOT <command>)"))
		val grouped = entries.values.groupBy { it.category }
		for (cat in ManagedConfig.Category.entries) {
			if (category != null && cat != category) continue
			val list = grouped[cat] ?: continue
			lines.add(Component.empty().append(cat.labelText.copy().withStyle(net.minecraft.ChatFormatting.YELLOW)).append(":"))
			list.sortedBy { it.name }.forEach {
				lines.add(Component.literal(" §b/$ROOT ${it.name}" + (it.description?.let { d -> " §7- $d" } ?: "")))
			}
		}
		return lines
	}

	fun finish(root: AnyCaseLiteral.Builder<DefaultSource>) {
		for (child in root.arguments) {
			if (child.name.lowercase() !in entries) record(ManagedConfig.Category.GENERAL, child.name, null)
		}
		record(ManagedConfig.Category.GENERAL, "help", "List all commands, optionally for one category")
		root.onLiteral("help") {
			onRun { helpLines().forEach { source.sendFeedback(it) } }
			onArgument("category", string()) { cat ->
				suggestFrom { ManagedConfig.Category.entries.filter { c -> entries.values.any { it.category == c } }.map { it.name.lowercase() } }
				onRun {
					val c = ManagedConfig.Category.entries.find { it.name.equals(this[cat], true) }
					helpLines(c).forEach { source.sendFeedback(it) }
				}
			}
		}
	}
}
