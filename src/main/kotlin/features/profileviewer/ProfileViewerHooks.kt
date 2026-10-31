// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.profileviewer

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.hover
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object ProfileViewerHooks {

	private val partyFinderJoin = "^Party Finder > (\\w{2,16}) joined the (?:dungeon )?group!.*".toRegex()

	private val profileMenuTitle = "^(\\w{2,16})'s Profile$".toRegex()

	private const val BUTTON_W = 60
	private const val BUTTON_H = 16

	@Subscribe
	fun onChat(event: ModifyChatEvent) {
		if (!ProfileViewer.TConfig.partyFinderButton || !SkyBlockSession.inSkyBlock) return
		val name = partyFinderJoin.find(event.bareText)?.groupValues?.get(1) ?: return
		if (name.equals(Client.game.user.name, ignoreCase = true)) return
		val stats = ProfileViewerQuickStats.cached(name)
		event.replaceWith = event.replaceWith.copy().append(
			Component.literal(" [PV]").withStyle(ChatFormatting.AQUA)
				.runsOnClick("/apex pv $name")
				.hover(stats?.let { Component.literal(it.details.joinToString("\n")) }
					?: tr("apex.pv.chat-button.hover", "Open the profile viewer for $name"))
		)

		if (stats == null && ProfileViewer.TConfig.partyFinderStats) {
			ProfileViewerQuickStats.fetch(name) { summary, _ ->
				summary?.let { Client.showChat(ProfileViewerQuickStats.chatLine(it)) }
			}
		}
	}

	private fun profileMenuName(screen: AbstractContainerScreen<*>): String? {
		if (!ProfileViewer.TConfig.profileMenuButton || !SkyBlockSession.inSkyBlock) return null

		if (dev.ashtonite365.apex.features.menus.MenuRegistry.match(screen.title.bareText)?.let(dev.ashtonite365.apex.features.menus.MenuRegistry::isOn) == true) return null
		return profileMenuTitle.find(screen.title.bareText)?.groupValues?.get(1)
	}

	private fun buttonX(screen: AbstractContainerScreen<*>) = (screen as AccessorHandledScreen).x_Apex
	private fun buttonY(screen: AbstractContainerScreen<*>) = (screen as AccessorHandledScreen).y_Apex - BUTTON_H - 2

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		profileMenuName(screen) ?: return
		val x = buttonX(screen); val y = buttonY(screen)
		if (y < 0) return
		val hovered = event.mouseX in x until x + BUTTON_W && event.mouseY in y until y + BUTTON_H
		val ctx = event.drawContext
		ctx.fill(x, y, x + BUTTON_W, y + BUTTON_H, if (hovered) 0xE0404060.toInt() else 0xE0202020.toInt())
		ctx.text(Client.font, "§bOpen PV", x + 6, y + 4, -1, true)
	}

	@Subscribe
	fun onClick(event: HandledScreenClickEvent) {
		val name = profileMenuName(event.screen) ?: return
		val x = buttonX(event.screen); val y = buttonY(event.screen)
		if (event.mouseX >= x && event.mouseX < x + BUTTON_W && event.mouseY >= y && event.mouseY < y + BUTTON_H) {
			event.cancel()
			dev.ashtonite365.apex.features.menus.MenuPress.acted()
			ProfileViewer.open(name)
		}
	}
}
