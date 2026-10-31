/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): fancy warp menu and area navigation, built on the item repo's
 * constants/islands.json (island_warps, area_names, teleporters) and the NavigationHelper.
 * Inspired by' fancy warp menu and the /navigate (descriptions only). Original implementation.
 */
package dev.ashtonite365.apex.features.misc

import io.github.moulberry.repo.constants.Islands
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.world.NavigableWaypoint
import dev.ashtonite365.apex.features.world.NavigationHelper
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.WarpTravel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object WarpMenu {
	@Config
	object TConfig : ManagedConfig("warp-menu", Category.MISC) {
		val openKey by keyBindingWithDefaultUnbound("open-key")
		val showAliases by toggle("show-aliases") { true }
	}

	data class Entry(val warp: String, val mode: String, val aliases: List<String>, val pos: BlockPos)

	fun entries(): List<Entry> = WarpTravel.warps.map { Entry(it.warp, it.mode, it.aliases ?: emptyList(), BlockPos(it.x.toInt(), it.y.toInt(), it.z.toInt())) }.toList()

	fun find(query: String, list: List<Entry>, islandName: (String) -> String): Entry? {
		val q = query.trim().lowercase()
		return list.firstOrNull { it.warp == q } ?: list.firstOrNull { q in it.aliases.map(String::lowercase) }
			?: list.firstOrNull { islandName(it.mode).lowercase() == q } ?: list.firstOrNull { islandName(it.mode).lowercase().startsWith(q) }
	}

	private fun islandName(mode: String) = SkyBlockIsland.ofMode(mode).userFriendlyName

	class Target(override val name: String, override val position: BlockPos, override val island: SkyBlockIsland) : NavigableWaypoint

	fun navigate(e: Entry) {
		NavigationHelper.targetWaypoint = Target("/warp ${e.warp}", e.pos, SkyBlockIsland.ofMode(e.mode))
		Client.showChat(Component.literal("§e[SkyBlock Apex] Navigating to ${islandName(e.mode)} (${e.warp}). Follow the waypoint."))
	}

	class Menu : Screen(Component.literal("Warps")) {
		override fun init() {
			val grouped = entries().groupBy { it.mode }
			val colW = 110; val cols = ((width - 20) / colW).coerceAtLeast(1)
			var col = 0; var y = 30; var rowHeight = 0
			for ((mode, warps) in grouped) {
				val x = 10 + col * colW
				var yy = y
				addRenderableWidget(Button.builder(Component.literal("§l" + islandName(mode))) { }.bounds(x, yy, colW - 6, 14).build().also { it.active = false })
				yy += 16
				for (w in warps) {
					val label = if (TConfig.showAliases && w.aliases.isNotEmpty()) "${w.warp} §7(${w.aliases.first()})" else w.warp
					addRenderableWidget(Button.builder(Component.literal(label)) { Client.runCommand("warp ${w.warp}"); onClose() }
						.bounds(x, yy, colW - 6, 14).build())
					yy += 15
				}
				rowHeight = maxOf(rowHeight, yy - y)
				if (++col >= cols) { col = 0; y += rowHeight + 6; rowHeight = 0 }
			}
		}

		override fun extractRenderState(context: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
			super.extractRenderState(context, mouseX, mouseY, delta)
			context.centeredText(font, title, width / 2, 12, -1)
		}

		override fun isPauseScreen() = false
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (event.matches(TConfig.openKey)) ScreenUtil.setScreenLater(Menu())
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MISC, "warps", "Open the warp menu") {
			onRun { ScreenUtil.setScreenLater(Menu()) }
		}
		event.register(ManagedConfig.Category.MISC, "navigate", "Navigate to a warp point or island by name") {
			onArgument("target", RestArgumentType) { t ->
				suggestFrom { entries().map { it.warp } }
				onRun {
					val e = find(this[t], entries(), ::islandName)
					if (e == null) source.sendFeedback(Component.literal("§cUnknown warp or island."))
					else navigate(e)
				}
			}
		}
	}
}
