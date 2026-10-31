// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.apexbzmenu

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundChatPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.stripSectionCodes
import kotlin.time.Duration.Companion.seconds

object BzPacketLog {
	private var lastBazaar = TimeMark.farPast()

	private fun active(): Boolean {
		val scr = Client.screen
		val now = when (scr) {
			is AbstractContainerScreen<*> -> true
			is AbstractSignEditScreen -> true
			else -> false
		}
		if (now) lastBazaar = TimeMark.now()
		return now || lastBazaar.passedTime() < 10.seconds
	}

	private fun log(s: String) = Apex.logger.debug("[ApexPkt] $s")

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!BzMenu.TConfig.packetDebug || !BzMenu.switchOn()) return
		try { active() } catch (_: Throwable) {}
	}

	@Subscribe
	fun onOutgoing(event: OutgoingPacketEvent) {
		try {
			if (!BzMenu.TConfig.packetDebug || !BzMenu.switchOn() || !active()) return
			val p = event.packet
			val name = p.javaClass.simpleName
			if (name.contains("Pong") || name.contains("TickEnd") || name.contains("KeepAlive") || name == "Pos" || name == "Rot" || name == "PosRot" || name == "StatusOnly") return
			log(when (p) {
				is ServerboundContainerClickPacket -> {
					val menu = Client.player?.containerMenu
					val clicked = menu?.takeIf { it.containerId == p.containerId() }?.slots?.getOrNull(p.slotNum().toInt())
						?.item?.hoverName?.string?.stripSectionCodes()
					val carried = menu?.carried?.takeIf { !it.isEmpty }?.hoverName?.string?.stripSectionCodes()
					"$name containerId=${p.containerId()} stateId=${p.stateId()} slot=${p.slotNum()} button=${p.buttonNum()} " +
						"clickType=${p.containerInput()} carried=${carried ?: "-"} slotItem=${clicked ?: "-"}"
				}
				is ServerboundSignUpdatePacket -> "$name lines=${p.lines()}"
				is ServerboundChatCommandPacket -> "$name command=/${p.command()}"
				is ServerboundChatPacket -> "$name message=${p.message()}"
				is ServerboundContainerClosePacket -> "$name containerId=${p.containerId}"
				else -> name
			})
		} catch (_: Throwable) {}
	}

	@JvmStatic
	fun onOpenScreen(p: ClientboundOpenScreenPacket) {
		try {
			if (!BzMenu.TConfig.packetDebug || !BzMenu.switchOn()) return
			val title = p.title.string
			if (title.contains("Bazaar", ignoreCase = true)) lastBazaar = TimeMark.now()
			log("IN open ${p.containerId} $title")
		} catch (_: Throwable) {}
	}
}
