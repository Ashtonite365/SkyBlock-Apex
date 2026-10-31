package dev.ashtonite365.apex.util.render

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.util.hud.HudStyle

fun HudRenderEvent.drawHudLines(position: HudMeta, lines: List<Component>, style: HudStyle.Override? = null) {
	HudStyle.drawLines(this, position, lines, style)
}
