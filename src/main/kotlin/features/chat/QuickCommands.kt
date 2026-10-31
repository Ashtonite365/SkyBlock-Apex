// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.chat

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.tr

object QuickCommands {
	val identifier: String get() = "quick-commands"

	@Config
	object TConfig : ManagedConfig(identifier, Category.CHAT) {
		val enableJoin by toggle("join") { true }
		val enableDh by toggle("dh") { true }

		override fun onChange(option: ManagedOption<*>) {
			super.onChange(option)
			reloadCommands()
		}
	}

	var lastReceivedTreePacket: ClientboundCommandsPacket? = null

	val kuudraLevelNames = listOf("NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL")
	val dungeonLevelNames = listOf("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")

	fun reloadCommands() {
		val connection = Client.connection ?: return
		val serverTree = lastReceivedTreePacket ?: return
		val before = ClientCommandInternals.getActiveDispatcher()
		try {
			val rebuilt = CommandDispatcher<DefaultSource>()
			ClientCommandInternals.setActiveDispatcher(rebuilt)
			val buildContext = CommandBuildContext.simple(connection.registryAccess(), connection.enabledFeatures())
			ClientCommandRegistrationCallback.EVENT.invoker().register(rebuilt, buildContext)
			ClientCommandInternals.finalizeInit()
			connection.handleCommands(serverTree)
		} catch (failure: Exception) {
			ClientCommandInternals.setActiveDispatcher(before)
			Faults.reportFault("Could not rebuild the client command tree", failure)
		}
	}

	fun removePartialPrefix(text: String, prefix: String): String? {
		var shared = 0
		val limit = minOf(text.length, prefix.length)
		while (shared < limit && text[shared] == prefix[shared]) shared++
		return if (shared == 0) null else text.substring(shared)
	}

	private fun levelIndex(remainder: String, names: List<String>): Int {
		remainder.toIntOrNull()?.let { return it - 1 }
		val wanted = remainder.uppercase()
		return names.indexOfFirst { it.startsWith(wanted) }
	}

	private fun firstRemoval(text: String, vararg prefixes: String): String? =
		prefixes.firstNotNullOfOrNull { removePartialPrefix(text, it) }

	fun CommandContext<DefaultSource>.getNameForFloor(w: String): String? {
		val tierPart = firstRemoval(w, "kuudratier", "tier")
		if (tierPart != null) {
			val tier = levelIndex(tierPart, kuudraLevelNames)
			if (tier in kuudraLevelNames.indices) return "KUUDRA_" + kuudraLevelNames[tier]
			source.sendFeedback(tr("apex.quick-commands.join.unknown-kuudra", "Unknown kuudra floor $tierPart"))
			return null
		}
		val masterPart = removePartialPrefix(w, "master")
		val floorPart = masterPart ?: firstRemoval(w, "floor", "catacombs", "dungeons") ?: return null
		val floor = levelIndex(floorPart, dungeonLevelNames)
		if (masterPart == null && floor == -1) return "CATACOMBS_ENTRANCE"
		if (floor !in dungeonLevelNames.indices) {
			source.sendFeedback(tr("apex.quick-commands.join.unknown-catacombs", "Unknown catacombs floor $floorPart"))
			return null
		}
		val tierPrefix = if (masterPart != null) "MASTER_" else ""
		return tierPrefix + "CATACOMBS_FLOOR_" + dungeonLevelNames[floor]
	}

	fun serverJoinCommand(what: String): String = "join $what"

	private fun CommandContext<DefaultSource>.join(typed: String) {
		if (!SkyBlockSession.inSkyBlock) {

			Client.connection?.send(ServerboundChatCommandPacket(serverJoinCommand(typed)))
			return
		}
		val instance = getNameForFloor(typed.lowercase().replace(" ", ""))
		if (instance == null) {
			source.sendFeedback(tr("apex.quick-commands.join.unknown", "Could not find instance for $typed"))
			return
		}
		source.sendFeedback(tr("apex.quick-commands.join.success", "Joining: $instance"))
		Client.runCommand("joininstance $instance")
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		if (TConfig.enableDh) {
			event.register(ManagedConfig.Category.CHAT, "dh", "Warp to the dungeon hub") {
				onRun { Client.runCommand("warp dhub") }
			}
			event.register(ManagedConfig.Category.CHAT, "dn", "Warp to the dungeon hub") {
				onRun {
					source.sendFeedback(tr("apex.quick-commands.dn", "Warping to... Deez Nuts!").grey())
					Client.runCommand("warp dhub")
				}
			}
		}
		if (TConfig.enableJoin) {
			event.register(ManagedConfig.Category.CHAT, "join", "Join a dungeon floor or kuudra tier, e.g. f7, m3, t5") {
				onRun {
					source.sendFeedback(tr("apex.quick-commands.join.explain", "Join a dungeon or kuudra floor by using commands like /join f1, /join m7, /join fe or /join khot"))
				}
				onArgument("what", RestArgumentType) { what ->
					onRun { join(this[what]) }
				}
			}
		}
	}
}
