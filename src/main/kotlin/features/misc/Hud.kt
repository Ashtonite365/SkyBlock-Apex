// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.misc

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tr

object Hud {
	val identifier: String get() = "hud"

	@Config
	object TConfig : ManagedConfig(category = Category.GENERAL, name = identifier) {
		var dayCount by toggle("day-count") { false }
		val dayCountHud by position("day-count-hud", 80, 10) { Vector2i() }
		var fpsCount by toggle("fps-count") { false }
		val fpsCountHud by position("fps-count-hud", 80, 10) { Vector2i() }
		var pingCount by toggle("ping-count") { false }
		val pingCountHud by position("ping-count-hud", 80, 10) { Vector2i() }
		var coordinates by toggle("coordinates") { false }
		val coordinatesFacing by toggle("coordinates-facing") { true }
		val coordinatesHud by position("coordinates-hud", 120, 10) { Vector2i(0, 30) }

		val editorSnap by toggle("editor-snap") { true }

		val hiddenElements = option("hidden-elements", { emptySet<String>() }, dev.ashtonite365.apex.gui.config.StringSetHandler(this))
	}

	private fun filled(pattern: Component, value: Any): Component = Component.literal(pattern.string.format(value))

	private fun day(): Component = filled(tr("apex.config.hud.day-count-hud.display", "Day: %s"), (Client.level?.gameTime ?: 0L) / 24000)
	private fun fps(): Component = filled(tr("apex.config.hud.fps-count-hud.display", "FPS: %s"), Client.game.fps)

	private fun ping(): Component {
		val uuid = Client.player?.uuid
		val latency = uuid?.let { Client.connection?.getPlayerInfo(it) }?.latency ?: -1
		return filled(tr("apex.config.hud.ping-count-hud.display", "Ping: %s ms"), latency)
	}

	private fun position(): Component? {
		val p = Client.player ?: return null
		val heading = if (!TConfig.coordinatesFacing) "" else " §7" + p.direction.name.lowercase().replaceFirstChar(Char::uppercase)
		return Component.literal("XYZ: ${p.blockX} ${p.blockY} ${p.blockZ}$heading")
	}

	@Subscribe
	fun drawInfoLines(event: HudRenderEvent) {
		val c = TConfig

		val shown = listOf<Triple<Boolean, HudMeta, () -> Component?>>(
			Triple(c.dayCount, c.dayCountHud, ::day),
			Triple(c.fpsCount, c.fpsCountHud, ::fps),
			Triple(c.coordinates, c.coordinatesHud, ::position),
			Triple(c.pingCount, c.pingCountHud, ::ping),
		)
		for ((on, where, line) in shown) {
			if (!on) continue
			line()?.let { event.drawHudLines(where, listOf(it)) }
		}
	}
}
