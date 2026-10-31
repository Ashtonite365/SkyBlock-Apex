// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.essence

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.guides.GuideLore
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTileText
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class EssenceEntry(
	val slot: Int,
	val name: String,
	val type: String,
	val amountText: String?,
	val amount: Long?,

	val info: String,
	val tooltip: List<String>,
)

data class EssenceNav(val label: String, val slot: Int)

data class EssenceModel(val heading: String, val entries: List<EssenceEntry>, val nav: List<EssenceNav>) {
	val back get() = nav.firstOrNull { it.label.startsWith("Back") }
	val close get() = nav.firstOrNull { it.label == "Close" }
}

object EssenceParser {

	val TITLE = Regex("^Your Essence$")
	private val NAME = Regex("^(.+?) Essence$")

	private val OWN = Regex("You currently own ([\\d,]+)\\s+(.+?)\\s+Essence\\.?", RegexOption.IGNORE_CASE)

	fun amount(lore: List<String>): Pair<String, Long>? {
		val joined = lore.map(MenuText::clean).filter { it.isNotEmpty() }.joinToString(" ")
		val m = OWN.find(joined) ?: return null
		val text = m.groupValues[1]
		return text.replace(",", "").toLongOrNull()?.let { text to it }
	}

	private fun flavour(lore: List<String>) = GuideLore.paragraphs(lore).filterNot { OWN.containsMatchIn(it) }.joinToString(" · ")

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): EssenceModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val nav = HubText.navOf(chest).filter { it.first.startsWith("Back") || it.first == "Close" }.map { EssenceNav(it.first, it.second) }
		val navSlots = nav.map { it.slot }.toSet()

		val entries = chest.filter { it.slot !in navSlots && !it.cleanName.equals(t, true) }.mapNotNull { s ->
			val type = NAME.matchEntire(s.cleanName)?.groupValues?.get(1) ?: return@mapNotNull null
			val a = amount(s.lore)
			if (a == null && s.cleanLore.none { it.contains("Essence", true) }) return@mapNotNull null
			EssenceEntry(s.slot, s.cleanName, type, a?.first, a?.second, flavour(s.lore), HubText.clean(s.lore))
		}.sortedBy { it.slot }
		if (entries.isEmpty()) return null
		return EssenceModel(t, entries, nav)
	}
}

object EssenceLayout {
	const val PAD = 6
	const val TILE_W = 104
	const val TILE_MIN_H = 30
	const val GAP = 4
	const val HEADER_TOP = 7
	const val LINE = 9
	const val CELL = 18
	const val NAV_H = 18
	const val NAV_BOTTOM = 4
	const val ICON_X = 3
	const val TEXT_X = 22
	const val TEXT_W = TILE_W - 26

	fun navTop(y: Int, h: Int) = y + h - NAV_BOTTOM - NAV_H

	fun floor(y: Int, h: Int) = navTop(y, h) - ScrollClip.BAR_GAP

	fun gridTop(y: Int, headingLines: Int) = y + HEADER_TOP + maxOf(1, headingLines) * LINE + 6

	fun cols(w: Int, n: Int) = ((w - 2 * PAD + GAP) / (TILE_W + GAP)).coerceAtLeast(1).coerceAtMost(maxOf(1, n))

	fun tileText(e: EssenceEntry, measure: (String) -> Int): Pair<List<String>, List<String>> =
		HubTileText.wrap("§f" + e.name, e.amountText?.let { "Owned: §e$it" } ?: e.info.ifEmpty { null }, TEXT_W, measure)

	fun tileHeight(e: EssenceEntry, measure: (String) -> Int) =
		tileText(e, measure).let { (n, i) -> HubTileText.height(n.size, i.size) }.coerceAtLeast(TILE_MIN_H)

	data class Grid(val tiles: List<MenuBounds>, val contentHeight: Int)

	fun grid(x: Int, w: Int, top: Int, floor: Int, heights: List<Int>): Grid {
		if (heights.isEmpty()) return Grid(emptyList(), 0)
		val cols = cols(w, heights.size)
		val rows = heights.indices.chunked(cols)
		val rowH = rows.map { r -> r.maxOf { heights[it] } }
		val content = rowH.sum() + GAP * (rows.size - 1)
		val y0 = top + maxOf(0, (floor - top - content) / 2)
		val out = ArrayList<MenuBounds>(heights.size)
		var ry = y0
		rows.forEachIndexed { ri, r ->
			val rowW = r.size * TILE_W + (r.size - 1) * GAP
			val rx = x + (w - rowW) / 2
			r.forEachIndexed { i, _ -> out += MenuBounds(rx + i * (TILE_W + GAP), ry, TILE_W, rowH[ri]) }
			ry += rowH[ri] + GAP
		}
		return Grid(out, (y0 - top) + content)
	}

	data class NavButton(val nav: EssenceNav, val frame: MenuBounds, val cell: MenuBounds, val label: MenuBounds)

	fun nav(x: Int, y: Int, h: Int, nav: List<EssenceNav>, measure: (String) -> Int): List<NavButton> {
		var nx = x + PAD; val ny = navTop(y, h)
		return nav.map { n ->
			val lw = measure(n.label) + 10
			val b = NavButton(n, MenuBounds(nx, ny, CELL + lw, NAV_H), MenuBounds(nx, ny, CELL, CELL), MenuBounds(nx + CELL, ny, lw, NAV_H))
			nx += CELL + lw + 4; b
		}
	}
}

@Config object EssenceMenuSettings : MenuSettings("essence") { val reset by button("reset") { resetToDefaults() } }

object EssenceMenu : CustomMenu {
	override val id = "essence"
	override val displayName = "Your Essence"
	override val title = EssenceParser.TITLE
	override val settings get() = EssenceMenuSettings

	override val defaultSize = 440 to 220
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) EssenceView(screen) else null
}

class EssenceView(screen: AbstractContainerScreen<*>) : ApexViewBase<EssenceModel>(screen) {
	override fun palette() = EssenceMenuSettings.customisation.palette
	override val logTag get() = "[ApexEssence]"
	private val scroller = ScrollView { scrollKey() }
	override val scrollView get() = scroller
	private var lastUnread: String? = null

	override fun parse(title: String, slots: List<SlotText>): EssenceModel? {
		val m = EssenceParser.parse(title, slots, screen.menu.slots.size - 36)

		if (m == null && EssenceParser.TITLE.matches(MenuText.clean(title))) {
			val why = "no essence slots (${slots.count { it.cleanName.isNotEmpty() }} named slots)"
			if (why != lastUnread) { lastUnread = why; dev.ashtonite365.apex.Apex.logger.warn("[ApexEssence] unreadable: $why") }
		} else lastUnread = null
		return m
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: EssenceModel, mx: Int, my: Int) {
		val p = palette(); val pad = EssenceLayout.PAD

		if (drawingCached) {
			val t = "Reading menu…"
			ctx.text(font, "§7$t", x + (w - font.width(t)) / 2, y + h / 2 - 4, MenuTheme.GREY); return
		}

		val vw = font.width("Vanilla") + 10

		val heading = dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText("§6§l" + m.heading, w - 2 * pad - vw - 8, font::width)
		heading.forEachIndexed { i, l -> ctx.text(font, l, x + pad, y + EssenceLayout.HEADER_TOP + i * EssenceLayout.LINE, MenuTheme.WHITE) }
		val top = EssenceLayout.gridTop(y, heading.size)
		val floor = EssenceLayout.floor(y, h)
		val grid = EssenceLayout.grid(x, w, top, floor, m.entries.map { EssenceLayout.tileHeight(it, font::width) })
		val view = MenuBounds(x + 1, top, w - 2, maxOf(0, floor - top))
		scroller.layout(view, grid.contentHeight)
		var hover: EssenceEntry? = null
		beginClip(ctx, view)
		m.entries.forEachIndexed { i, e ->
			val r = grid.tiles[i]; val ty = r.y - scroller.offset
			if (ty >= floor || ty + r.h <= top) return@forEachIndexed
			val hov = hovered(mx, my, r.x, ty, r.w, r.h)
			MenuTheme.button(ctx, font, r.x, ty, r.w, r.h, "", MenuTheme.state(hov, false, true), palette = p)
			stacks[e.slot]?.let { ctx.item(it, r.x + EssenceLayout.ICON_X, ty + (r.h - 16) / 2) }
			val (nameLines, infoLines) = EssenceLayout.tileText(e, font::width)
			var ly = ty + 5
			nameLines.forEach { ctx.text(font, it, r.x + EssenceLayout.TEXT_X, ly, MenuTheme.WHITE); ly += EssenceLayout.LINE }
			ly += 3
			infoLines.forEach { ctx.text(font, it, r.x + EssenceLayout.TEXT_X, ly, MenuTheme.GREY); ly += EssenceLayout.LINE }
			if (hov) hover = e
		}
		endClip(ctx)

		for (b in EssenceLayout.nav(x, y, h, m.nav, font::width)) {
			val hovLabel = hovered(mx, my, b.label.x, b.label.y, b.label.w, b.label.h)
			MenuTheme.button(ctx, font, b.frame.x, b.frame.y, b.frame.w, b.frame.h, "", MenuTheme.state(hovLabel), palette = p)
			MenuTheme.frame(ctx, b.cell.x, b.cell.y, b.cell.x + b.cell.w, b.cell.y + b.cell.h, p.fill, p.border)
			realSlotCell(b.nav.slot, b.cell.x, b.cell.y)
			ctx.text(font, b.nav.label, b.label.x + 5, b.label.y + 5, MenuTheme.WHITE)
			area(b.label.x, b.label.y, b.label.w, b.label.h) { btn -> clickSlot(b.nav.slot, btn) }
		}

		hover?.let { e ->
			val st = stacks[e.slot]
			if (st != null && !st.isEmpty) { if (screen.menu.carried.isEmpty) ctx.setTooltipForNextFrame(font, st, mx, my) }
			else ctx.setComponentTooltipForNextFrame(font, (listOf(e.name) + e.tooltip).map { Component.literal(it) }, mx, my)
		}
	}
}
