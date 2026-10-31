// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.Client
import net.minecraft.client.gui.screens.Screen

data class CommunityTab(val slot: Int, val name: String, val selected: Boolean)

data class CommunityTile(val slot: Int, val name: String, val kind: String?, val lines: List<String>,
                         val slots: List<Int> = listOf(slot), val lore: List<String> = emptyList())
data class CommunityModel(val tabs: List<CommunityTab>, val tiles: List<CommunityTile>, val account: List<String>, val accountSlot: Int?,
                          val nav: Map<String, Int>, val selectedTab: String? = null, val upgrades: UpgradesModel? = null)

object CommunityParser {
	val TITLE = Regex("^Community Shop$", RegexOption.IGNORE_CASE)

	fun isBitsShop(tab: String?) = tab?.contains("Bits Shop", ignoreCase = true) == true

	fun filterBits(tiles: List<CommunityTile>, query: String) = dev.ashtonite365.apex.features.menus.support.MenuSearch.filter(tiles, query) { it.name }
	val NAV = listOf("Previous Page", "Go Back", "Close", "Next Page")

	private val KEY = Regex("(?i)^(cost|price|gems?\\b|bits?\\b|stock|remaining|amount|time|ends|starts|sale|duration|available|limit|" +
		"total contributions|stage|you have|click|currently|unlocked|owned|purchased|requires|sold)|\\d+(?:[.,]\\d+)*\\s*(gems|bits|coins)\\b|%!$|completed|contributed")

	fun parse(title: String, slots: List<SlotText>): CommunityModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		val tabs = slots.filter { it.slot < 9 && it.cleanName.isNotBlank() && it.cleanLore.any { l -> l.equals("Currently selected!", true) || l.startsWith("Click to view", true) } }
			.map { CommunityTab(it.slot, it.cleanName, it.cleanLore.any { l -> l.equals("Currently selected!", true) }) }
		if (tabs.isEmpty()) return null
		val selected = tabs.firstOrNull { it.selected }?.name
		val account = slots.firstOrNull { s -> s.cleanLore.any { it.startsWith("Gems:", true) } && s.cleanLore.any { it.startsWith("Bits:", true) } }

		val items = slots.filter { it.slot >= 9 && it.slot !in nav.values && it.slot != account?.slot && it.cleanName.isNotBlank() &&
			!it.cleanName.contains("Categories", true) && it.lore.isNotEmpty() && tabs.none { t -> t.slot == it.slot } }.sortedBy { it.slot }
		fun lines(l: List<String>) = l.filter { x -> x.isNotBlank() && KEY.containsMatchIn(x.trimStart('-', ' ')) }.map { it.trimStart('-', ' ') }
		val ranks = selected?.contains("Rank", true) == true
		val tiles = if (ranks) {

			items.groupBy { it.slot % 9 }.toSortedMap().values.map { col ->
				val c = col.sortedBy { it.slot }
				val lore = c.flatMapIndexed { i, s -> (if (i > 0) listOf("", "§f" + s.cleanName) else emptyList()) + s.cleanLore }
				CommunityTile(c.first().slot, c.first().cleanName, null, lines(lore), c.map { it.slot }, lore)
			}
		} else items.map { s ->
			val l = s.cleanLore
			CommunityTile(s.slot, s.cleanName, l.firstOrNull()?.takeIf { it.equals("City Project", true) }, lines(l), lore = l)
		}
		if (tiles.isEmpty()) return null
		val acc = account?.cleanLore?.filter { it.startsWith("Gems:") || it.startsWith("Bits:") || it.startsWith("Fame Rank:") } ?: emptyList()
		val upgrades = UpgradesParser.parse(title, slots, nav.values.toSet() + listOfNotNull(account?.slot), account?.slot)
		return CommunityModel(tabs, tiles, acc, account?.slot, nav, selected, upgrades)
	}
}

@Config
object CommunitySettings : MenuSettings("community", panels = listOf("account")) {
	val reset by button("reset") { resetToDefaults() }
}

object CommunitySection {
	const val ID = "community-shop"
	const val DISPLAY_NAME = "Community Shop"
}

object CommunityMenu : CustomMenu {
	override val id = "community"
	override val displayName = "Community Shop"
	override val title = CommunityParser.TITLE
	override val settings get() = CommunitySettings

	const val DEFAULT_HEIGHT = 340
	override val defaultSize = 480 to DEFAULT_HEIGHT

	fun vanillaX(x: Int, y: Int, w: Int, vw: Int, s: dev.ashtonite365.apex.features.menus.style.StyleState): Int {
		val (right, by) = dev.ashtonite365.apex.features.menus.MenuChrome.barAnchor(x, y, w)
		val inside = by >= y
		return if (inside) dev.ashtonite365.apex.features.menus.style.StyleBar.geometry(right, by, s).dropX - 4 - vw else x + w - vw - 4
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = CommunityView(screen)

	val bitsSearch = dev.ashtonite365.apex.features.menus.support.MenuSearch()
}

class CommunityView(screen: AbstractContainerScreen<*>) : ApexViewBase<CommunityModel>(screen) {
	override fun palette() = CommunitySettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = CommunityParser.parse(title, slots)

	private var scroll by dev.ashtonite365.apex.util.ScrollMemory.int(settle = true) { scrollKey() }
	private val search get() = CommunityMenu.bitsSearch
	init { search.focused = false }
	override val textFocused get() = search.focused
	override fun keyPressed(input: net.minecraft.client.input.KeyEvent): Boolean = search.keyPressed(input) || super.keyPressed(input)
	override fun charTyped(input: net.minecraft.client.input.CharacterEvent): Boolean { search.charTyped(input); return true }
	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean { scroll = maxOf(0, scroll - (amount * 16).toInt()); return true }

	override fun draw(ctx: GuiGraphicsExtractor, m: CommunityModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lCommunity Shop", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val sel = m.tabs.indexOfFirst { it.selected }
		val (_, tabsH) = MenuTheme.tabRows(null, font, x + 6, y + 22, w - 12, m.tabs.map { it.name }, sel, mx, my, palette())
		run {
			val placed = dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapTabs(m.tabs.map { font.width(it.name) + 8 }, w - 12)
			placed.forEachIndexed { i, t -> val tb = m.tabs[i]
				button(ctx, mx, my, x + 6 + t.x, y + 22 + t.row * 16, t.width, 14, tb.name, active = tb.selected) { b -> if (!tb.selected) clickSlot(tb.slot, b) } }
		}
		m.upgrades?.let { drawUpgrades(ctx, m, it, mx, my, y + 26 + tabsH); return }
		val acc = CommunitySettings.shows("account") && m.account.isNotEmpty()
		val lw = if (acc) w - 130 else w - 12
		val headY = y + 26 + tabsH
		ctx.text(font, "§6" + (m.selectedTab ?: "Community Shop"), x + 6, headY, MenuTheme.GOLD)
		var top = headY + 12; val bottom = y + h - 24
		val bits = CommunityParser.isBitsShop(m.selectedTab)
		if (bits) top += searchBox(ctx, mx, my, search, x + 6, top, minOf(160, lw - 22), "Search Bits Shop…") + 4
		else search.focused = false
		val tiles = if (bits) CommunityParser.filterBits(m.tiles, search.query) else m.tiles
		if (bits) {
			val (cards, total) = CommunityCompact.bits(tiles, lw, font::width)
			scroll = scroll.coerceAtMost(maxOf(0, total - (bottom - top)))
			if (tiles.isEmpty()) ctx.text(font, "§7No Bits Shop items match \"${search.query}\"", x + 6, top + 4, MenuTheme.GREY)
			var tipB: CommunityTile? = null
			ctx.enableScissor(x + 2, top, x + 6 + lw + 1, bottom)
			for (c in cards) {
				val cx = x + 6 + c.x; val cy = top - scroll + c.y
				if (cy + c.h < top || cy > bottom) continue
				val inView = my in top until bottom
				button(ctx, if (inView) mx else -1, my, cx, cy, c.w, c.h, "") { b -> if (inView) clickSlot(c.tile.slot, b) }
				stacks[c.tile.slot]?.let { ctx.item(it, cx + CommunityCompact.PAD, cy + (c.h - 16) / 2) }
				val tx = cx + CommunityCompact.PAD + CommunityCompact.ICON + 3
				ctx.text(font, c.name, tx, cy + CommunityCompact.PAD + 1, MenuTheme.WHITE)
				ctx.text(font, c.cost, tx, cy + CommunityCompact.PAD + 1 + CommunityCompact.LINE, MenuTheme.GREY)
				if (inView && MenuTheme.inside(mx, my, cx, cy, c.w, c.h)) tipB = c.tile
			}
			ctx.disableScissor()
			if (total > bottom - top) MenuTheme.scrollbar(ctx, x + 6 + lw - 3, top, bottom - top, bottom - top, total, scroll)
			drawAccount(ctx, m, mx, my, headY)
			drawNav(ctx, m, mx, my)
			tipB?.let { tt -> stacks[tt.slot]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) } }
			return
		}

		val cols = maxOf(1, lw / 150); val gap = 4; val cw = (lw - gap * (cols - 1)) / cols
		val cards = tiles.map { t ->
			val name = CityText.wrap("§f${t.name}" + (t.kind?.let { " §7($it)" } ?: ""), cw - 25, font::width)
			val body = t.lines.flatMap { CityText.wrap("§7$it", cw - 8, font::width) }
			Triple(t, name, body)
		}
		val rows = cards.chunked(cols)
		val heights = rows.map { r -> r.maxOf { (_, n, b) -> 6 + maxOf(14, n.size * 10) + b.size * 10 + 2 } }
		scroll = scroll.coerceAtMost(maxOf(0, heights.sum() + gap * (rows.size - 1) - (bottom - top)))
		if (bits && tiles.isEmpty()) ctx.text(font, "§7No Bits Shop items match \"${search.query}\"", x + 6, top + 4, MenuTheme.GREY)
		var tip: CommunityTile? = null
		ctx.enableScissor(x + 2, top, x + 6 + lw + 1, bottom)
		var ry = top - scroll
		for ((ri, r) in rows.withIndex()) {
			val rh = heights[ri]
			for ((ci, c) in r.withIndex()) {
				val (t, name, body) = c
				val cx = x + 6 + ci * (cw + gap)
				if (ry + rh >= top && ry <= bottom) {
					val inView = my in top until bottom
					button(ctx, if (inView) mx else -1, my, cx, ry, cw, rh, "") { b -> if (inView) clickSlot(t.slot, b) }
					stacks[t.slot]?.let { ctx.item(it, cx + 3, ry + 3) }
					name.forEachIndexed { k, l -> ctx.text(font, l, cx + 22, ry + 5 + k * 10, MenuTheme.WHITE) }
					var ly = ry + 6 + maxOf(14, name.size * 10)
					body.forEach { ctx.text(font, it, cx + 4, ly, MenuTheme.GREY); ly += 10 }
					if (inView && MenuTheme.inside(mx, my, cx, ry, cw, rh)) tip = t
				}
			}
			ry += rh + gap
		}
		ctx.disableScissor()
		drawAccount(ctx, m, mx, my, headY)
		drawNav(ctx, m, mx, my)
		tip?.let { t ->
			if (t.slots.size == 1) stacks[t.slot]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) }
			else ctx.setComponentTooltipForNextFrame(font, (listOf("§f" + t.name) + t.lore).map { net.minecraft.network.chat.Component.literal(it) }, mx, my)
		}
	}

	private fun drawAccount(ctx: GuiGraphicsExtractor, m: CommunityModel, mx: Int, my: Int, headY: Int) {
		if (!(CommunitySettings.shows("account") && m.account.isNotEmpty())) return
		val ax = x + w - 118
		MenuTheme.frame(ctx, ax, headY, x + w - 6, headY + 16 + m.account.size * 10, MenuTheme.FILL)
		ctx.text(font, "§6Account", ax + 4, headY + 4, MenuTheme.GOLD)
		m.account.forEachIndexed { k, l -> CityText.wrap("§f$l", 104, font::width).firstOrNull()?.let { ctx.text(font, it, ax + 4, headY + 16 + k * 10, MenuTheme.WHITE) } }
		if (m.accountSlot != null && MenuTheme.inside(mx, my, ax, headY, 112, 16 + m.account.size * 10))
			stacks[m.accountSlot]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) }
	}

	private fun drawNav(ctx: GuiGraphicsExtractor, m: CommunityModel, mx: Int, my: Int) {
		var bx = x + 6
		for ((n, s) in m.nav) { val bw = font.width(n) + 14; button(ctx, mx, my, bx, y + h - 20, bw, 14, n) { b -> clickSlot(s, b) }; bx += bw + 4 }
	}

	private fun drawUpgrades(ctx: GuiGraphicsExtractor, m: CommunityModel, u: UpgradesModel, mx: Int, my: Int, headY: Int) {
		val iw = w - 12; val ix = x + 6
		ctx.text(font, "§6" + (m.selectedTab ?: "Account & Profile Upgrades"), ix, headY, MenuTheme.GOLD)
		var ty = headY + 11
		for (l in u.status) for (wl in CityText.wrap("§f$l", iw, font::width)) { ctx.text(font, wl, ix, ty, MenuTheme.WHITE); ty += 10 }
		u.divider?.let { d -> for (wl in CityText.wrap("§8$d", iw, font::width)) { ctx.text(font, wl, ix, ty, MenuTheme.DIM); ty += 10 } }

		val navW = m.nav.keys.sumOf { font.width(it) + 18 }

		val accText = if (CommunitySettings.shows("account") && u.account.isNotEmpty()) u.account.joinToString(" §8·§f ") else null
		val accLines = accText?.let { CityText.wrap("§f$it", maxOf(40, iw - navW - 8), font::width) } ?: emptyList()
		val footH = maxOf(20, accLines.size * 10 + 8)
		val top = ty + 2; val bottom = y + h - footH - 2
		val lay = CommunityCompact.upgrades(u, iw - 6, font::width)
		scroll = scroll.coerceAtMost(maxOf(0, lay.height - (bottom - top)))
		val view = dev.ashtonite365.apex.features.menus.MenuBounds(ix, top, iw, bottom - top)
		var tip: Int? = null
		beginClip(ctx, view)
		val p = palette()
		for (c in lay.cards) {
			val cx = ix + c.x; val cy = top - scroll + c.y
			if (cy > bottom || cy + c.h < top) continue
			val hover = hovered(mx, my, cx, cy, c.w, c.h)
			if (c.section) {
				ctx.text(font, c.name, cx + CommunityCompact.PAD, cy + 2, MenuTheme.GOLD)
				ctx.fill(cx, cy + c.h - 1, cx + c.w, cy + c.h, p.border)
				if (hover) tip = c.cell.slot
				continue
			}
			val border = when (c.cell.state) {
				UpgradeState.READY_TO_CLAIM -> MenuTheme.GOLD
				UpgradeState.AVAILABLE -> 0xFF55FF55.toInt()
				UpgradeState.IN_PROGRESS -> 0xFFFFFF55.toInt()
				UpgradeState.CANNOT_AFFORD, UpgradeState.BLOCKED -> 0xFF884444.toInt()
				UpgradeState.MAXED -> 0xFF2E7D32.toInt()
				null -> p.border
			}
			MenuTheme.frame(ctx, cx, cy, cx + c.w, cy + c.h, if (hover) p.fillHover else p.fill, if (hover) p.borderHover else border)
			stacks[c.cell.slot]?.let { ctx.item(it, cx + CommunityCompact.PAD, cy + CommunityCompact.PAD) }
			val tx = cx + c.textX
			ctx.text(font, c.name, tx, cy + CommunityCompact.PAD + 1, MenuTheme.WHITE)
			c.lines.forEachIndexed { k, l -> ctx.text(font, l, tx, cy + CommunityCompact.PAD + 1 + (k + 1) * CommunityCompact.LINE, MenuTheme.GREY) }
			area(cx, cy, c.w, c.h) { b -> clickSlot(c.cell.slot, b) }
			if (hover) tip = c.cell.slot
		}
		endClip(ctx)
		if (lay.height > bottom - top) MenuTheme.scrollbar(ctx, ix + iw - 3, top, bottom - top, bottom - top, lay.height, scroll)
		val fy = y + h - footH + 2
		var bx = ix
		for ((n, s) in m.nav) { val bw = font.width(n) + 14; button(ctx, mx, my, bx, fy + (footH - 18) / 2, bw, 14, n) { b -> clickSlot(s, b) }; bx += bw + 4 }
		if (accLines.isNotEmpty()) {
			val aw = accLines.maxOf { font.width(it) } + 8; val ah = accLines.size * 10 + 4
			val ax = maxOf(bx + 4, x + (w - aw) / 2)
			MenuTheme.frame(ctx, ax, fy - 2, ax + aw, fy - 2 + ah, MenuTheme.FILL)
			accLines.forEachIndexed { k, l -> ctx.text(font, l, ax + 4, fy + k * 10, MenuTheme.WHITE) }
			m.accountSlot?.let { s -> area(ax, fy - 2, aw, ah) { b -> clickSlot(s, b) }; if (MenuTheme.inside(mx, my, ax, fy - 2, aw, ah)) tip = s }
		}
		tip?.let { s -> stacks[s]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) } }
	}
}
