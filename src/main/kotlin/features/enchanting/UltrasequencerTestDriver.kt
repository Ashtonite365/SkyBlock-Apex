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
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil

object UltrasequencerTestDriver {
	val enabled = System.getenv("APEX_FAKE_ULTRASEQ") == "1"
	private var ticks = 0
	private var container: SimpleContainer? = null

	private val steps: List<Pair<Int, Item>> = listOf(
		10 to Items.INK_SAC, 12 to Items.DYE.lime, 14 to Items.COCOA_BEANS,
		16 to Items.DYE.red, 28 to Items.DYE.yellow, 30 to Items.LAPIS_LAZULI,
	)

	private fun named(item: Item, name: String, count: Int = 1) =
		ItemStack(item, count).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(name)) }

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!enabled) return
		val player = Client.player ?: return
		ticks++
		when (ticks) {
			8 * 20 -> {
				val c = SimpleContainer(54)
				for (i in 0 until 54) c.setItem(i, named(Items.STAINED_GLASS_PANE.black, " "))
				c.setItem(49, named(Items.GLOWSTONE, "Remember the pattern!"))
				steps.forEachIndexed { n, (slot, item) -> c.setItem(slot, named(item, "${n + 1}", n + 1)) }
				container = c
				ScreenUtil.setScreenLater(ContainerScreen(ChestMenu.sixRows(0, player.inventory, c), player.inventory,
					Component.literal("Ultrasequencer (Metaphysical)")))
			}
			11 * 20 -> container?.let { c ->

				for ((slot, _) in steps) c.setItem(slot, named(Items.STAINED_GLASS_PANE.white, " "))
				c.setItem(49, named(Items.CLOCK, "Timer: 10s"))
			}
			13 * 20 -> {
				net.minecraft.client.Screenshot.grab(Client.game.gameDirectory, Client.gameRenderer.mainRenderTarget()) {}
				Apex.logger.info("[apex ultraseq test] screen=${Client.screen?.title?.string} order=${ExperimentSolvers.debugSequencerOrder()}")
			}
		}
	}
}
