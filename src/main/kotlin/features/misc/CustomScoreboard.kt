// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText

object CustomScoreboard {
	@Config
	object TConfig : ManagedConfig("custom-scoreboard", Category.GENERAL) {
		val enabled by toggle("enabled") { false }
		val showTitle by toggle("show-title") { true }
		val hideServerAddress by toggle("hide-server-address") { true }
		val hideEmptyLines by toggle("hide-empty-lines") { true }
		val hideServerId by toggle("hide-server-id") { false }
		val showActivePet by toggle("show-active-pet") { true }
		val showSlayer by toggle("show-slayer") { true }
		val showForestWhispers by toggle("show-forest-whispers") { true }
		val hideWhileTabOrChat by toggle("hide-while-tab-or-chat") { true }
		val hud by position("hud", 140, 160) { Vector2i(5, 30) }
	}

	private val dateAndServer = Regex("^\\d{2}/\\d{2}/\\d{2,4} +\\S+$")

	fun keepLine(raw: String, hideEmpty: Boolean, hideAddress: Boolean, hideServerId: Boolean): Boolean {
		val text = raw.trim()
		if (hideEmpty && text.isEmpty()) return false
		if (hideAddress && text.contains("hypixel.net")) return false
		if (hideServerId && dateAndServer.matches(text)) return false
		return true
	}

	fun isActive() = TConfig.enabled && SkyBlockSession.inSkyBlock

	@JvmStatic
	fun shouldHideVanilla(): Boolean = isActive()

	fun lines(): List<Component> {
		val out = mutableListOf<Component>()
		if (TConfig.showTitle) {
			Client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.let { out += it.displayName }
		}
		for (line in ScoreboardUtil.scoreboardLines) {
			if (keepLine(line.bareText, TConfig.hideEmptyLines, TConfig.hideServerAddress, TConfig.hideServerId)) out += line
		}

		val texts = out.map { it.bareText }
		if (TConfig.showActivePet) dev.ashtonite365.apex.features.inventory.PetFeatures.currentPet()?.let { pet ->
			out += Component.literal("§7Pet: §f[Lvl ${pet.level}] ${pet.name}")
		}
		if (TConfig.showSlayer) {
			dev.ashtonite365.apex.features.skills.SlayerOverlay.slayerXpLine?.let { out += Component.literal("§5$it") }
			dev.ashtonite365.apex.features.skills.SlayerOverlay.rngMeterLine?.let { out += Component.literal("§d$it") }
		}
		if (TConfig.showForestWhispers && texts.none { it.contains("Whispers") }) {
			dev.ashtonite365.apex.util.mc.TabListReader.currentSnapshot.body
				.firstOrNull { it.bareText.contains("Forest Whispers") }
				?.let { out += Component.literal("§2" + it.bareText.trim()) }
		}
		return out
	}

	private fun tabOrChatOpen(): Boolean =
		Client.options.keyPlayerList.isDown || Client.screen is net.minecraft.client.gui.screens.ChatScreen

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!isActive()) return
		if (TConfig.hideWhileTabOrChat && tabOrChatOpen()) return
		event.drawHudLines(TConfig.hud, lines())
	}
}
