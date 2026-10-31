// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object ChatTabs {
	@Config
	object TConfig : ManagedConfig("chat-tabs", Category.CHAT) {
		val enabled by toggle("enabled") { false }
	}

	enum class Tab(val label: String, val matches: (String) -> Boolean) : StringRepresentable {
		ALL("All", { true }),
		PARTY("Party", { it.startsWith("Party > ") || it.startsWith("P > ") }),
		GUILD("Guild", { it.startsWith("Guild > ") || it.startsWith("Officer > ") || it.startsWith("G > ") }),
		PRIVATE("PM", { it.startsWith("From ") || it.startsWith("To ") }),
		COOP("Co-op", { it.startsWith("Co-op > ") || it.startsWith("CC > ") }),
		;

		override fun getSerializedName() = name.lowercase()
	}

	var current = Tab.ALL
		private set

	fun select(tab: Tab) {
		current = tab
		ChatSearch.applyFilter()
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, height ->
			if (screen !is ChatScreen) return@AfterInit
			if (!TConfig.enabled) {
				if (current != Tab.ALL) select(Tab.ALL)
				return@AfterInit
			}
			val widgets = Screens.getWidgets(screen)
			var x = 2
			val buttons = mutableListOf<Pair<Tab, Button>>()
			fun label(tab: Tab) = Component.literal(if (tab == current) "§e${tab.label}" else tab.label)
			for (tab in Tab.entries) {
				val w = Client.font.width(tab.label) + 10
				val button = Button.builder(label(tab)) {
					select(tab)
					buttons.forEach { (t, b) -> b.message = label(t) }
				}.bounds(x, height - 28, w, 12).build()
				buttons += tab to button
				widgets.add(button)
				x += w + 2
			}
		})
	}
}
