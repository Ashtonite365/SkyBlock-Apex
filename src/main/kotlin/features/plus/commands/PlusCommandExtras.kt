// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.commands

import dev.ashtonite365.apex.repo.itemsSafe
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.chat.AutoCompletions
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.tr

object PlusCommandExtras {
	internal val cooldownPattern by PlusPatterns.pattern("commands.cooldown", "You may only use this command after (?<cooldown>\\d+)s on the server!")

	private val opts get() = AutoCompletions.TConfig

	private var selfSending = false

	private fun dispatch(command: String) {
		selfSending = true
		try {
			Client.runCommand(command)
		} finally {
			selfSending = false
		}
	}

	private val VIEWRECIPE = Regex("(?i)viewrecipe (.+)")
	private val SPACES = dev.ashtonite365.apex.util.SharedRegex.WHITESPACE

	internal fun fixViewRecipe(command: String): String? {
		val trimmed = command.trim()
		val words = VIEWRECIPE.matchEntire(trimmed)?.groupValues?.get(1)?.trim()?.split(SPACES) ?: return null
		val page = if (words.size > 1) words.last().toIntOrNull() else null
		val nameWords = if (page == null) words else words.subList(0, words.size - 1)
		val rewritten = buildString {
			append("viewrecipe ").append(nameWords.joinToString("_").uppercase())
			if (page != null) append(' ').append(page)
		}
		return if (rewritten == trimmed) null else rewritten
	}

	enum class StorageType(val pages: IntRange, val commands: Set<String>, val target: String) {
		ENDER_CHEST(1..9, setOf("ec", "enderchest"), "enderchest"),
		BACKPACK(0..18, setOf("bp", "backpack"), "backpack");

		companion object {
			fun of(cmd: String) = entries.firstOrNull { cmd in it.commands }
		}
	}

	var lastStorage: Pair<StorageType, Int>? = null
		private set

	internal fun handleStorage(command: String): String? {
		val words = command.trim().lowercase().split(SPACES)
		val kind = StorageType.of(words[0]) ?: return null
		if (words.size > 1) {
			val page = words[1].toIntOrNull()
			if (page != null && page in kind.pages) lastStorage = kind to page
			return null
		}
		val last = lastStorage ?: return null
		return if (last.first == kind) "${last.first.target} ${last.second}" else null
	}

	private var pendingCommand: String? = null
	private var nowTick = 0
	private var retryTick = -1

	@Subscribe
	fun onTick(event: TickEvent) {
		nowTick = event.tickCount
		if (retryTick < 0 || retryTick > nowTick) return
		retryTick = -1
		val command = pendingCommand
		pendingCommand = null
		if (command != null && opts.preventEarlyCommands) dispatch(command)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		pendingCommand = null
		retryTick = -1
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!opts.preventEarlyCommands || retryTick >= 0) return
		val command = pendingCommand ?: return
		cooldownPattern.matchMatcher(event.bareText) {
			retryTick = nowTick + 20 * (group("cooldown").toIntOrNull() ?: 5)
			event.cancel()
			Client.showChat(tr("apex.plus.commands.early-retry", "Command was too early, retrying /$command shortly."))
		}
	}

	@Subscribe
	fun onOutgoing(event: OutgoingPacketEvent) {
		val packet = event.packet as? ServerboundChatCommandPacket ?: return
		if (selfSending) return
		val command = packet.command()
		if (opts.preventEarlyCommands) pendingCommand = command
		if (!SkyBlockSession.inSkyBlock) return
		val replacement = (if (opts.viewRecipeLowercase) fixViewRecipe(command) else null)
			?: (if (opts.openLastStorage) handleStorage(command) else null)
			?: return
		event.cancel()
		dispatch(replacement)
	}

	@Subscribe
	fun onSubCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.CHAT, "viewrecipe", "Hypixel /viewrecipe with item tab-completion") {
			onArgument("item", StringArgumentType.string()) { item ->
				suggestFrom { RepoManager.coreRepo.items.itemsSafe.keys }
				onRun { Client.runCommand("viewrecipe ${this[item].uppercase()}") }
			}
		}
		event.register(ManagedConfig.Category.CHAT, "gfs", "Hypixel /gfs (get from sacks) with item tab-completion") {
			onArgument("item", StringArgumentType.string()) { item ->
				suggestFrom { RepoManager.coreRepo.items.itemsSafe.keys }
				onArgument("amount", IntegerArgumentType.integer(1)) { amount ->
					onRun { Client.runCommand("gfs ${this[item].uppercase()} ${this[amount]}") }
				}
			}
		}
	}
}
