// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.h6

import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.PurseReadout
import dev.ashtonite365.apex.features.menus.support.SlotText

data class HubTile(
	val slot: Int,
	val name: String,
	val info: List<String> = emptyList(),
	val button: Int = 0,
	val locked: Boolean = false,

	val delayed: Boolean = false,

	val shiftButton: Int? = null,
	val tooltip: List<String> = emptyList(),

	val clickable: Boolean = true,

	val highlight: Boolean = false,

	val realItem: Boolean = false,

	val leftPicksUp: Boolean = false,
)

object HubClicks {
	enum class Kind { BUTTON, ITEM_PICKUP, ITEM_QUICK_MOVE }
	data class Route(val button: Int, val kind: Kind)
	fun route(t: HubTile, b: Int, shift: Boolean): Route =
		if (t.leftPicksUp && b == 0) Route(0, if (shift) Kind.ITEM_QUICK_MOVE else Kind.ITEM_PICKUP)
		else if (t.leftPicksUp) Route(1, Kind.BUTTON)
		else if (t.realItem) Route(if (b == 1) 1 else 0, if (shift) Kind.ITEM_QUICK_MOVE else Kind.ITEM_PICKUP)
		else Route(if (b == 0 && t.shiftButton != null && shift) t.shiftButton else if (b == 1) 1 else t.button, Kind.BUTTON)
}

data class HubSection(val title: String, val tiles: List<HubTile>)

data class HubModel(
	val heading: String,
	val chips: List<Pair<String, String>> = emptyList(),
	val sections: List<HubSection> = emptyList(),
	val nav: List<Pair<String, Int>> = emptyList(),

	val tabs: Boolean = false,
	val selectedTab: Int = 0,

	val emptyText: String = "Empty",

	val locked: String? = null,
) {
	val allTiles get() = sections.flatMap { it.tiles }
}

object HubText {
	val NAV_NAMES = listOf("Go Back", "Close", "Next Page", "Previous Page")
	private val ACTION = Regex("^(?:(?:Left|Right|Shift)[- ]?)?Click(?:s)? (?:to|for) .*|^Right-click.*|^Also accessible via.*", RegexOption.IGNORE_CASE)
	val RARITY_FOOTER = Regex("^(?:a )?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL|ULTIMATE|ADMIN)\\b.*?(?: a)?$")

	fun clean(lore: List<String>) = lore.map(MenuText::clean)

	fun isEmpty(s: SlotText) = s.cleanName.isEmpty()
	fun isNav(s: SlotText) = NAV_NAMES.any { s.cleanName.equals(it, true) }
	fun navOf(slots: List<SlotText>): List<Pair<String, Int>> =
		NAV_NAMES.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { s ->
			val label = if (n == "Go Back") s.cleanLore.firstOrNull()?.takeIf { it.startsWith("To ") }?.let { "Back ($it)".replace("(To ", "(") } ?: "Back" else n
			label to s.slot } }

	fun infoLines(lore: List<String>) = clean(lore).filter { it.isNotEmpty() && !ACTION.matches(it) }

	fun kv(lore: List<String>): Map<String, String> = clean(lore).mapNotNull { l ->
		val i = l.indexOf(':'); if (i <= 0 || i == l.length - 1) null else l.substring(0, i).trim().lowercase() to l.substring(i + 1).trim()
	}.distinctBy { it.first }.toMap()

	fun block(lore: List<String>, head: String): List<String> {
		val c = clean(lore); val i = c.indexOfFirst { it.equals(head, true) }
		if (i < 0) return emptyList()
		return c.drop(i + 1).takeWhile { it.isNotEmpty() }
	}

	fun rarity(lore: List<String>) = clean(lore).lastOrNull { RARITY_FOOTER.matches(it) }?.let { RARITY_FOOTER.matchEntire(it)!!.groupValues[1] }

	fun colour(rarity: String?): String = when (rarity) {
		"COMMON" -> "§f"; "UNCOMMON" -> "§a"; "RARE" -> "§9"; "EPIC" -> "§5"; "LEGENDARY" -> "§6"; "MYTHIC" -> "§d"
		"DIVINE" -> "§b"; "SPECIAL", "VERY SPECIAL" -> "§c"; "ULTIMATE" -> "§4"; else -> "§f"
	}

	fun page(title: String): Pair<Int, Int>? = Regex("\\((\\d+)/(\\d+)\\)").find(MenuText.clean(title))?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

	fun selected(lore: List<String>) = clean(lore).firstOrNull { it.startsWith("▶") }?.removePrefix("▶")?.trim()

	fun actionLabel(s: SlotText) = selected(s.lore)?.let { "${s.cleanName}: $it" } ?: s.cleanName

	fun requirement(lore: List<String>) = clean(lore).firstOrNull { it.startsWith("Requires ", true) }
}

object HubLayout {
	const val CHIP_PAD = 8
	const val CHIP_ROW_H = 22

	fun navTop(y: Int, h: Int) = y + h - 18

	fun inventoryOrigin(x: Int, y: Int, w: Int, h: Int) = x + (w - InventoryPanel.WIDTH) / 2 to y + h - 22 - InventoryPanel.RESERVED + 2

	fun chipWidth(chip: Pair<String, String>, measure: (String) -> Int) = maxOf(measure(chip.first), measure(chip.second)) + CHIP_PAD

	fun chipRows(chips: List<Pair<String, String>>, width: Int, measure: (String) -> Int): List<List<Int>> {
		if (chips.isEmpty()) return emptyList()
		val cw = width / chips.size
		if (chips.size == 1 || chips.all { chipWidth(it, measure) <= cw }) return listOf(chips.indices.toList())
		val first = (chips.size + 1) / 2
		return listOf((0 until first).toList(), (first until chips.size).toList())
	}

	fun chipColumns(row: List<Pair<String, String>>, width: Int, measure: (String) -> Int): List<Int> {
		if (row.isEmpty()) return emptyList()
		val nat = row.map { chipWidth(it, measure) }
		val spare = width - nat.sum()
		if (spare < 0) return List(row.size) { width / row.size }
		return nat.mapIndexed { i, n -> n + spare / row.size + if (i < spare % row.size) 1 else 0 }
	}

	fun chipPanelHeight(rows: Int) = 4 + rows * CHIP_ROW_H

	fun visibleSpan(ty: Int, th: Int, topY: Int, floorY: Int) = dev.ashtonite365.apex.features.menus.support.ScrollClip.visibleSpan(ty, th, topY, floorY)

	fun visibleHeight(ty: Int, th: Int, floorY: Int, topY: Int = Int.MIN_VALUE) = visibleSpan(ty, th, topY, floorY).let { it.second - it.first }

	fun hit(mx: Int, my: Int, tx: Int, ty: Int, tw: Int, th: Int, floorY: Int, topY: Int = Int.MIN_VALUE): Boolean {
		val (a, b) = visibleSpan(ty, th, topY, floorY)
		return mx >= tx && mx < tx + tw && my >= a && my < b
	}

	fun maxScroll(contentBottom: Int, floorY: Int) = dev.ashtonite365.apex.features.menus.support.ScrollClip.maxScroll(contentBottom, floorY)

	fun contentBottom(top: Int, rowHeights: List<Int>, lastIsTileRow: Boolean, gap: Int) =
		top + rowHeights.sum() - if (lastIsTileRow && rowHeights.isNotEmpty()) gap else 0
}

abstract class HubView(screen: AbstractContainerScreen<*>, private val settings: () -> MenuSettings) : ApexViewBase<HubModel>(screen) {
	override fun palette() = settings().customisation.palette
	protected var tab = -1

	protected open fun extraButtons(m: HubModel): List<Pair<String, () -> Unit>> = emptyList()

	protected open fun onTab(m: HubModel, index: Int) { tab = index }

	private val scroller = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey("tab=$shownTab") }
	override val scrollView get() = scroller
	private var shownTab = -1

	companion object { const val SCROLL_STEP = dev.ashtonite365.apex.features.menus.support.ScrollClip.STEP }

	private var shift = false
	override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean): Boolean {
		shift = click.hasShiftDown()
		return super.mouseClicked(click, doubled)
	}

	protected open fun onPress(m: HubModel, t: HubTile) {}

	protected open fun allowPress(m: HubModel, t: HubTile): Boolean = true

	protected fun press(m: HubModel, t: HubTile, b: Int) {
		if (t.locked || !allowPress(m, t)) return
		onPress(m, t)
		val r = HubClicks.route(t, b, shift)
		val button = r.button
		if (r.kind != HubClicks.Kind.BUTTON) {

			if (!windowStillOpen()) return
			val s = screen.menu.slots.getOrNull(t.slot) ?: return
			ApexClickDelay.send(screen.menu, s, button,
				if (r.kind == HubClicks.Kind.ITEM_QUICK_MOVE) net.minecraft.world.inventory.ContainerInput.QUICK_MOVE else net.minecraft.world.inventory.ContainerInput.PICKUP)
			return
		}
		if (t.delayed) {
			if (!windowStillOpen()) return
			val s = screen.menu.slots.getOrNull(t.slot) ?: return
			MenuClicks.clickLater(screen.menu, s, button)
		} else clickSlot(t.slot, button)
	}

	protected open val clipAtNav: Boolean = false

	protected open fun showsInventory(m: HubModel) = false

	protected open fun wrapsTileText(m: HubModel) = false

	protected open fun showsPurse(m: HubModel) = false

	protected open fun tileColumns(m: HubModel): Int? = null

	protected open fun textScale(m: HubModel): Float = 1f

	protected open fun drawPurse(ctx: GuiGraphicsExtractor, m: HubModel, x: Int, y: Int, maxWidth: Int): Int =
		PurseReadout.draw(ctx, font, x, y, maxWidth)

	protected open fun wrapsAllInfo(m: HubModel) = false

	protected open fun liveOnly(m: HubModel) = false

	override fun draw(ctx: GuiGraphicsExtractor, m: HubModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		if (drawingCached && liveOnly(m)) {
			val t = "Reading menu…"
			ctx.text(font, "§7$t", x + (w - font.width(t)) / 2, y + h / 2 - 4, MenuTheme.GREY); return
		}
		ctx.text(font, MenuTheme.fit("§6§l" + m.heading, w / 2, font::width), x + pad, y + 7, MenuTheme.WHITE)
		var bx = x + w - 4
		val buttons = emptyList<Pair<String, () -> Unit>>() + extraButtons(m)
		for ((label, act) in buttons) {
			val bw = font.width(label) + 10; bx -= bw
			button(ctx, mx, my, bx, y + 3, bw, 14, label) { act() }; bx -= 4
		}
		var top = y + 22

		if (showsPurse(m)) drawPurse(ctx, m, x + pad, top, w - 2 * pad).takeIf { it > 0 }?.let { top += it + 2 }
		if (m.chips.isNotEmpty()) {
			val inner = w - 2 * pad
			val rows = HubLayout.chipRows(m.chips, inner, font::width)
			val ph = HubLayout.chipPanelHeight(rows.size)
			MenuTheme.panel(ctx, x + pad, top, inner, ph, p)
			rows.forEachIndexed { r, idx ->
				val row = idx.map { m.chips[it] }
				var cx = x + pad
				HubLayout.chipColumns(row, inner, font::width).forEachIndexed { i, cw ->
					val (l, v) = row[i]
					val ry = top + 4 + r * HubLayout.CHIP_ROW_H
					ctx.text(font, MenuTheme.fit("§7$l", cw - 8, font::width), cx + 5, ry, MenuTheme.GREY)
					ctx.text(font, MenuTheme.fit("§f$v", cw - 8, font::width), cx + 5, ry + 10, MenuTheme.WHITE)
					cx += cw
				}
			}
			top += ph + 4
		}
		val sections = if (m.tabs && m.sections.isNotEmpty()) {
			if (tab !in m.sections.indices) tab = m.selectedTab.coerceIn(m.sections.indices)
			MenuTheme.tabStrip(ctx, font, x + pad, top, m.sections.map { it.title }, tab, mx, my, p)
			MenuTheme.tabOffsets(m.sections.map { font.width(it.title) }).forEachIndexed { i, off ->
				area(x + pad + off, top, font.width(m.sections[i].title) + 8, 14) { onTab(m, i) }
			}
			top += 18
			listOf(m.sections[tab])
		} else m.sections
		val inv = showsInventory(m)
		val bottom = y + h - 22 - (if (inv) InventoryPanel.RESERVED else 0)
		val gap = 4; val tileH = 30
		val fixedCols = tileColumns(m)
		val tileW = fixedCols?.let { HubTileGrid.tileWidth(w - 2 * pad, gap, it) } ?: 104
		val cols = fixedCols ?: ((w - 2 * pad + gap) / (tileW + gap)).coerceAtLeast(1)
		val ts = textScale(m)
		val lh = HubTileGrid.lineH(ts)
		fun measure(t: String) = kotlin.math.ceil(font.width(t) * ts).toInt()
		fun sText(t: String, tx: Int, ty: Int, c: Int) {
			if (ts == 1f) { ctx.text(font, t, tx, ty, c); return }
			ctx.pose().pushMatrix(); ctx.pose().translate(tx.toFloat(), ty.toFloat()); ctx.pose().scale(ts, ts)
			ctx.text(font, t, 0, 0, c); ctx.pose().popMatrix()
		}

		data class Row(val head: String?, val tiles: List<HubTile>)
		val rows = sections.flatMap { s -> listOfNotNull(Row(s.title, emptyList()).takeIf { !m.tabs && sections.size > 1 || !m.tabs && s.title.isNotEmpty() }) + s.tiles.chunked(cols).map { Row(null, it) } }
		if (tab != shownTab) shownTab = tab
		val navClip = HubLayout.navTop(y, h) - if (clipAtNav) 0 else dev.ashtonite365.apex.features.menus.support.ScrollClip.BAR_GAP

		val clipY = if (inv) minOf(navClip, bottom) else navClip

		val wrap = wrapsTileText(m)
		val textW = tileW - 26
		fun lines(t: HubTile): Pair<List<String>, List<String>> = if (!wrap)
			listOf(MenuTheme.fit(t.name, textW, ::measure)) to listOfNotNull(t.info.firstOrNull()?.let { MenuTheme.fit("§7$it", textW, ::measure) })
		else if (wrapsAllInfo(m)) HubTileText.wrapAll(t.name, t.info, textW, ::measure)
		else HubTileText.wrap(t.name, t.info.firstOrNull(), textW, ::measure)
		fun rowTileH(r: Row) = if (wrap) r.tiles.maxOfOrNull { t -> lines(t).let { (n, i) -> HubTileText.height(n.size, i.size, lh) } }?.coerceAtLeast(tileH) ?: tileH else tileH
		val heights = rows.map { if (it.head != null) 12 else rowTileH(it) + gap }
		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + 1, top, w - 2, maxOf(0, clipY - top))
		scroller.layout(view, HubLayout.contentBottom(top, heights, rows.lastOrNull()?.head == null, gap) - top)
		var ry = top - scroller.offset
		var hover: HubTile? = null
		beginClip(ctx, view)
		rows.forEachIndexed { ri, r ->
			val rh = heights[ri]
			if (ry >= clipY) return@forEachIndexed
			if (ry + rh <= top) { ry += rh; return@forEachIndexed }
			if (r.head != null) { sText("§e§l" + r.head, x + pad, ry + 2, MenuTheme.WHITE); ry += rh; return@forEachIndexed }
			val th = rh - gap
			r.tiles.forEachIndexed { i, t ->
				val tx = x + pad + i * (tileW + gap)
				val hov = hovered(mx, my, tx, ry, tileW, th)
				MenuTheme.button(ctx, font, tx, ry, tileW, th, "", MenuTheme.state(hov, false, !t.locked), palette = p)
				stacks[t.slot]?.let { ctx.item(it, tx + 3, ry + (th - 16) / 2) }
				val (nameLines, infoLines) = lines(t)
				var ly = ry + 5
				nameLines.forEach { sText(it, tx + 22, ly, if (t.locked) MenuTheme.DIM else MenuTheme.WHITE); ly += lh }
				ly = if (wrap) ly + 3 else ry + 17
				infoLines.forEach { sText(it, tx + 22, ly, MenuTheme.GREY); ly += lh }
				if (t.highlight) ctx.fill(tx, ry, tx + 2, ry + th, 0xFF55FF55.toInt())

				if (!t.locked && t.clickable) area(tx, ry, tileW, th) { b -> press(m, t, b) }
				if (hov) hover = t
			}
			ry += rh
		}
		endClip(ctx)
		if (m.allTiles.isEmpty()) {
			val msg = if (m.locked != null) "§c${m.locked}" else "§7${m.emptyText}"
			ctx.text(font, msg, x + (w - font.width(msg)) / 2, top + 20, MenuTheme.GREY)
		}

		var nx = x + pad
		for ((label, slot) in m.nav) {
			val bw = font.width(label) + 10
			button(ctx, mx, my, nx, HubLayout.navTop(y, h), bw, 14, label) { clickSlot(slot, 0) }; nx += bw + 4
		}
		if (inv) HubLayout.inventoryOrigin(x, y, w, h).let { (ix, iy) -> inventoryPanel(ctx, mx, my, ix, iy) }
		hover?.let { t ->
			val lines = (listOf(t.name) + (t.tooltip.ifEmpty { t.info })).map { Component.literal(it) }
			ctx.setComponentTooltipForNextFrame(font, lines, mx, my)
		}
	}
}

object HubTileText {
	fun wrap(name: String, info: String?, width: Int, measure: (String) -> Int): Pair<List<String>, List<String>> =
		dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText(name, width, measure) to
			(info?.let { dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText("§7$it", width, measure) } ?: emptyList())

	fun wrapAll(name: String, info: List<String>, width: Int, measure: (String) -> Int): Pair<List<String>, List<String>> =
		dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText(name, width, measure) to
			info.flatMap { dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText("§7$it", width, measure) }

	fun height(nameLines: Int, infoLines: Int, lineH: Int = 9) = 5 + nameLines * lineH + (if (infoLines > 0) 3 + infoLines * lineH else 0) + 4
}

object HubTileGrid {

	fun tileWidth(innerW: Int, gap: Int, cols: Int) = ((innerW - (cols - 1) * gap) / cols).coerceAtLeast(30)

	fun lineH(scale: Float) = kotlin.math.ceil(9 * scale).toInt()
}
