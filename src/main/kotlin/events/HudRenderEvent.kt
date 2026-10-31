// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker
import net.minecraft.world.level.GameType
import dev.ashtonite365.apex.util.Client

data class HudRenderEvent(val context
	: GuiGraphicsExtractor, val tickDelta: DeltaTracker) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<HudRenderEvent>()

	val isRenderingHud: Boolean = !Client.hud.isHidden
	val isRenderingCursor: Boolean = isRenderingHud && Client.gameMode?.playerMode != GameType.SPECTATOR

	init { if (dev.ashtonite365.apex.features.render.HidePlayers.hideApexHud || !isRenderingHud) cancel() }
}
