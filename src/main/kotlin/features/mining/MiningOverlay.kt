// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText

object MiningOverlay {
	@Config
	object TConfig : ManagedConfig("mining-overlay", Category.MINING) {
		val enabled by toggle("enabled") { true }
		val showPowder by toggle("show-powder") { true }
		val hud by position("hud", 120, 70) { Vector2i(5, 60) }
	}

	val miningIslands = setOf(SkyBlockIsland.DWARVEN_MINES, SkyBlockIsland.CRYSTAL_HOLLOWS, SkyBlockIsland.MINESHAFT)

	private val powderRegex = "^\\s*(Mithril|Gemstone|Glacite)( Powder)?: ?([0-9,.]+)\\s*$".toRegex()

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		if (SkyBlockSession.skyblockIsland !in miningIslands) return
		val lines = mutableListOf<Component>()
		val commissions = DwarvenMinesWaypoints.currentCommissions()
		if (commissions.isNotEmpty()) {
			lines.add(Component.literal("§9Commissions:"))
			commissions.forEach { (name, progress) ->
				val colour = when {
					progress == "DONE" -> "§a"
					progress.removeSuffix("%").toDoubleOrNull()?.let { it >= 50 } == true -> "§e"
					else -> "§c"
				}
				lines.add(Component.literal("§7$name: $colour$progress"))
			}
		}
		if (TConfig.showPowder) {
			TabListReader.currentSnapshot.body.forEach {
				val match = powderRegex.matchEntire(it.bareText) ?: return@forEach
				lines.add(Component.literal("§3${match.groupValues[1]} Powder: §b${match.groupValues[3]}"))
			}
		}
		event.drawHudLines(TConfig.hud, lines)
	}
}
