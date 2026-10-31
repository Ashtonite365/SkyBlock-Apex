// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.commands

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.chat.AutoCompletions.TConfig
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.findMatcher
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.tr
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket

object PlusCommands {
	private val patterns = PlusPatterns.group("party.invite")
	internal val inviteReceived by patterns.pattern("received", "(?:\\[.*] )?(?<player>\\S+) has invited you to join their party!.*")
	internal val inviteExpired by patterns.pattern("expired", "The party invite from (?:\\[.*] )?(?<player>\\S+) has expired\\.")

	var lastInviter: String? = null
		private set

	private var sending = false

	private fun send(command: String) {
		sending = true
		try {
			Client.runCommand(command)
		} finally {
			sending = false
		}
	}

	internal fun handleChatLine(line: String) {
		val inviter = inviteReceived.findMatcher(line) { group("player") }
		if (inviter != null) {
			lastInviter = inviter
			return
		}
		val expired = inviteExpired.matchMatcher(line) { group("player") } ?: return
		if (expired == lastInviter) lastInviter = null
	}

	private val BARE_ACCEPT = setOf("p accept", "party accept")

	internal fun isBareAccept(command: String) = command.trim().lowercase() in BARE_ACCEPT

	internal fun shortWarpFor(command: String, warps: Collection<String>, onPrivateIsland: Boolean): String? {
		val name = command.trim().lowercase()
		if (name.isEmpty() || name.contains(' ')) return null
		if (onPrivateIsland && name == "jerry") return null
		return if (name in warps) name else null
	}

	private fun warpNames(): Set<String> {
		val warps = RepoManager.coreRepo.constants?.islands?.warps ?: return emptySet()
		return warps.mapTo(HashSet()) { it.warp.lowercase() }
	}

	private const val TRANSFER_TICKS = 60
	private var transferEndsAtTick = -1
	private var currentTick = 0
	private var queued: String? = null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.acceptLastInvite) return
		handleChatLine(event.bareText)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		if (!TConfig.transferCooldown) return
		transferEndsAtTick = currentTick + TRANSFER_TICKS
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		currentTick = event.tickCount
		if (transferEndsAtTick < 0 || transferEndsAtTick > currentTick) return
		transferEndsAtTick = -1
		if (TConfig.transferCooldown && TConfig.transferCooldownMessage && SkyBlockSession.inSkyBlock)
			Client.showChat(tr("apex.plus.commands.transfer-ended", "Player Transfer Cooldown has ended."))
		val held = queued
		if (held != null && TConfig.transferCooldown) send(held)
		queued = null
	}

	@Subscribe
	fun onOutgoing(event: OutgoingPacketEvent) {
		val packet = event.packet as? ServerboundChatCommandPacket ?: return
		if (sending) return
		val command = packet.command()
		if (TConfig.acceptLastInvite && isBareAccept(command)) {
			event.cancel()
			val who = lastInviter
			if (who != null) {
				lastInviter = null
				send("party accept $who")
			} else {
				Client.showChat(tr("apex.plus.commands.no-invite", "There is no party invite to accept!"))
			}
			return
		}
		if (!SkyBlockSession.inSkyBlock) return
		if (TConfig.transferCooldown && transferEndsAtTick >= 0) {
			when (val first = command.substringBefore(' ').lowercase()) {
				"is", "warp", "warpforge" -> {
					event.cancel()
					queued = if (first == "warpforge") "warp forge" else command
					return
				}
			}
		}
		if (!TConfig.shortenWarp) return
		val warp = shortWarpFor(command, warpNames(), SkyBlockSession.skyblockIsland == SkyBlockIsland.PRIVATE_ISLAND) ?: return
		event.cancel()
		send("warp $warp")
	}
}
