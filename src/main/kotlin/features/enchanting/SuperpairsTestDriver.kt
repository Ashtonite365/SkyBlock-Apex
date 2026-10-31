// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.enchanting

import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil

object SuperpairsTestDriver {
	val enabled = System.getenv("APEX_FAKE_SUPERPAIRS") == "1"
	private var ticks = 0

	private fun named(item: Item, name: String) =
		ItemStack(item).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!enabled) return
		val player = Client.player ?: return
		ticks++
		when (ticks) {
			8 * 20 -> {
				val c = SimpleContainer(54)
				for (i in 0 until 45) c.setItem(i, named(Items.STAINED_GLASS_PANE.purple, "?"))
				for (i in 45 until 54) c.setItem(i, named(Items.STAINED_GLASS_PANE.black, " "))

				c.setItem(10, named(Items.DIAMOND, "Enchanted Book")); c.setItem(20, named(Items.DIAMOND, "Enchanted Book"))
				c.setItem(12, named(Items.EMERALD, "Titanic Experience Bottle")); c.setItem(30, named(Items.EMERALD, "Titanic Experience Bottle"))
				c.setItem(49, named(Items.BOOKSHELF, "Superpairs").also {
					it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("Clicks Remaining: 7"))))
				})
				ScreenUtil.setScreenLater(ContainerScreen(ChestMenu.sixRows(0, player.inventory, c), player.inventory,
					Component.literal("Superpairs (Metaphysical)")))
			}
			11 * 20 -> net.minecraft.client.Screenshot.grab(Client.game.gameDirectory, Client.gameRenderer.mainRenderTarget()) {}
		}
	}
}
