// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.features.menus.style.CloseGhost
import dev.ashtonite365.apex.features.menus.style.GuiCanvas
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.StyleAnim
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import dev.ashtonite365.apex.util.Client
import net.minecraft.client.gui.GuiGraphicsExtractor

class MenuChrome {
	val bar = StyleBar()
	private val openedAt = dev.ashtonite365.apex.features.menus.style.ScreenTransitions.openStartNs()
	private val seed = System.nanoTime()
	private var held = false

	companion object {

		fun barAnchor(viewX: Int, viewY: Int, viewW: Int): Pair<Int, Int> =
			if (viewY >= StyleBar.H + 4) (viewX + viewW) to (viewY - StyleBar.H - 3) else (viewX + viewW - 4) to (viewY + 4)

		fun wantsBlur(styleBlurs: Boolean, inGameUi: Boolean, blurriness: Int) = styleBlurs && inGameUi && blurriness >= 1

		fun frostFade(kind: dev.ashtonite365.apex.features.menus.style.MenuAnim, reveal: Double) =
			if (kind == dev.ashtonite365.apex.features.menus.style.MenuAnim.FADE) reveal.coerceIn(0.0, 1.0) else 1.0
	}

	fun geometry(view: dev.ashtonite365.apex.features.menus.MenuView) =
		barAnchor(view.x, view.y, view.w).let { (r, y) -> StyleBar.geometry(r, y, MenuStyle.state, Client.screen?.height ?: Int.MAX_VALUE) }

	fun backdrop(ctx: GuiGraphicsExtractor, sw: Int, sh: Int, inGameUi: Boolean = true) {
		val s = MenuStyle.state; val p = MenuStyle.palette
		val elapsed = (System.nanoTime() - openedAt) / 1_000_000
		dev.ashtonite365.apex.features.menus.style.Frost.fade = frostFade(StyleAnim.kind(s), StyleAnim.openReveal(s, elapsed))
		if (!p.clearOutside && (p.backdrop ushr 24) != 0) ctx.fill(0, 0, sw, sh, p.backdrop)
	}

	fun overlay(ctx: GuiGraphicsExtractor, view: dev.ashtonite365.apex.features.menus.MenuView, mx: Int, my: Int) {
		val s = MenuStyle.state; val p = MenuStyle.palette
		val c = GuiCanvas(ctx, Client.font)
		ctx.nextStratum()
		bar.drawButton(c, p, s, geometry(view), mx, my, held)
		val elapsed = (System.nanoTime() - openedAt) / 1_000_000
		StyleWidgets.cover(c, StyleAnim.kind(s), p.window, view.x, view.y, view.w, view.h, StyleAnim.openReveal(s, elapsed), seed, elapsed)
	}

	fun lateOverlay(ctx: GuiGraphicsExtractor, view: dev.ashtonite365.apex.features.menus.MenuView, mx: Int, my: Int) {
		if (!bar.open) return
		bar.drawList(GuiCanvas(ctx, Client.font), MenuStyle.palette, MenuStyle.state, geometry(view), mx, my)
	}

	fun press(view: dev.ashtonite365.apex.features.menus.MenuView, mx: Double, my: Double): Boolean {
		val ours = bar.click(geometry(view), mx, my)
		held = ours
		return ours
	}
	fun release() { held = false }

	fun closed(view: dev.ashtonite365.apex.features.menus.MenuView) {
		val s = MenuStyle.state
		if (StyleAnim.closeMs(s) > 0) Ghosts.start(Ghosts.Ghost(view.x, view.y, view.w, view.h, System.nanoTime(), seed))
	}
}

object Ghosts {
	data class Ghost(val x: Int, val y: Int, val w: Int, val h: Int, val startNs: Long, val seed: Long)
	private var current: Ghost? = null
	fun start(g: Ghost) { current = g }

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val g = current ?: return

		if (!dev.ashtonite365.apex.features.menus.style.ScreenTransitions.closeShows(Client.screen != null)) { current = null; return }
		val s = MenuStyle.state
		val elapsed = (System.nanoTime() - g.startNs) / 1_000_000
		val v = StyleAnim.closeVisible(s, elapsed)
		if (v <= 0.0) { current = null; return }
		CloseGhost.draw(GuiCanvas(event.context, Client.font), StyleAnim.kind(s), MenuStyle.palette, g.x, g.y, g.w, g.h, v, g.seed, elapsed)
	}
}
