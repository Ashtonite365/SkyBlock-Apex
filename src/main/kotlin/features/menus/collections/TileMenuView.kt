// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.collections

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.util.Client

object TileLayout {
	const val LABEL_H = 12
	fun cols(width: Int, tileW: Int, gap: Int = 3) = maxOf(1, (width + gap) / (tileW + gap))

	fun height(sizes: List<Int>, cols: Int, tileH: Int, gap: Int = 3) =
		sizes.sumOf { n -> LABEL_H + ((n + cols - 1) / cols) * (tileH + gap) }
	fun maxScroll(content: Int, visible: Int) = maxOf(0, content - visible)

	data class Widened(val cols: Int, val tileW: Int, val gap: Int, val margin: Int) {
		val used get() = cols * tileW + (cols - 1) * gap
		fun x(col: Int) = margin + col * (tileW + gap)
	}
	const val WIDEN = 1.18

	fun tileHeight(base: Int, tiles: List<TileSpec>): Int {
		val bar = tiles.filter { it.fraction != null }
		if (bar.isEmpty()) return base
		return maxOf(base, MenuTheme.BarTile.height(bar.any { it.sub != null }, true))
	}

	fun rects(tx: Int, ty: Int, tileW: Int, tileH: Int, t: TileSpec) =
		MenuTheme.BarTile.rects(tx, ty, tileW, tileH, tx + 22, t.sub != null, t.fraction != null, barX = tx + 22, barRight = tx + tileW - 4)

	fun widened(innerW: Int, cols: Int, tileW: Int, gap: Int = 3, factor: Double = WIDEN): Widened {
		val c = maxOf(1, cols)
		val maxW = maxOf(1, (innerW - (c - 1) * gap) / c)
		var tw = minOf(Math.round(tileW * factor).toInt(), maxW)
		var g = gap
		if ((innerW - (c * tw + (c - 1) * g)) % 2 != 0) {
			if (c % 2 == 1) tw -= 1
			else if (c * tw + (c - 1) * (g + 1) <= innerW) g += 1
			else g -= 1
		}
		val used = c * tw + (c - 1) * g
		return Widened(c, tw, g, maxOf(0, (innerW - used) / 2))
	}
}

data class TileSpec(val slot: Int, val title: String, val sub: String?, val fraction: Double?, val colour: Int, val tip: List<String>? = null,
                    val badge: String? = null, val subAlts: List<String> = emptyList(), val iconSlot: Int? = null, val barColour: Int? = null,

                    val barText: String? = null)

data class TileContent(val iconX: Int, val iconY: Int, val showIcon: Boolean, val textX: Int, val textW: Int, val titleY: Int,
                       val showTitle: Boolean, val subY: Int, val showSub: Boolean, val barX: Int, val barW: Int, val barY: Int) {
	companion object {
		const val ICON = 16
		fun of(tw: Int, th: Int): TileContent {
			val pad = maxOf(2, minOf(6, tw / 30))
			val showIcon = tw >= ICON + 2 * pad && th >= ICON + 2
			val textX = if (showIcon) pad + ICON + pad else pad
			val textW = maxOf(0, tw - textX - pad)
			val showTitle = textW >= 18
			val showSub = showTitle && th >= 22
			val titleY = if (!showSub) (th - 8) / 2 else maxOf(2, th / 2 - 11)
			return TileContent(pad, (th - ICON) / 2, showIcon, textX, textW, titleY, showTitle, titleY + 10, showSub,
				textX, if (th >= 20) textW else 0, th - 4)
		}
	}
}

object TileText {

	fun pick(cands: List<String>, w: Int, width: (String) -> Int): String? = cands.firstOrNull { width(it) <= w }

	fun titleRoom(w: Int, badgeW: Int, gap: Int = 4) = if (badgeW <= 0) w else w - badgeW - gap
}

abstract class TileMenuView<M : Any>(screen: AbstractContainerScreen<*>) : ApexViewBase<M>(screen) {
	protected var scroll = 0
	protected var hoverSlot: Int? = null
	protected var hoverTip: List<String>? = null
	open val tileW = 112
	open val tileH = 30

	open val widenTiles = false

	protected fun header(ctx: GuiGraphicsExtractor, mx: Int, my: Int, title: String, progress: LoreProgress?, extra: String? = null): Int {
		val pad = 6
		ctx.text(font, MenuTheme.fit("§6§l$title", w - 80, font::width), x + pad, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		var ty = y + 22
		if (progress != null) {
			val label = progress.label + ": " + listOfNotNull(progress.pct?.let { fmtPct(it) },
				progress.current?.let { c -> "${fmtNum(c)}/${progress.max?.let(::fmtNum) ?: "?"}" }).joinToString(" · ")
			MenuTheme.progressBar(ctx, font, x + pad, ty, w - 2 * pad, 12, progress.fraction ?: 0.0, 1.0, label, palette = palette())
			ty += 15
		}
		if (extra != null) { ctx.text(font, MenuTheme.fit(extra, w - 2 * pad, font::width), x + pad, ty, MenuTheme.GREY); ty += 11 }
		return ty + 2
	}

	protected fun groups(ctx: GuiGraphicsExtractor, mx: Int, my: Int, top: Int, bottom: Int, groups: List<Pair<String, List<TileSpec>>>) {
		val pad = 6
		val oldCols = TileLayout.cols(w - 2 * pad - 4, tileW, 3)
		val grid = if (widenTiles) TileLayout.widened(w - 2 * pad, oldCols, tileW, 3) else TileLayout.Widened(oldCols, tileW, 3, 0)
		val cols = grid.cols; val gap = grid.gap; val tw = grid.tileW
		val tileH = TileLayout.tileHeight(tileH, groups.flatMap { it.second })
		val content = TileLayout.height(groups.map { it.second.size }, cols, tileH, gap)

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + 1, top, w - 2, bottom - top)
		scroller.layout(view, content - if (groups.any { it.second.isNotEmpty() }) gap else 0)
		scroll = scroller.offset
		beginClip(ctx, view)
		var cy = top - scroll
		for ((label, tiles) in groups) {
			if (cy + TileLayout.LABEL_H > top && cy < bottom) ctx.text(font, "§e$label §8(${tiles.size})", x + pad, cy + 2, MenuTheme.GOLD)
			cy += TileLayout.LABEL_H
			tiles.forEachIndexed { i, t ->
				val tx = x + pad + grid.x(i % cols); val ty = cy + (i / cols) * (tileH + gap)
				if (ty + tileH <= top || ty >= bottom) return@forEachIndexed
				tile(ctx, mx, my, tx, ty, tw, tileH, t)
			}
			cy += ((tiles.size + cols - 1) / cols) * (tileH + gap)
		}
		endClip(ctx)
		if (scroller.max > 0) MenuTheme.scrollbar(ctx, x + w - 4, top, bottom - top, bottom - top, scroller.contentHeight, scroll)
	}

	protected val scroller = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey() }
	override val scrollView get() = scroller

	private fun tile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, tileW: Int, tileH: Int, t: TileSpec) {
		val p = palette()
		val hover = hovered(mx, my, tx, ty, tileW, tileH)
		MenuTheme.frame(ctx, tx, ty, tx + tileW, ty + tileH, if (hover) p.fillHover else p.fill, if (hover) p.borderHover else p.border)
		(stacks[t.iconSlot ?: t.slot] ?: stacks[t.slot])?.let { ctx.item(it, tx + 3, ty + (tileH - 16) / 2) }

		val r = TileLayout.rects(tx, ty, tileW, tileH, t)
		var cut = false
		val badge = t.badge?.let { "§6$it" }
		val bw = badge?.let(font::width) ?: 0
		val room = TileText.titleRoom(r.title.w, bw)
		val title = "§f" + t.title
		if (font.width(title) > room) cut = true
		if (room > 6) ctx.text(font, MenuTheme.fit(title, room, font::width), r.title.x, r.title.y, MenuTheme.WHITE)
		if (badge != null) ctx.text(font, badge, r.title.x + r.title.w - bw, r.title.y, MenuTheme.GOLD)
		if (t.sub != null) {
			val b = r.sub
			if (b == null) cut = true
			else {
				val s = TileText.pick(listOf(t.sub) + t.subAlts, b.w, font::width)
				if (s != t.sub) cut = true
				ctx.text(font, s ?: MenuTheme.fit(t.subAlts.lastOrNull() ?: t.sub, b.w, font::width), b.x, b.y, t.colour)
			}
		}
		if (t.fraction != null) r.bar?.let { b ->
			MenuTheme.progressBar(ctx, font, b.x, b.y, b.w, b.h, t.fraction, 1.0, t.barText,
				fill = t.barColour ?: if (t.fraction >= 1) 0xFF55FF55.toInt() else 0xFFFFFF55.toInt(), palette = p)
		}
		area(tx, ty, tileW, tileH) { b -> clickSlot(t.slot, b) }

		if (hover) { hoverSlot = t.slot; hoverTip = t.tip ?: if (cut) listOfNotNull("§f${t.title}" + (t.badge?.let { " §6$it" } ?: ""), t.sub) else null }
	}

	protected fun navRow(ctx: GuiGraphicsExtractor, mx: Int, my: Int, nav: List<Pair<Int, String>>): Int {
		val by = y + h - 20
		var bx = x + 6
		for ((slot, name) in nav) {
			val label = when { name.equals("Go Back", true) -> "‹ Go Back"; name.equals("Next Page", true) -> "Next ›"; name.equals("Previous Page", true) -> "‹ Prev"; else -> name }
			val bw = minOf(font.width(label) + 12, 130)
			if (bx + bw > x + w - 6) break
			button(ctx, mx, my, bx, by, bw, 14, label) { b -> clickSlot(slot, b) }
			bx += bw + 3
		}
		return by
	}

	protected fun tooltip(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		val s = hoverSlot
		val tip = hoverTip
		if (tip != null) ctx.setComponentTooltipForNextFrame(font, tip.map { Component.literal(it) }, mx, my)
		else if (s != null) stacks[s]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) }
		hoverSlot = null; hoverTip = null
	}

	companion object {
		fun fmtPct(p: Double) = (if (p % 1.0 == 0.0) "%.0f" else "%.1f").format(java.util.Locale.ROOT, p) + "%"
		fun fmtNum(v: Double) = if (v >= 10_000) dev.ashtonite365.apex.features.menus.support.MenuText.coins(v)
			else if (v % 1.0 == 0.0) "%,d".format(java.util.Locale.ROOT, v.toLong()) else "%.1f".format(java.util.Locale.ROOT, v)
		fun stateColour(s: TileState) = when (s) {
			TileState.MAXED -> 0xFFFFAA00.toInt(); TileState.UNLOCKED -> 0xFF55FF55.toInt(); TileState.IN_PROGRESS -> 0xFFFFFF55.toInt()
			TileState.LOCKED -> 0xFFFF5555.toInt(); TileState.UNKNOWN -> MenuTheme.GREY
		}
	}
}
