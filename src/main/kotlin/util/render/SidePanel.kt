/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): shared side-panel renderer + layout manager. Original implementation.
 */
package dev.ashtonite365.apex.util.render

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.features.misc.SolidMenus
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.accessors.getProperRectangle

object SidePanel {

	enum class Side { LEFT, RIGHT, TOP, BOTTOM }

	data class Palette(val rim: Int, val fill: Int, val light: Int, val dark: Int, val text: Int, val shadow: Boolean, val hover: Int,
		val themed: Boolean = false, val radius: Int = 0, val frost: Boolean = false, val darkText: Boolean = false)

	val LIGHT = Palette(0xFF000000.toInt(), 0xFFC6C6C6.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt(), 0xFF404040.toInt(), false, 0x40000000)
	val DARK = Palette(0xFF000000.toInt(), 0xFF2B2B2B.toInt(), 0xFF4A4A4A.toInt(), 0xFF141414.toInt(), 0xFFE0E0E0.toInt(), true, 0x40FFFFFF)

	val basePalette get() = if (SolidMenus.TConfig.panelTheme == SolidMenus.Theme.DARK) DARK else LIGHT

	val palette: Palette get() = paletteFor(apexMenuShown(Client.screen), basePalette) { dev.ashtonite365.apex.features.menus.style.MenuStyle.palette }

	fun paletteFor(apexMenu: Boolean, base: Palette, style: () -> dev.ashtonite365.apex.features.menus.style.StylePalette): Palette =
		if (!apexMenu) base else runCatching { themed(style()) }.getOrDefault(base)

	fun themed(sp: dev.ashtonite365.apex.features.menus.style.StylePalette): Palette {
		val luminance = ((sp.text ushr 16 and 0xFF) * 3 + (sp.text ushr 8 and 0xFF) * 6 + (sp.text and 0xFF)) / 10
		return Palette(rim = sp.border, fill = sp.card, light = sp.rimHighlight, dark = sp.shadow, text = sp.text,
			shadow = sp.textShadow, hover = sp.buttonHover, themed = true, radius = sp.cardRadius, frost = sp.blurWorld,
			darkText = luminance < 0x80)
	}

	fun apexMenuShown(screen: Screen?): Boolean {
		val gui = (screen as? dev.ashtonite365.apex.util.customgui.HasCustomGui)?.getCustomGui_Apex() ?: return false
		return gui.sidePanelAnchor() != null
	}

	fun anchorOf(screen: AbstractContainerScreen<*>): Rectangle {
		val gui = (screen as? dev.ashtonite365.apex.util.customgui.HasCustomGui)?.getCustomGui_Apex()
		gui?.let { g -> runCatching { g.sidePanelAnchor() }.getOrNull()?.let { return it } }
		val bounds = gui?.let { runCatching { it.getBounds() }.getOrNull() }.orEmpty()
		return union(bounds) ?: screen.getProperRectangle()
	}

	fun union(rects: List<Rectangle>): Rectangle? {
		if (rects.isEmpty()) return null
		val x = rects.minOf { it.x }; val y = rects.minOf { it.y }
		return Rectangle(x, y, rects.maxOf { it.x + it.width } - x, rects.maxOf { it.y + it.height } - y)
	}

	fun withOpacity(p: Palette, percent: Int): Palette {
		if (percent >= 100) return p
		fun f(c: Int) = (((c ushr 24) * percent.coerceAtLeast(0) / 100) shl 24) or (c and 0xFFFFFF)
		return p.copy(rim = f(p.rim), fill = f(p.fill), light = f(p.light), dark = f(p.dark), hover = f(p.hover))
	}

	const val PADDING = 6
	const val GAP = 4
	const val MARGIN = 2
	const val ROW_H = 10
	const val COLUMN_GAP = 8
	const val MIN_SCALE = 0.5f

	data class Row(val left: String, val right: String = "")

	class Placed(
		val id: String, val x: Int, val y: Int, val w: Int, val h: Int, val scale: Float,
		val title: String?, val rows: List<Row>, val headerH: Int, val innerW: Int,
	) {
		val rect get() = Rectangle(x, y, w, h)

		private fun titleH() = if (title == null) 0 else ROW_H + 2

		fun rowY(i: Int) = y + PADDING + ((titleH() + headerH + i * ROW_H) * scale).toInt()

		fun rowH() = (ROW_H * scale).toInt()

		fun headerY() = y + PADDING + (titleH() * scale).toInt()

		fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
	}

	private val nextY = mutableMapOf<Side, Int>()
	private var frameScreen: Screen? = null

	val lastRects = mutableMapOf<String, Rectangle>()
	private val thisFrame = mutableMapOf<String, Rectangle>()
	private val deferred = mutableListOf<() -> Unit>()

	@JvmStatic
	fun beginFrame(screen: Screen) {
		frameScreen = screen
		nextY.clear()
		thisFrame.clear()
		deferred.clear()
	}

	fun defer(block: () -> Unit) { deferred.add(block) }

	@JvmStatic
	fun endFrame() {
		while (deferred.isNotEmpty()) {
			val pending = deferred.toList()
			deferred.clear()
			pending.forEach { it() }
		}
		lastRects.clear()
		lastRects.putAll(thisFrame)
	}

	private val NUMBER = Regex("(?:(?<=§[0-9a-fk-orA-FK-OR])|(?<![\\w.§]))([+-]?)(\\d{1,3}(?:,\\d{3})+|\\d{4,})(\\.\\d+)?")

	fun abbreviateNumbers(s: String): String = NUMBER.replace(s) { m ->
		val (sign, whole, fraction) = m.destructured
		val value = (whole.replace(",", "") + fraction).toDoubleOrNull()
		if (value == null || value < 1000) m.value else sign + abbreviate(value)
	}

	fun abbreviate(v: Double): String {
		val units = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "k")
		val (divisor, suffix) = units.firstOrNull { abs(v) >= it.first } ?: return String.format(Locale.ROOT, "%.0f", v)
		val shown = v / divisor
		val digits = if (abs(shown) >= 100) "%.0f" else "%.1f"
		return String.format(Locale.ROOT, digits, shown).removeSuffix(".0") + suffix
	}

	private fun width(s: String) = Client.font.width(s)

	fun contentWidth(title: String?, rows: List<Row>, minInner: Int): Int {
		var widest = maxOf(minInner, title?.let(::width) ?: 0)
		for (row in rows) {
			val both = width(row.left) + if (row.right.isEmpty()) 0 else COLUMN_GAP + width(row.right)
			widest = maxOf(widest, both)
		}
		return widest
	}

	private fun topFor(screen: AbstractContainerScreen<*>, side: Side, anchor: Rectangle): Int {
		if (frameScreen !== screen) beginFrame(screen)
		return nextY[side] ?: anchor.y
	}

	private fun claim(id: String, side: Side, box: Rectangle) {
		thisFrame[id] = box

		nextY[side] = if (side == Side.TOP) box.y - GAP else box.y + box.height + GAP
	}

	fun place(id: String, screen: AbstractContainerScreen<*>, side: Side, title: String?, rows: List<Row>, headerH: Int = 0, minInner: Int = 0, userScale: Float = 1f): Placed? {
		val anchor = anchorOf(screen)
		val room = when (side) {
			Side.LEFT -> anchor.x - GAP - MARGIN
			Side.RIGHT -> screen.width - (anchor.x + anchor.width + GAP) - MARGIN
			Side.TOP, Side.BOTTOM -> screen.width - MARGIN * 2
		} - PADDING * 2
		if (room <= 0) return null
		var shownTitle = title
		var shownRows = rows
		var inner = contentWidth(shownTitle, shownRows, minInner)
		if (inner * userScale > room) {
			shownTitle = title?.let(::abbreviateNumbers)
			shownRows = rows.map { Row(abbreviateNumbers(it.left), abbreviateNumbers(it.right)) }
			inner = contentWidth(shownTitle, shownRows, minInner)
		}

		val scale = minOf(userScale, if (inner > 0) room.toFloat() / inner else userScale)
		if (scale < MIN_SCALE * userScale) return null
		val titleRows = if (shownTitle == null) 0 else ROW_H + 2
		val contentH = titleRows + headerH + shownRows.size * ROW_H
		val w = ceil(inner * scale).toInt() + PADDING * 2
		val h = ceil(contentH * scale).toInt() + PADDING * 2
		val box = slot(screen, side, anchor, w, h) ?: return null
		val placed = Placed(id, box.x, box.y, w, h, scale, shownTitle, shownRows, headerH, inner)
		claim(id, side, placed.rect)
		return placed
	}

	private fun slot(screen: AbstractContainerScreen<*>, side: Side, anchor: Rectangle, w: Int, h: Int): Rectangle? {
		if (frameScreen !== screen) beginFrame(screen)
		val centredX = (anchor.x + (anchor.width - w) / 2).coerceIn(MARGIN, maxOf(MARGIN, screen.width - MARGIN - w))
		return when (side) {
			Side.LEFT, Side.RIGHT -> {
				val top = topFor(screen, side, anchor)
				if (top + h > screen.height - MARGIN) return null
				Rectangle(if (side == Side.LEFT) (anchor.x - GAP - w).coerceAtLeast(MARGIN) else anchor.x + anchor.width + GAP, top, w, h)
			}
			Side.TOP -> {
				val bottom = nextY[side] ?: (anchor.y - GAP)
				if (bottom - h < MARGIN) return null
				Rectangle(centredX, bottom - h, w, h)
			}
			Side.BOTTOM -> {
				val top = nextY[side] ?: (anchor.y + anchor.height + GAP)
				if (top + h > screen.height - MARGIN) return null
				Rectangle(centredX, top, w, h)
			}
		}
	}

	fun offset(p: Placed, dx: Int, dy: Int, screen: Screen): Placed {
		if (dx == 0 && dy == 0) return p
		val x = (p.x + dx).coerceIn(0, maxOf(0, screen.width - p.w)); val y = (p.y + dy).coerceIn(0, maxOf(0, screen.height - p.h))
		return Placed(p.id, x, y, p.w, p.h, p.scale, p.title, p.rows, p.headerH, p.innerW).also { thisFrame[p.id] = it.rect }
	}

	fun placeAt(id: String, screen: Screen, x: Int, y: Int, title: String?, rows: List<Row>, userScale: Float = 1F): Placed {
		if (frameScreen !== screen) beginFrame(screen)
		val inner = contentWidth(title, rows, 0)
		val contentH = (if (title != null) ROW_H + 2 else 0) + rows.size * ROW_H
		val w = ceil(inner * userScale).toInt() + PADDING * 2
		val h = ceil(contentH * userScale).toInt() + PADDING * 2
		val left = x.coerceIn(0, maxOf(0, screen.width - w))
		val top = y.coerceIn(0, maxOf(0, screen.height - h))
		return Placed(id, left, top, w, h, userScale, title, rows, 0, inner).also { thisFrame[id] = it.rect }
	}

	fun reserve(id: String, screen: AbstractContainerScreen<*>, side: Side, w: Int, h: Int): Rectangle? {
		val box = slot(screen, side, anchorOf(screen), w, h) ?: return null
		return box.also { claim(id, side, it) }
	}

	fun draw(ctx: GuiGraphicsExtractor, p: Placed, highlightRow: Int? = null, pal: Palette = palette) {
		val colours = pal
		drawBox(ctx, p.x, p.y, p.x + p.w, p.y + p.h, colours)
		val pose = ctx.pose()
		pose.pushMatrix()
		pose.translate((p.x + PADDING).toFloat(), (p.y + PADDING).toFloat())
		pose.scale(p.scale, p.scale)
		var top = 0
		p.title?.let {
			text(ctx, it, 0, top, colours)
			top += ROW_H + 2
		}
		top += p.headerH
		p.rows.forEachIndexed { i, row ->
			val rowTop = top + i * ROW_H
			if (i == highlightRow) ctx.fill(-2, rowTop - 1, p.innerW + 2, rowTop + ROW_H - 1, colours.hover)
			text(ctx, row.left, 0, rowTop, colours)
			if (row.right.isNotEmpty()) text(ctx, row.right, p.innerW - width(row.right), rowTop, colours)
		}
		pose.popMatrix()
	}

	@JvmStatic
	fun drawBox(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, p: Palette = palette) {
		if (p.themed && x2 - x1 >= 4 && y2 - y1 >= 4) {

			if (p.frost) dev.ashtonite365.apex.features.menus.style.Frost.draw(ctx, x1, y1, x2 - x1, y2 - y1, p.radius)
			val c = dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, Client.font)
			dev.ashtonite365.apex.features.menus.style.Shapes.roundFrame(c, x1, y1, x2 - x1, y2 - y1, p.radius, p.fill, p.rim, glass = p.frost)
			return
		}
		if (x2 - x1 < 4 || y2 - y1 < 4) {
			ctx.fill(x1, y1, x2, y2, p.fill)
			return
		}
		ctx.fill(x1, y1, x2, y2, p.rim)
		ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, p.light)
		ctx.fill(x1 + 2, y1 + 2, x2 - 1, y2 - 1, p.dark)
		ctx.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, p.fill)
	}

	private val lightRemap = mapOf('f' to '0', '7' to '8', 'e' to '6', 'a' to '2', 'b' to '3', 'd' to '5', 'c' to '4', '9' to '1')

	fun adapt(s: String, p: Palette = palette): String {
		if ((p.text != LIGHT.text && !(p.themed && p.darkText)) || '§' !in s) return s
		val out = StringBuilder(s.length)
		var afterMarker = false
		for (ch in s) {
			out.append(if (afterMarker) lightRemap[ch.lowercaseChar()] ?: ch else ch)
			afterMarker = ch == '§'
		}
		return out.toString()
	}

	fun text(ctx: GuiGraphicsExtractor, s: String, x: Int, y: Int, p: Palette = palette) =
		ctx.text(Client.font, adapt(s, p), x, y, p.text, p.shadow)

	fun text(ctx: GuiGraphicsExtractor, s: net.minecraft.network.chat.Component, x: Int, y: Int, p: Palette = palette): Unit =
		ctx.text(Client.font, s, x, y, p.text, p.shadow)
}
