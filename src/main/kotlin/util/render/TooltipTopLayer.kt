/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: item tooltips are the very top UI layer. Vanilla draws a screen's deferred elements (the
 * setTooltipForNextFrame tooltip, hovered-text effects) at the end of Screen.extractRenderStateWithTooltipAndSubtitles,
 * but Fabric's ScreenEvents.afterExtract (the ScreenRenderPostEvent: container value, side panels, search,
 * market/AH overlays, ...) runs AFTER that, so those overlays covered the tooltip. While a Gui frame is extracting,
 * the screen's deferred pass is postponed and run once, after every after-screen overlay (before toasts / the saving
 * indicator, which vanilla also draws over tooltips). Overlays keep their positions and content. Original implementation.
 */
package dev.ashtonite365.apex.util.render

class TooltipDeferral<G : Any> {
	data class Pending<G>(val graphics: G, val mouseX: Int, val mouseY: Int, val partialTick: Float)

	var inFrame = false; private set
	private val pending = ArrayList<Pending<G>>()

	fun beginFrame() { inFrame = true; pending.clear() }

	fun defer(graphics: G, mouseX: Int, mouseY: Int, partialTick: Float): Boolean {
		if (!inFrame) return false
		val p = Pending(graphics, mouseX, mouseY, partialTick)
		val i = pending.indexOfFirst { it.graphics === graphics }
		if (i >= 0) pending[i] = p else pending += p
		return true
	}

	fun flush(run: (Pending<G>) -> Unit) {
		inFrame = false
		val todo = pending.toList(); pending.clear()
		for (p in todo) runCatching { run(p) }.onFailure { dev.ashtonite365.apex.Apex.logger.warn("[Apex] tooltip layer: deferred pass failed", it) }
	}
}

object TooltipTopLayer {
	@JvmField val deferral = TooltipDeferral<net.minecraft.client.gui.GuiGraphicsExtractor>()

	@JvmStatic fun beginFrame() = deferral.beginFrame()

	@JvmStatic fun defer(g: net.minecraft.client.gui.GuiGraphicsExtractor, mx: Int, my: Int, a: Float) = deferral.defer(g, mx, my, a)

	@JvmStatic fun flush() = deferral.flush { it.graphics.extractDeferredElements(it.mouseX, it.mouseY, it.partialTick) }
}
