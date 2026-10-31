// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import org.joml.Vector2i
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker

object PowderGrindingOverlay {
	@Config
	object TConfig : ManagedConfig("powder-grinding", Category.MINING) {
		val enabled by toggle("enabled") { true }
		val resetOnWorldChange by toggle("reset-on-world-change") { false }
		val hud by position("hud", 140, 50) { Vector2i(5, 140) }
	}

	private val chestRegex = "You uncovered a treasure chest!".toRegex()
	private val powderRegex = "You received \\+([0-9,]+) (Mithril|Gemstone|Glacite) Powder".toRegex()

	val tracker = SessionTracker()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		val text = event.bareText
		if (chestRegex.containsMatchIn(text)) tracker.add("Chests Found")
		powderRegex.find(text)?.let {
			val amount = it.groupValues[1].replace(",", "").toDoubleOrNull() ?: return
			tracker.add("${it.groupValues[2]} Powder", amount)
		}
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		if (TConfig.resetOnWorldChange) tracker.reset()
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		event.drawHudLines(TConfig.hud, tracker.lines())
	}
}
