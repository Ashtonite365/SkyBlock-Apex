// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.ah

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.features.auction.AhSoldPrice
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSession
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuSlots
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

@Config
object AhMenuSettings : MenuSettings("ah", panels = listOf("sidebar", "chips", "popover", "pager", "suggestions", "inventory")) {

	val gridColumns by integer("grid-columns", 0, 8) { 0 }

	val cardWidth by integer("card-width", 56, 140) { 84 }
}

object AhMenu : CustomMenu {
	override val id = "ah"
	override val displayName = "Auction House"
	override val title = AhModel.ANY
	override val settings get() = AhMenuSettings
	override val defaultSize = 480 to 280

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	val cache = AhStateCache<Map<Int, ItemStack>>()

	internal var lastSeenMs = 0L

	override fun create(screen: AbstractContainerScreen<*>): MenuView {
		if (System.currentTimeMillis() - lastSeenMs > 3000) cache.clear()
		return AhView(screen)
	}
}

class AhView(private val screen: AbstractContainerScreen<*>) : MenuView {
	override var x = 0; override var y = 0; override var w = 0; override var h = 0
	override fun layout(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }

	private val opened = System.currentTimeMillis()
	private var session: MenuSession? = null
	override fun attached(session: MenuSession) { this.session = session }
	private var page: AhPage? = null
	private var stacks: Map<Int, ItemStack> = emptyMap()
	private var signature = ""
	private var sidebarOpen = true

	private var loading = false
	private val keyGate = dev.ashtonite365.apex.features.menus.support.KeyRepeatGate()
	private var gridScroll by dev.ashtonite365.apex.util.ScrollMemory.int(settle = true) { "ah:grid|$gridKey" }
	private var gridKey = ""
	private var gridCanScroll = intArrayOf(0, 0)
	private var gridRect: IntArray? = null

	private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val slot: Int?, val action: (() -> Unit)?)
	private val hits = mutableListOf<Hit>()

	private val font get() = Client.font
	private val c get() = AhMenuSettings.customisation
	private val pal get() = c.palette

	private fun slotsNow(): List<Pair<AhSlot, ItemStack>> = screen.menu.slots.filter { it.container !is Inventory }.map { s ->
		val st = s.item
		val name = if (st.isEmpty) "" else MenuText.clean(st.hoverName.bareText)
		val lore = if (st.isEmpty) emptyList() else st.view().loreAccordingToNbt.map { it.bareText }
		val filler = !st.isEmpty && BuiltInRegistries.ITEM.getKey(st.item).path.endsWith("stained_glass_pane") && name.isBlank() && lore.all { it.isBlank() }
		AhSlot(s.index, name, lore, !st.isEmpty, filler) to st
	}

	private fun refresh() {
		val now = slotsNow()
		val sig = MenuSlots.signature(screen).toString()

		AhMenu.lastSeenMs = System.currentTimeMillis()
		if (sig == signature && page != null && !loading) return
		signature = sig
		val t = System.currentTimeMillis()
		AhMenu.lastSeenMs = t
		val shown = AhMenu.cache.update(AhModel.parse(screen.title.bareText, now.map { it.first }),
			now.associate { it.first.index to it.second.copy() }, t, AhModel.kindOf(screen.title.bareText),
			MenuText.clean(screen.title.bareText))
		page = shown.page; stacks = shown.extra ?: emptyMap(); loading = shown.loading

		shown.page?.takeIf { it.kind == AhKind.BROWSER && !shown.loading }?.let { p ->
			dev.ashtonite365.apex.features.museum.AhSeenPrices.record(p.cards.map { c -> stacks[c.slot]?.view()?.repoItemId?.skyblockId to c.listing }, t)
		}
		if (page == null && !AhMenu.cache.covering(t) && t - opened > ContainerGate.GRACE_MS) session?.fallback("auction page not recognised")
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		keyGate.frame { InputConstants.isKeyDown(it) }
		refresh()
		hits.clear(); gridCells.clear(); gridMouse = null
		MenuTheme.panel(ctx, x, y, w, h, pal)
		val p = page
		if (p == null) { ctx.centeredText(font, "§7Loading auctions…", x + w / 2, y + h / 2 - 4, MenuTheme.GREY); return }
		if (loading) { ctx.fill(x + 1, y + 1, x + w - 1, y + 2, pal.accent); ctx.text(font, "§7Loading…", x + w - 6 - font.width("Loading…"), y + h - 12, pal.textDim) }
		when (p.kind) {
			AhKind.BROWSER -> renderBrowser(ctx, p, mouseX, mouseY)
			AhKind.MANAGE -> renderKanban(ctx, p, mouseX, mouseY)
			AhKind.VIEW, AhKind.CONFIRM, AhKind.CREATE -> renderWizard(ctx, p, mouseX, mouseY)
		}
	}

	private fun btn(ctx: GuiGraphicsExtractor, mx: Int, my: Int, bx: Int, by: Int, bw: Int, bh: Int, label: String, slot: Int?, active: Boolean = false, enabled: Boolean = true, action: (() -> Unit)? = null) {
		MenuTheme.button(ctx, font, bx, by, bw, bh, MenuTheme.fit(label, bw - 6, font::width),
			MenuTheme.state(MenuTheme.inside(mx, my, bx, by, bw, bh), active, enabled), palette = pal)
		if (enabled) hits += Hit(bx, by, bw, bh, slot, action)
		if (slot != null) countBadge(ctx, slot, bx, by, bw)
	}

	private fun countBadge(ctx: GuiGraphicsExtractor, slot: Int, bx: Int, by: Int, bw: Int) {
		val p = page ?: return
		val st = stacks[slot]
		val name = st?.takeUnless { it.isEmpty }?.hoverName?.bareText ?: return
		val lore = if (loading) null else st.view().loreAccordingToNbt.map { it.bareText }
		val n = AhTileCounts.countFor(AhTileCounts.key(p.kind.name, name), lore) ?: return
		val tw = font.width(n) + 4
		ctx.nextStratum()
		ctx.fill(bx + bw - tw - 1, by - 3, bx + bw + 1, by + 7, pal.accent)
		ctx.fill(bx + bw - tw, by - 2, bx + bw, by + 6, pal.fill)
		ctx.text(font, n, bx + bw - tw + 2, by - 2, pal.accent)
	}

	private fun renderBrowser(ctx: GuiGraphicsExtractor, p: AhPage, mx: Int, my: Int) {
		val top = y + 6

		val query = AhModel.searchTerm(p)
		val chipW = 64
		val chips = if (c.shows("chips")) listOfNotNull(p.button(AhControl.SORT), p.button(AhControl.RARITY), p.button(AhControl.BIN_FILTER)) else emptyList()
		val searchW = w - 12 - 22 - chips.size * (chipW + 4) - (if (p.button(AhControl.RESET) != null && c.shows("chips")) 44 else 0)
		btn(ctx, mx, my, x + 6, top, 18, 16, "≡", null, active = sidebarOpen) { sidebarOpen = !sidebarOpen }
		val search = p.button(AhControl.SEARCH)
		val sx = x + 28
		ctx.fill(sx, top, sx + searchW, top + 16, pal.border)
		ctx.fill(sx + 1, top + 1, sx + searchW - 1, top + 15, if (MenuTheme.inside(mx, my, sx, top, searchW, 16)) pal.fillHover else pal.fill)
		ctx.text(font, MenuTheme.fit(if (query.isNullOrEmpty()) "§7🔍 Search auctions…" else "§f🔍 $query", searchW - 8, font::width), sx + 5, top + 4, pal.text)
		if (search != null) hits += Hit(sx, top, searchW, 16, search.slot, null)
		var cx = sx + searchW + 4
		for (b in chips) {
			val label = (b.selection ?: b.name).let { if (b.control == AhControl.BIN_FILTER) "BIN: $it" else it }
			btn(ctx, mx, my, cx, top, chipW, 16, label, b.slot, active = b.selection != null); cx += chipW + 4
		}
		if (c.shows("chips")) p.button(AhControl.RESET)?.let { btn(ctx, mx, my, cx, top, 40, 16, "Reset", it.slot) }

		val bodyY = top + 22
		val pagerH = if (c.shows("pager")) 22 else 0
		val bodyH = h - (bodyY - y) - pagerH - 6
		var gx = x + 6
		if (sidebarOpen && c.shows("sidebar") && p.categories.isNotEmpty()) {
			val sw = 96
			MenuTheme.panel(ctx, gx, bodyY, sw, bodyH, pal)
			var cy = bodyY + 4
			for (cat in p.categories) {
				if (cy + 18 > bodyY + bodyH) break
				btn(ctx, mx, my, gx + 3, cy, sw - 6, 18, "    " + cat.name, cat.slot, active = cat.selected)
				stacks[cat.slot]?.let { ctx.item(it, gx + 5, cy + 1) }
				cy += 20
			}
			gx += sw + 6
		}

		val gw = x + w - 6 - gx
		val cols = AhModel.columns(gw, c.scalePct.let { AhMenuSettings.cardWidth }, AhMenuSettings.gridColumns)
		val cardW = (gw - (cols - 1) * 4) / cols
		val cardH = 40
		var hovered: AhCard? = null
		dev.ashtonite365.apex.features.auction.AhHoverLink.gridSlot = null
		val linkedSlot = dev.ashtonite365.apex.features.auction.AhHoverLink.panelSlot
		if (p.cards.isEmpty()) ctx.centeredText(font, "§7No auctions on this page", gx + gw / 2, bodyY + bodyH / 2, MenuTheme.GREY)

		val fit = AhGrid.rowsFit(bodyH, cardH)
		val key = p.title + "|" + p.page + "|" + p.cards.joinToString(",") { it.slot.toString() }
		if (key != gridKey) gridKey = key
		gridScroll = AhGrid.clamp(gridScroll, p.cards.size, cols, fit)
		val shownIdx = AhGrid.visible(p.cards.size, cols, fit, gridScroll)
		val below = AhGrid.hiddenBelow(p.cards.size, cols, fit, gridScroll)
		gridCanScroll = intArrayOf(gridScroll, AhGrid.maxScroll(p.cards.size, cols, fit) - gridScroll)
		gridRect = intArrayOf(gx, bodyY, gw, bodyH)
		p.cards.forEachIndexed { i, card ->
			if (i !in shownIdx) return@forEachIndexed
			val j = i - shownIdx.first
			val cx0 = gx + (j % cols) * (cardW + 4)
			val cy0 = bodyY + (j / cols) * (cardH + 4)
			val hov = MenuTheme.inside(mx, my, cx0, cy0, cardW, cardH)
			if (hov) { hovered = card; dev.ashtonite365.apex.features.auction.AhHoverLink.gridSlot = card.slot }
			val lit = dev.ashtonite365.apex.features.auction.AhHoverLink.cardHighlighted(card.slot, hov, linkedSlot)
			val l = card.listing
			ctx.fill(cx0, cy0, cx0 + cardW, cy0 + cardH, if (lit) pal.borderHover else pal.border)
			ctx.fill(cx0 + 1, cy0 + 1, cx0 + cardW - 1, cy0 + cardH - 1, if (lit) pal.fillHover else pal.fill)
			stacks[card.slot]?.let { ctx.item(it, cx0 + 4, cy0 + 4) }
			ctx.text(font, MenuTheme.fit(card.name, cardW - 26, font::width), cx0 + 23, cy0 + 4, pal.text)
			ctx.text(font, (if (l?.isBin == true) "§bBIN " else "§eBid ") + "§6" + AhModel.shortCoins(l?.price), cx0 + 23, cy0 + 15, pal.text)
			val ends = l?.endsIn?.let { dev.ashtonite365.apex.util.ApexFormatters.timespanText(it) } ?: ""
			val bids = if (l != null && !l.isBin) " §7· ${l.bids} bids" else ""
			ctx.text(font, MenuTheme.fit("§7$ends$bids", cardW - 8, font::width), cx0 + 4, cy0 + 28, pal.textDim)

			if (dev.ashtonite365.apex.features.museum.MuseumAhTarget.blocked(stacks[card.slot]?.view()?.repoItemId?.skyblockId))
				ctx.fill(cx0, cy0, cx0 + cardW, cy0 + cardH, 0xB0000000.toInt())
			else hits += Hit(cx0, cy0, cardW, cardH, card.slot, null)
		}
		if (below > 0 || gridScroll > 0) {
			val hint = (if (gridScroll > 0) "▲ " else "") + (if (below > 0) "▼ $below more on this page (scroll)" else "")
			ctx.text(font, "§e" + hint, gx + gw - 4 - font.width(hint), bodyY + bodyH - 9, pal.text)
		}

		if (c.shows("pager")) {
			val py = y + h - 6 - 18
			val prev = p.button(AhControl.PREV); val next = p.button(AhControl.NEXT)
			btn(ctx, mx, my, x + w / 2 - 110, py, 70, 18, "◀ Prev", prev?.slot, enabled = prev != null)
			ctx.centeredText(font, if (p.page != null) "§fPage ${p.page}/${p.pages}" else "§7${p.cards.size} auctions", x + w / 2, py + 5, pal.text)
			btn(ctx, mx, my, x + w / 2 + 40, py, 70, 18, "Next ▶", next?.slot, enabled = next != null)
			p.button(AhControl.BACK)?.let { btn(ctx, mx, my, x + 6, py, 50, 18, "Back", it.slot) }
			p.button(AhControl.CLOSE)?.let { btn(ctx, mx, my, x + w - 56, py, 50, 18, "Close", it.slot) }
		}

		val hc = hovered
		if (hc != null && c.shows("popover")) popover(ctx, hc, mx, my)
	}

	private fun popover(ctx: GuiGraphicsExtractor, card: AhCard, mx: Int, my: Int) {
		val l = card.listing
		val lines = buildList {
			add("§f${card.name}")
			l?.seller?.let { add("§7Seller: §f$it") }
			val sold = AhModel.soldLine(card.name, l)
			if (sold != null) {
				add("§7Sold for: §6" + sold.first)
				l?.buyer?.let { add("§7Buyer: §f$it") }
				sold.second?.let { add("§7Collected (after tax): §6$it") }
			} else
			add((if (l?.isBin == true) "§7Buy it now: §6" else "§7Price: §6") + (l?.price?.let { dev.ashtonite365.apex.util.ApexFormatters.withThousands(it, 0) } ?: "?") + " coins")
			if (l != null && !l.isBin) add("§7Bids: §f${l.bids}")
			l?.endsIn?.let { add("§7Ends in: §f" + dev.ashtonite365.apex.util.ApexFormatters.timespanText(it)) }
			stacks[card.slot]?.view()?.repoItemId?.let { HypixelStaticData.lowestBin[it] }?.let { add("§7Lowest BIN: §6" + AhModel.shortCoins(it)) }
			add("§8Click to open")
		}
		val pw = lines.maxOf { font.width(it) } + 10
		val ph = lines.size * 10 + 6
		var px = mx + 12; var py = my + 4
		if (px + pw > x + w) px = mx - pw - 6
		if (py + ph > y + h) py = y + h - ph
		ctx.nextStratum()
		MenuTheme.panel(ctx, px, py, pw, ph, pal)
		ctx.fill(px, py, px + pw, py + 1, pal.accent)
		lines.forEachIndexed { i, s -> ctx.text(font, s, px + 5, py + 4 + i * 10, pal.text) }
	}

	private fun renderWizard(ctx: GuiGraphicsExtractor, p: AhPage, mx: Int, my: Int) {
		val showInv = p.kind == AhKind.CREATE && AhMenuSettings.customisation.shows("inventory")
		val cw = if (showInv) minOf(w - 36 - 9 * 18, 300) else minOf(w - 24, 300)
		val cx = if (showInv) x + 12 else x + (w - cw) / 2
		if (showInv) inventoryGrid(ctx, x + w - 12 - 9 * 18, y + 24, mx, my)
		var cy = y + 10
		val type = AhModel.auctionType(p)?.let { if (it == AhModel.AuctionType.BIN) "  §b[BIN]" else "  §e[Auction]" } ?: ""

		ctx.centeredText(font, MenuTheme.fit("§6" + p.title + type, cw, font::width), cx + cw / 2, cy, pal.text); cy += 14

		val hero = p.hero
		MenuTheme.panel(ctx, cx, cy, cw, 70, pal)
		if (hero != null) {
			ctx.fill(cx + 8, cy + 8, cx + 34, cy + 34, pal.accent); ctx.fill(cx + 9, cy + 9, cx + 33, cy + 33, pal.fill)
			stacks[hero.index]?.let { ctx.item(it, cx + 13, cy + 13) }
			ctx.text(font, MenuTheme.fit(hero.name, cw - 50, font::width), cx + 42, cy + 10, pal.text)
			hero.lore.filter { it.isNotBlank() }.takeLast(4).forEachIndexed { i, s -> ctx.text(font, MenuTheme.fit("§7$s", cw - 50, font::width), cx + 42, cy + 22 + i * 10, pal.textDim) }
			hits += Hit(cx + 8, cy + 8, 26, 26, hero.index, null)
		} else {
			val hint = AhMenuText.createHintLines(cw - 16, font::width)
			val hy = cy + (70 - hint.size * 10) / 2
			hint.forEachIndexed { i, l -> ctx.text(font, l, cx + 8, hy + i * 10, pal.textDim) }
		}
		cy += 76

		AhModel.soldOnView(p)?.let { s ->
			val got = AhSoldPrice.tracker.find(s.item, s.buyer)?.net
			val txt = "§aSold for §6" + dev.ashtonite365.apex.util.ApexFormatters.withThousands(s.gross?.toDouble() ?: 0.0, 0) + " coins" +
				(s.buyer?.let { " §7to §f$it" } ?: "") + (got?.let { " §7· collected §6" + dev.ashtonite365.apex.util.ApexFormatters.withThousands(it.toDouble(), 0) } ?: "")
			ctx.text(font, MenuTheme.fit(txt, cw - 4, font::width), cx + 2, cy, pal.text); cy += 12
		}

		p.button(AhControl.PRICE)?.let { b ->
			val current = AhModel.priceLabel(b)
			btn(ctx, mx, my, cx, cy, cw, 20, "§fPrice: §6$current  §7(click to set)", b.slot); cy += 24
			val id = hero?.let { stacks[it.index] }?.view()?.repoItemId
			if (c.shows("suggestions") && id != null) {
				val lb = HypixelStaticData.lowestBin[id]; val avg = HypixelStaticData.avg1dlowestBin[id]
				ctx.text(font, MenuTheme.fit("§7Lowest BIN: §6${AhModel.shortCoins(lb)}   §7Avg (1d): §6${AhModel.shortCoins(avg)}", cw - 4, font::width), cx + 2, cy, pal.textDim); cy += 12
			}
		}
		p.button(AhControl.DURATION)?.let { b ->
			val current = b.lore.firstOrNull { Regex("\\d+\\s*(?:h|d|hours?|days?)", RegexOption.IGNORE_CASE).containsMatchIn(it) }?.trim() ?: b.name
			btn(ctx, mx, my, cx, cy, cw / 2, 18, "⏱ $current", b.slot, active = true); cy += 22
		}

		val primary = listOf(AhControl.CREATE, AhControl.BUY, AhControl.BID, AhControl.CONFIRM, AhControl.COLLECT).mapNotNull { p.button(it) }
		val secondary = listOf(AhControl.SWITCH_TYPE, AhControl.CANCEL_AUCTION, AhControl.CANCEL, AhControl.BACK, AhControl.CLOSE).mapNotNull { p.button(it) }
		val by = y + h - 30 - (if (secondary.isNotEmpty()) 22 else 0)
		if (primary.isNotEmpty()) {
			val bw = (cw - (primary.size - 1) * 6) / primary.size

			val other = (p.kind == AhKind.VIEW || p.kind == AhKind.CONFIRM) &&
				dev.ashtonite365.apex.features.museum.MuseumAhTarget.blocked(hero?.let { stacks[it.index] }?.view()?.repoItemId?.skyblockId)
			primary.forEachIndexed { i, b ->
				val buyish = b.control == AhControl.BUY || b.control == AhControl.BID || b.control == AhControl.CONFIRM
				btn(ctx, mx, my, cx + i * (bw + 6), by, bw, 24, if (other && buyish) "§8Other item" else "§l" + b.name, b.slot, active = !(other && buyish), enabled = !(other && buyish))
			}
		}
		if (secondary.isNotEmpty()) {
			val bw = (cw - (secondary.size - 1) * 6) / secondary.size
			secondary.forEachIndexed { i, b -> btn(ctx, mx, my, cx + i * (bw + 6), y + h - 26, bw, 18, b.name, b.slot) }
		}
	}

	private class GridCell(val x: Int, val y: Int, val slot: net.minecraft.world.inventory.Slot) {
		fun contains(mx: Double, my: Double) = mx >= x && mx < x + 18 && my >= y && my < y + 18
	}
	private val gridCells = mutableListOf<GridCell>()
	private var gridMouse: Pair<Double, Double>? = null
	override fun hoveredRealSlot(): net.minecraft.world.inventory.Slot? {
		val (mx, my) = gridMouse ?: return null
		return gridCells.lastOrNull { it.contains(mx, my) }?.slot
	}

	private fun inventoryGrid(ctx: GuiGraphicsExtractor, gx: Int, gy: Int, mx: Int, my: Int) {
		val inv = screen.menu.slots.filter { it.container is Inventory }
		val ids = inv.map { it.index }
		ctx.text(font, AhMenuText.inventoryHeader(9 * 18, font::width), gx, gy - 11, pal.textDim)
		var tip: ItemStack? = null
		for (i in ids.indices) {
			val sx = gx + (i % 9) * 18
			val sy = gy + (i / 9) * 18 + if (i >= 27) 4 else 0
			val stack = inv[i].item
			val hov = MenuTheme.inside(mx, my, sx, sy, 18, 18)

			session?.standInCells?.put(inv[i].index, sx + 1 to sy + 1)
			gridCells += GridCell(sx, sy, inv[i])
			ctx.fill(sx, sy, sx + 18, sy + 18, if (hov) pal.borderHover else pal.border)
			ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, if (hov) pal.fillHover else pal.fill)
			if (!stack.isEmpty) {
				ctx.item(stack, sx + 1, sy + 1)
				ctx.itemDecorations(font, stack, sx + 1, sy + 1)
				if (hov) tip = stack
				AhModel.gridToSlotId(i, ids)?.let { id -> hits += Hit(sx, sy, 18, 18, null) { forward(id, 0) } }
			}
		}
		gridMouse = mx.toDouble() to my.toDouble()
		tip?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
	}

	private fun renderKanban(ctx: GuiGraphicsExtractor, p: AhPage, mx: Int, my: Int) {
		var top = y + 8
		ctx.text(font, "§6" + p.title, x + 8, top + 4, pal.text)
		p.button(AhControl.CLAIM_ALL)?.let { btn(ctx, mx, my, x + w - 96, top, 88, 18, "Claim All", it.slot, active = true) }
		p.button(AhControl.CREATE)?.let { btn(ctx, mx, my, x + w - 190, top, 88, 18, "Create auction", it.slot) }
		top += 24
		val bottom = y + h - 30
		val lanes = AhLane.entries
		val colW = (w - 16 - (lanes.size - 1) * 6) / lanes.size
		var hovered: AhCard? = null
		dev.ashtonite365.apex.features.auction.AhHoverLink.gridSlot = null
		val linkedSlot = dev.ashtonite365.apex.features.auction.AhHoverLink.panelSlot
		lanes.forEachIndexed { li, lane ->
			val lx = x + 8 + li * (colW + 6)
			MenuTheme.panel(ctx, lx, top, colW, bottom - top, pal)
			val cards = p.cards.filter { it.lane == lane }
			val head = when (lane) { AhLane.ACTIVE -> "§aActive"; AhLane.ENDED -> "§eEnded · claim"; AhLane.OUTBID -> "§cOutbid" } + " §7(${cards.size})"
			ctx.text(font, head, lx + 5, top + 5, pal.text)
			var cy = top + 18
			for (card in cards) {
				if (cy + 26 > bottom) { ctx.text(font, "§7…", lx + 5, cy, pal.textDim); break }
				val hov = MenuTheme.inside(mx, my, lx + 3, cy, colW - 6, 24)
				if (hov) hovered = card
				ctx.fill(lx + 3, cy, lx + colW - 3, cy + 24, if (hov) pal.borderHover else pal.border)
				ctx.fill(lx + 4, cy + 1, lx + colW - 4, cy + 23, if (hov) pal.fillHover else pal.fill)
				stacks[card.slot]?.let { ctx.item(it, lx + 6, cy + 4) }
				ctx.text(font, MenuTheme.fit(card.name, colW - 34, font::width), lx + 26, cy + 3, pal.text)
				val soldFor = card.listing?.soldFor
				ctx.text(font, (if (soldFor != null) "§aSold §6" else "§6") + AhModel.shortCoins(card.listing?.price), lx + 26, cy + 13, pal.text)

				if (AhTileCounts.TConfig.show) stacks[card.slot]?.let { st -> AhTileCounts.parse(st.view().loreAccordingToNbt.map { it.bareText }) }?.let { n -> ctx.text(font, "§7×§f$n", lx + colW - 8 - font.width("×$n"), cy + 13, pal.text) }
				hits += Hit(lx + 3, cy, colW - 6, 24, card.slot, null)
				cy += 27
			}
		}
		val py = y + h - 24
		p.button(AhControl.PREV)?.let { btn(ctx, mx, my, x + 8, py, 60, 18, "◀ Prev", it.slot) }
		p.button(AhControl.NEXT)?.let { btn(ctx, mx, my, x + w - 68, py, 60, 18, "Next ▶", it.slot) }
		p.button(AhControl.BACK)?.let { btn(ctx, mx, my, x + w / 2 - 54, py, 50, 18, "Back", it.slot) }
		p.button(AhControl.CLOSE)?.let { btn(ctx, mx, my, x + w / 2 + 4, py, 50, 18, "Close", it.slot) }
		val hc = hovered
		if (hc != null && c.shows("popover")) popover(ctx, hc, mx, my)
	}

	private fun forward(slot: Int, mouse: Int) {

		if (Client.player?.containerMenu !== screen.menu) { session?.fallback("window changed"); return }
		val s = screen.menu.slots.getOrNull(slot) ?: return
		MenuClicks.click(screen.menu, s, mouse)
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val mouse = when (click.button()) { InputConstants.MOUSE_BUTTON_LEFT -> 0; InputConstants.MOUSE_BUTTON_RIGHT -> 1; else -> return false }
		val hit = hits.lastOrNull { MenuTheme.inside(click.x().toInt(), click.y().toInt(), it.x, it.y, it.w, it.h) } ?: return false

		hit.action?.let { if (mouse == 0) { dev.ashtonite365.apex.features.menus.MenuPress.acted(); it() }; return true }
		if (loading && hit.slot != null) return true
		hit.slot?.let { forward(it, mouse) }
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		val p = page ?: return false
		if (loading) return true

		val g = gridRect
		if (p.kind == AhKind.BROWSER && g != null && MenuTheme.inside(mouseX.toInt(), mouseY.toInt(), g[0], g[1], g[2], g[3])) {
			if (amount < 0 && gridCanScroll[1] > 0) { gridScroll++; return true }
			if (amount > 0 && gridCanScroll[0] > 0) { gridScroll--; return true }
		}

		return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		val p = page ?: return false
		if (loading) return false
		val b = when (input.input()) {
			InputConstants.KEY_LEFT -> p.button(AhControl.PREV)
			InputConstants.KEY_RIGHT -> p.button(AhControl.NEXT)
			else -> null
		} ?: return false

		if (!keyGate.press(input.input())) return true
		forward(b.slot, 0)
		return true
	}

	override fun charTyped(input: CharacterEvent) = false
}

object AhMenuText {

	fun createHintLines(width: Int, measure: (String) -> Int): List<String> =
		MenuTheme.wrapFit("Put an item in: click it in your inventory with the vanilla menu", width, 4, measure, "§7")

	fun inventoryHeader(width: Int, measure: (String) -> Int): String {
		val full = "§7Your inventory §8(click to list)"
		return if (measure(full) <= width) full else MenuTheme.fit("§7Your inventory", width, measure)
	}
}
