// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object MenuTheme {
	const val BG = 0xF0141418.toInt()
	const val BORDER = 0xFF5A5A66.toInt()
	const val BORDER_HOVER = 0xFF9A9AAA.toInt()
	const val BORDER_ACTIVE = 0xFFFFAA00.toInt()
	const val FILL = 0xFF202028.toInt()
	const val FILL_HOVER = 0xFF3A3A50.toInt()
	const val FILL_PRESSED = 0xFF101014.toInt()
	const val FILL_ACTIVE = 0xFF3A3020.toInt()
	const val FILL_DISABLED = 0xFF18181C.toInt()
	const val WHITE = 0xFFFFFFFF.toInt()
	const val GOLD = 0xFFFFAA00.toInt()
	const val GREY = 0xFFA0A0A0.toInt()
	const val DIM = 0xFF606060.toInt()

	const val SELECTED_ROW = 0x50FFAA00
	const val HOVER_ROW = 0x30FFFFFF
	const val WARN_FILL = 0xF0200000.toInt()
	const val SCROLL_KNOB = 0xFF909090.toInt()
	const val PANE = 0xC0000000.toInt()
	const val PROGRESS_FILL = 0xFF55FF55.toInt()

	data class Palette(
		val bg: Int = BG, val border: Int = BORDER, val borderHover: Int = BORDER_HOVER, val accent: Int = GOLD,
		val fill: Int = FILL, val fillHover: Int = FILL_HOVER, val fillPressed: Int = FILL_PRESSED,
		val fillActive: Int = FILL_ACTIVE, val fillDisabled: Int = FILL_DISABLED,
		val text: Int = WHITE, val textDim: Int = GREY, val disabled: Int = DIM,
	) {

		fun withAccent(argb: Int) = copy(accent = argb or (0xFF shl 24))
		companion object { val DEFAULT = Palette() }
	}

	enum class State { NORMAL, HOVER, PRESSED, ACTIVE, DISABLED }

	fun state(hover: Boolean, active: Boolean, enabled: Boolean, pressed: Boolean): State = when {
		!enabled -> State.DISABLED
		hover && pressed -> State.PRESSED
		active -> State.ACTIVE
		hover -> State.HOVER
		else -> State.NORMAL
	}

	fun state(hover: Boolean, active: Boolean = false, enabled: Boolean = true): State =
		state(hover, active, enabled, Minecraft.getInstance().mouseHandler.isLeftPressed)

	fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

	fun buttonColours(state: State, p: Palette = Palette.DEFAULT): Triple<Int, Int, Int> = when (state) {
		State.NORMAL -> Triple(p.border, p.fill, p.text)
		State.HOVER -> Triple(p.borderHover, p.fillHover, p.text)
		State.PRESSED -> Triple(p.accent, p.fillPressed, p.accent)
		State.ACTIVE -> Triple(p.accent, p.fillActive, p.accent)
		State.DISABLED -> Triple(p.disabled, p.fillDisabled, p.disabled)
	}

	fun shade(argb: Int, f: Float = 0.55f): Int {
		val r = ((argb shr 16 and 0xFF) * f).toInt(); val g = ((argb shr 8 and 0xFF) * f).toInt(); val b = ((argb and 0xFF) * f).toInt()
		return (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
	}

	private val radius get() = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.controlRadius

	private val glass get() = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.style == dev.ashtonite365.apex.features.menus.style.Style.SKYGLASS
	private val cardRadius get() = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.cardRadius

	fun button(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, label: String, state: State, textX: Int? = null, palette: Palette = Palette.DEFAULT) {
		val (border, fill, text) = buttonColours(state, palette)
		val c = dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, font)
		val raised = state != State.PRESSED && state != State.DISABLED
		val nudge = if (state == State.PRESSED) 1 else 0
		if (raised) dev.ashtonite365.apex.features.menus.style.Shapes.round(c, x, y + 1, w, h - 1, radius, shade(border))
		dev.ashtonite365.apex.features.menus.style.Shapes.roundFrame(c, x, y + nudge, w, h - 1, radius, fill, border, glass)
		if (raised && w > 2 * radius + 2) ctx.fill(x + radius + 1, y + 1, x + w - radius - 1, y + 2, 0x30FFFFFF)
		if (textX == null && font.width(label) > w - 4) { fittedLabel(ctx, font, label, x + w / 2, y + nudge, w - 4, h - 2, text, true); return }
		val tx = textX ?: (x + (w - font.width(label)) / 2)
		ctx.text(font, label, tx, y + (h - 9) / 2 + nudge, text)
	}

	fun fittedLabel(ctx: GuiGraphicsExtractor, font: Font, label: String, cx: Int, top: Int, w: Int, h: Int, colour: Int, centreY: Boolean, shadow: Boolean = true) {
		val f = dev.ashtonite365.apex.features.menus.support.MenuText.fitLabel(label, w, { font.width(it) }, 2, h)
		val lineH = 9f * f.scale
		val total = lineH * f.lines.size
		val y0 = if (centreY) top + (h - total) / 2f + 1 else top.toFloat()
		val pose = ctx.pose()
		pose.pushMatrix()
		pose.translate(cx.toFloat(), y0)
		pose.scale(f.scale)
		f.lines.forEachIndexed { i, l -> ctx.text(font, l, -font.width(l) / 2, i * 9, colour, shadow) }
		pose.popMatrix()
	}

	fun panel(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, palette: Palette = Palette.DEFAULT) {
		val c = dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, net.minecraft.client.Minecraft.getInstance().font)
		val sp = dev.ashtonite365.apex.features.menus.style.MenuStyle.palette
		dev.ashtonite365.apex.features.menus.style.Shapes.round(c, x, y + 2, w, h, cardRadius, sp.shadow)
		if (sp.blurWorld) c.frost(x, y, w, h, cardRadius)
		dev.ashtonite365.apex.features.menus.style.Shapes.roundFrame(c, x, y, w, h, cardRadius, palette.bg, palette.border, glass)
		ctx.fill(x + cardRadius, y, x + w - cardRadius, y + 1, sp.rimHighlight)
		dev.ashtonite365.apex.features.menus.style.ForgeMetal.plate(c, sp, x, y, w, h)
	}

	fun frame(ctx: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, fill: Int, border: Int = BORDER) {
		val r = minOf(radius, (x1 - x0) / 4, (y1 - y0) / 4)
		if (r <= 0) { ctx.fill(x0, y0, x1, y1, border); ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill); return }
		val c = dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, net.minecraft.client.Minecraft.getInstance().font)
		dev.ashtonite365.apex.features.menus.style.Shapes.roundFrame(c, x0, y0, x1 - x0, y1 - y0, r, fill, border, glass)
	}

	fun scrollKnob(trackH: Int, visible: Int, total: Int, scroll: Int): Pair<Int, Int> {
		if (total <= visible || total <= 0) return 0 to trackH
		val kh = maxOf(8, trackH * visible / total)
		val maxScroll = total - visible
		return (trackH - kh) * scroll.coerceIn(0, maxScroll) / maxScroll to kh
	}

	fun scrollbar(ctx: GuiGraphicsExtractor, x: Int, y: Int, h: Int, visible: Int, total: Int, scroll: Int) {
		if (total <= visible) return
		val (top, kh) = scrollKnob(h, visible, total, scroll)
		ctx.fill(x, y + top, x + 2, y + top + kh, SCROLL_KNOB)
	}

	fun progressWidth(w: Int, value: Double, max: Double): Int =
		if (max <= 0) 0 else (w * (value / max).coerceIn(0.0, 1.0)).toInt()

	fun progressBar(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, value: Double, max: Double, label: String? = null, fill: Int = PROGRESS_FILL, palette: Palette = Palette.DEFAULT) {
		frame(ctx, x, y, x + w, y + h, palette.fill, palette.border)
		val fw = progressWidth(w - 2, value, max)
		if (fw > 0) ctx.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, fill)
		if (label != null) fitBarText(ctx, font, label, x, y, w, h, palette.text)
	}

	const val TEXT_H = 9

	data class FitText(val scale: Float, val x: Float, val y: Float, val w: Float, val h: Float)

	fun fitBarTextRect(barX: Int, barY: Int, barW: Int, barH: Int, textW: Int, textH: Int = TEXT_H): FitText {
		val s = minOf(1f, if (textW > 0) barW.toFloat() / textW else 1f, if (textH > 0) barH.toFloat() / textH else 1f)
		if (s >= 1f) {
			val ty = barY + maxOf(0, minOf((barH - 8) / 2, barH - textH))
			return FitText(1f, (barX + (barW - textW) / 2).toFloat(), ty.toFloat(), textW.toFloat(), textH.toFloat())
		}
		val w = textW * s; val h = textH * s
		return FitText(s, barX + (barW - w) / 2f, barY + (barH - h) / 2f, w, h)
	}

	fun fitBarText(ctx: GuiGraphicsExtractor, font: Font, label: String, barX: Int, barY: Int, barW: Int, barH: Int, colour: Int = WHITE) {
		val r = fitBarTextRect(barX, barY, barW, barH, font.width(label))
		if (r.scale >= 1f) { ctx.text(font, label, r.x.toInt(), r.y.toInt(), colour, true); return }
		ctx.pose().pushMatrix(); ctx.pose().translate(r.x, r.y); ctx.pose().scale(r.scale, r.scale)
		ctx.text(font, label, 0, 0, colour, true)
		ctx.pose().popMatrix()
	}

	object BarTile {
		const val PAD_TOP = 3
		const val GAP = 1
		const val BAR_H = 10
		const val PAD_BOTTOM = 2
		fun height(hasSub: Boolean, hasBar: Boolean) =
			PAD_TOP + TEXT_H + (if (hasSub) GAP + TEXT_H else 0) + (if (hasBar) GAP + BAR_H else 0) + PAD_BOTTOM

		val FULL = height(true, true)

		data class Rects(val title: MenuBounds, val sub: MenuBounds?, val bar: MenuBounds?)

		fun rects(tx: Int, ty: Int, tw: Int, th: Int, textX: Int, hasSub: Boolean, hasBar: Boolean,
		          barX: Int = tx + 3, barRight: Int = tx + tw - 3): Rects {
			val textW = tx + tw - 4 - textX
			if (!hasBar)
				return Rects(MenuBounds(textX, ty + 4, textW, TEXT_H), if (hasSub && th >= 22) MenuBounds(textX, ty + 14, textW, TEXT_H) else null, null)
			val title = MenuBounds(textX, ty + PAD_TOP, textW, TEXT_H)
			val showSub = hasSub && th >= height(true, false)
			val sub = if (showSub) MenuBounds(textX, title.y + TEXT_H + GAP, textW, TEXT_H) else null
			val showBar = th >= height(showSub, true)
			val bar = if (showBar) MenuBounds(barX, ty + th - PAD_BOTTOM - BAR_H, barRight - barX, BAR_H) else null
			return Rects(title, sub, bar)
		}
	}

	fun warnBox(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, lines: List<String>): Int {
		val h = lines.size * 10 + 4
		ctx.fill(x, y, x + w, y + h, WARN_FILL)
		lines.forEachIndexed { i, l -> ctx.text(font, l, x + 3, y + 2 + i * 10, WHITE) }
		return h
	}

	fun chip(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, label: String, active: Boolean, hover: Boolean, palette: Palette = Palette.DEFAULT) =
		button(ctx, font, x, y, w, 12, label, state(hover, active), palette = palette)

	fun tabOffsets(widths: List<Int>, pad: Int = 8, gap: Int = 2): List<Int> =
		widths.runningFold(0) { acc, tw -> acc + tw + pad + gap }.dropLast(1)

	fun tabStrip(ctx: GuiGraphicsExtractor?, font: Font, x: Int, y: Int, labels: List<String>, selected: Int, mx: Int, my: Int, palette: Palette = Palette.DEFAULT): Int =
		tabRows(ctx, font, x, y, Int.MAX_VALUE / 4, labels, selected, mx, my, palette).first

	fun tabRows(ctx: GuiGraphicsExtractor?, font: Font, x: Int, y: Int, maxWidth: Int, labels: List<String>, selected: Int, mx: Int, my: Int,
	            palette: Palette = Palette.DEFAULT, rowH: Int = 14, rowGap: Int = 2): Pair<Int, Int> {
		val placed = dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapTabs(labels.map { font.width(it) + 8 }, maxWidth)
		var hit = -1
		placed.forEachIndexed { i, t ->
			val ty = y + t.row * (rowH + rowGap)
			val over = inside(mx, my, x + t.x, ty, t.width, rowH)
			if (over) hit = i
			if (ctx != null) button(ctx, font, x + t.x, ty, t.width, rowH, fit(labels[i], t.width - 6, font::width), state(over, i == selected), palette = palette)
		}
		val rows = (placed.maxOfOrNull { it.row } ?: -1) + 1
		return hit to (rows * rowH + (rows - 1).coerceAtLeast(0) * rowGap)
	}

	const val MIN_TEXT_SCALE = 0.7f

	data class Fitted(val text: String, val scale: Float)

	fun fitScaled(s: String, width: Int, measure: (String) -> Int, min: Float = MIN_TEXT_SCALE): Fitted {
		val w = measure(s)
		if (w <= width || width <= 0) return Fitted(s, 1f)
		val sc = width.toFloat() / w
		if (sc >= min) return Fitted(s, sc)
		return Fitted(fit(s, (width / min).toInt(), measure), min)
	}

	fun drawFitted(ctx: GuiGraphicsExtractor, font: Font, s: String, x: Int, y: Int, width: Int, colour: Int = WHITE) {
		val f = fitScaled(s, width, font::width)
		if (f.scale >= 1f) { ctx.text(font, f.text, x, y, colour); return }
		ctx.pose().pushMatrix(); ctx.pose().translate(x.toFloat(), y + 8 * (1 - f.scale) / 2); ctx.pose().scale(f.scale, f.scale)
		ctx.text(font, f.text, 0, 0, colour)
		ctx.pose().popMatrix()
	}

	fun wrapFit(s: String, width: Int, maxLines: Int, measure: (String) -> Int, prefix: String = ""): List<String> {
		if (width <= 0 || maxLines <= 0) return emptyList()
		val lines = dev.ashtonite365.apex.gui.config.RapidClickBox.wrap(s, width - measure(prefix)) { measure(prefix + it) }
		if (lines.size <= maxLines) return lines.map { prefix + it }
		val kept = lines.take(maxLines).toMutableList()
		kept[maxLines - 1] = fit(prefix + kept[maxLines - 1] + " " + lines[maxLines], width, measure)
		return kept.mapIndexed { i, l -> if (i == maxLines - 1) l else prefix + l }
	}

	fun fit(s: String, width: Int, measure: (String) -> Int): String {
		if (measure(s) <= width) return s
		var t = s
		while (t.isNotEmpty() && measure("$t…") > width) t = t.dropLast(1)
		return "$t…"
	}
}
