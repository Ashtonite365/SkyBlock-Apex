/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (bazaar-1031): the Apex Bazaar is a different DRAWING of Hypixel's own Bazaar chest, nothing more.
 * Pure (unit tested, no Minecraft state): reads the open page (title + slot names/lore) into a [BzPage], and lays it
 * out for the Skyglass or Forge look ([BzPageLayout]). Every Apex button carries exactly one real chest slot and the
 * item name it showed; at click time the view re-reads that slot and sends ONE click only if the name still matches.
 * A page this file cannot read (unknown title, missing required slots) returns null, so the custom menus framework
 * shows the generic styled wrapper, or vanilla. Never fills a sign, never sends a command, never chains clicks.
 * Original implementation.
 */
package dev.ashtonite365.apex.features.apexbzmenu

import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

enum class BzPageKind { CATEGORY, INSTANT_BUY, INSTANT_SELL, CONFIRM_BUY, BUY_ORDER_PRICE, ORDERS, ORDER_OPTIONS, PRODUCT_GROUP, PRODUCT }

enum class BzRole {
	CATEGORY, TILE, OPTION, ORDER, NAV, BACK, CLOSE,

	FOOT,

	INV_TAB,
}

data class BzButton(val slot: Int, val name: String, val lore: List<String>, val role: BzRole, val active: Boolean = false)

data class BzPage(
	val kind: BzPageKind,
	val title: String,

	val rail: List<BzButton>,

	val nav: List<BzButton>,

	val cards: List<BzButton>,
	val back: BzButton?,
	val close: BzButton?,

	val foot: List<BzButton> = emptyList(),
) {
	val all: List<BzButton> get() = rail + nav + cards + foot + listOfNotNull(back, close)
}

object BzPages {
	private const val ARROW = "[\\u279C\\u2192\\u27A1>]"
	private val CATEGORY_TITLE = Regex("^Bazaar\\s*$ARROW\\s*(.+)$")
	private val INSTANT_BUY = Regex("$ARROW\\s*Instant(?: Buy)?$")
	private val INSTANT_SELL = Regex("$ARROW\\s*Instant Sell$")

	private val ARROW_TITLE = Regex("^.+\\s$ARROW\\s*.+$")

	val BAZAAR_BAR = setOf("Sell Inventory Now", "Sell Sacks Now", "Manage Orders", "Bazaar History", "Bazaar Settings")

	val PRODUCT_ACTIONS = setOf("Buy Instantly", "Sell Instantly", "Create Buy Order", "Create Sell Offer")

	fun arrowTitle(title: String) = ARROW_TITLE.matches(MenuText.clean(title))

	fun kindOf(title: String, slots: List<SlotText>): BzPageKind? {
		kindOf(title)?.let { return it }
		if (!arrowTitle(title)) return null
		val names = slots.map { it.cleanName }.toSet()
		return when {
			names.count { it in PRODUCT_ACTIONS } >= 2 -> BzPageKind.PRODUCT
			names.any { it in BAZAAR_BAR } -> BzPageKind.PRODUCT_GROUP
			else -> null
		}
	}

	fun isBazaarChest(title: String, slots: List<SlotText>) =
		kindOf(title, slots) != null || CATEGORY_TITLE.matches(MenuText.clean(title))

	fun kindOf(title: String): BzPageKind? {
		val t = MenuText.clean(title)
		return when {
			t == "Confirm Instant Buy" -> BzPageKind.CONFIRM_BUY
			t == "How much do you want to pay?" -> BzPageKind.BUY_ORDER_PRICE
			t == "Your Bazaar Orders" -> BzPageKind.ORDERS
			t == "Order options" -> BzPageKind.ORDER_OPTIONS
			INSTANT_SELL.containsMatchIn(t) -> BzPageKind.INSTANT_SELL
			INSTANT_BUY.containsMatchIn(t) -> BzPageKind.INSTANT_BUY
			CATEGORY_TITLE.matches(t) -> BzPageKind.CATEGORY
			else -> null
		}
	}

	private fun isCategory(s: SlotText) = s.slot % 9 == 0 && s.cleanLore.firstOrNull() == "Category"

	fun parse(title: String, slots: List<SlotText>, chestSize: Int): BzPage? {
		val kind = kindOf(title, slots.filter { it.slot < chestSize }) ?: return null
		if (chestSize < 27) return null
		val chest = slots.filter { it.slot in 0 until chestSize && it.cleanName.isNotEmpty() }
		val lastRow = chestSize - 9
		val back = chest.firstOrNull { it.cleanName == "Go Back" }
		val close = chest.firstOrNull { it.cleanName == "Close" }
		fun b(s: SlotText, r: BzRole, active: Boolean = false) = BzButton(s.slot, s.cleanName, s.cleanLore, r, active)
		val special = setOfNotNull(back?.slot, close?.slot)
		val rest = chest.filter { it.slot !in special }
		val rail = if (kind == BzPageKind.CATEGORY) rest.filter(::isCategory) else emptyList()
		val railSlots = rail.map { it.slot }.toSet()

		val navRow = chestSize >= 54 || kind == BzPageKind.PRODUCT_GROUP
		val nav = rest.filter { navRow && it.slot >= lastRow && it.slot !in railSlots }
		val navSlots = nav.map { it.slot }.toSet()
		val cardsRaw = rest.filter { it.slot !in railSlots && it.slot !in navSlots }
		val role = when (kind) {
			BzPageKind.CATEGORY, BzPageKind.PRODUCT_GROUP -> BzRole.TILE
			BzPageKind.ORDERS -> BzRole.ORDER
			else -> BzRole.OPTION
		}

		val (cards0, extraNav) = if (kind == BzPageKind.ORDERS) cardsRaw.partition { it.slot < lastRow } else cardsRaw to emptyList()

		val (cards, productFoot) = if (kind == BzPageKind.PRODUCT) productCards(cards0.filter { it.cleanName != "Go Back" }) else cards0 to emptyList()
		val (foot0, top) = (nav + extraNav).sortedBy { it.slot }.partition { it.cleanName in FOOT_NAMES || (kind == BzPageKind.PRODUCT && isProductFoot(it.cleanName)) }
		val foot = (foot0 + productFoot).sortedBy { it.slot }
		val page = BzPage(kind, MenuText.clean(title),
			rail.map { b(it, BzRole.CATEGORY, it.cleanLore.any { l -> l == "Currently viewing!" }) },
			top.map { b(it, BzRole.NAV) },
			cards.map { b(it, role) },
			back?.let { b(it, BzRole.BACK) }, close?.let { b(it, BzRole.CLOSE) },
			foot.map { b(it, BzRole.FOOT) })
		return page.takeIf { layoutOk(it) }
	}

	val PRODUCT_FOOT = listOf("Manage Orders", "View Graphs", "Instasell Ignore")

	val PRODUCT_ORDER = listOf("Buy Instantly", "Sell Instantly", "Create Buy Order", "Create Sell Offer")
	fun isProductFoot(name: String) = PRODUCT_FOOT.any { name.startsWith(it, true) }

	fun productCards(cards: List<SlotText>): Pair<List<SlotText>, List<SlotText>> {
		val (foot, rest) = cards.partition { isProductFoot(it.cleanName) }
		val (actions, info) = rest.partition { it.cleanName in PRODUCT_ACTIONS }
		return (info.sortedBy { it.slot } + actions.sortedBy { PRODUCT_ORDER.indexOf(it.cleanName) }) to foot
	}

	fun layoutOk(p: BzPage): Boolean = when (p.kind) {
		BzPageKind.CATEGORY -> p.rail.size >= 2 && p.cards.isNotEmpty()
		BzPageKind.INSTANT_BUY, BzPageKind.INSTANT_SELL -> p.back != null && p.cards.any { c -> c.lore.any { it.startsWith("Amount:") } }
		BzPageKind.CONFIRM_BUY -> p.back != null && p.cards.size in 1..3 && p.cards.any { c -> c.lore.any { it.startsWith("Price:") } }
		BzPageKind.BUY_ORDER_PRICE -> p.back != null && p.cards.any { it.lore.firstOrNull() == "Buy Order Setup" }
		BzPageKind.ORDERS -> p.back != null || p.close != null
		BzPageKind.ORDER_OPTIONS -> p.back != null && p.cards.isNotEmpty()
		BzPageKind.PRODUCT_GROUP -> p.cards.isNotEmpty() && (p.nav + p.foot).any { it.name in BAZAAR_BAR }
		BzPageKind.PRODUCT -> p.back != null && p.cards.count { it.name in PRODUCT_ACTIONS } >= 2
	}

	fun clickTarget(b: BzButton, liveName: String?): Int? =
		b.slot.takeIf { it >= 0 && liveName != null && MenuText.clean(liveName) == b.name }

	val FOOT_NAMES = setOf("Bazaar History", "Bazaar Settings")

	private val YOU_EARN = Regex("^You earn:\\s*(.+?)\\s*$")
	private val ITEM_LINE = Regex("^\\s*[\\d,]+x .+ for ([\\d,]+(?:\\.\\d+)?) coins?\\s*$")

	enum class SellSource { YOU_EARN_LINE, SUM_OF_ITEM_LINES }

	fun sellValue(b: BzButton): Pair<String, SellSource>? {
		if (!b.name.startsWith("Sell ") || !b.name.endsWith(" Now")) return null
		b.lore.firstNotNullOfOrNull { YOU_EARN.matchEntire(it.trim())?.groupValues?.get(1) }?.let {
			return (if (it.endsWith("coins") || it.endsWith("coin")) it else "$it coins") to SellSource.YOU_EARN_LINE
		}
		val items = b.lore.mapNotNull { ITEM_LINE.matchEntire(it)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }
		if (items.isEmpty()) return null
		val sum = items.sum()
		val txt = if (sum % 1.0 == 0.0) "%,d".format(sum.toLong()) else "%,.1f".format(sum)
		return "$txt coins" to SellSource.SUM_OF_ITEM_LINES
	}
}

data class BzPlaced(val button: BzButton, val x: Int, val y: Int, val w: Int, val h: Int, val icon: Boolean, val scrolled: Boolean = false) {
	fun overlaps(o: BzPlaced) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
	fun inside(px: Int, py: Int, pw: Int, ph: Int) = x >= px && y >= py && x + w <= px + pw && y + h <= py + ph
}

enum class BzLook { SKYGLASS, FORGE }

object BzPageLayout {
	const val PAD = 6
	const val TITLE_H = 18
	const val NAV_H = 20

	const val NAV_H_VALUE = 30
	const val FOOT_H = 20
	const val RAIL_W = 100
	const val GAP = 4
	const val BACK_W = 90

	fun invTab(active: Boolean) = BzButton(-1, "Inventory", emptyList(), BzRole.INV_TAB, active)

	fun rail(p: BzPage, inv: Boolean?): List<BzButton> =
		if (inv == null) p.rail else listOf(invTab(inv)) + p.rail.map { if (inv) it.copy(active = false) else it }

	data class Result(val placed: List<BzPlaced>, val content: IntArray, val contentHeight: Int,
		val list: IntArray? = null,

		val view: IntArray? = null) {

		fun shown(): List<BzPlaced> = placed.mapNotNull { pl ->
			val v = view
			if (!pl.scrolled || v == null) pl else {
				val y0 = maxOf(pl.y, v[1]); val y1 = minOf(pl.y + pl.h, v[1] + v[3])
				if (y1 <= y0) null else pl.copy(y = y0, h = y1 - y0)
			}
		}
	}

	@Suppress("UNUSED_PARAMETER") private fun navButtons(p: BzPage, inv: Boolean?) = p.nav
	fun navH(p: BzPage) = if (p.nav.any { BzPages.sellValue(it) != null }) NAV_H_VALUE else NAV_H

	fun footW(b: BzButton, sky: Boolean, measure: (String) -> Int) = measure(b.name) + 10 + (if (sky) 18 else 0)

	fun footRows(p: BzPage, w: Int, sky: Boolean, measure: (String) -> Int): Int {
		if (p.foot.isEmpty()) return 1
		val ws = p.foot.map { footW(it, sky, measure) }
		val side = (w - 2 * PAD - (if (p.back != null) BACK_W + 2 * GAP else 0)) / 2
		return if (ws.all { it <= side } && p.foot.size <= 2) 1 else 2
	}

	fun contentArea(p: BzPage, x: Int, y: Int, w: Int, h: Int, sky: Boolean = true, measure: (String) -> Int = ::approx, inv: Boolean? = null): IntArray {
		val top = y + PAD + TITLE_H + (if (navButtons(p, inv).isNotEmpty()) navH(p) + GAP else 0)
		val bottom = y + h - PAD - footRows(p, w, sky, measure) * (FOOT_H + GAP)
		return intArrayOf(x + PAD, top, w - 2 * PAD, maxOf(0, bottom - top))
	}

	fun approx(s: String) = s.length * 6

	fun place(p: BzPage, look: BzLook, x: Int, y: Int, w: Int, h: Int, scroll: Int = 0,
		measure: (String) -> Int = ::approx, inv: Boolean? = null): Result {
		val out = mutableListOf<BzPlaced>()
		val sky = look == BzLook.SKYGLASS

		val nav = navButtons(p, inv)
		if (nav.isNotEmpty()) {
			val n = nav.size
			val bw = (w - 2 * PAD - GAP * (n - 1)) / n
			nav.forEachIndexed { i, b -> out += BzPlaced(b, x + PAD + i * (bw + GAP), y + PAD + TITLE_H, bw, navH(p), sky) }
		}

		val fy = y + h - PAD - FOOT_H
		val bw = minOf(BACK_W, w - 2 * PAD)
		p.back?.let { out += BzPlaced(it, x + (w - bw) / 2, fy, bw, FOOT_H, false) }
		val rows = footRows(p, w, sky, measure)
		val ry = if (rows == 1) fy else fy - FOOT_H - GAP
		if (p.foot.size > 2) {

			val n = p.foot.size
			val share = (w - 2 * PAD - GAP * (n - 1)) / n
			val ws = p.foot.map { minOf(footW(it, sky, measure), share) }
			var fx = x + (w - (ws.sum() + GAP * (n - 1))) / 2
			p.foot.forEachIndexed { i, b -> out += BzPlaced(b, fx, ry, ws[i], FOOT_H, sky); fx += ws[i] + GAP }
		} else p.foot.forEachIndexed { i, b ->
			val fw = minOf(footW(b, sky, measure), (w - 2 * PAD - GAP) / 2)
			val fx = if (i == 0) x + PAD else x + w - PAD - fw
			out += BzPlaced(b, fx, ry, fw, FOOT_H, sky)
		}
		val c = contentArea(p, x, y, w, h, sky, measure, inv)
		val (cx, cy, cw, ch) = listOf(c[0], c[1], c[2], c[3])
		var gx = cx; var gw = cw
		val rail = rail(p, inv)
		if (rail.isNotEmpty()) {
			val step = minOf(24, (ch + GAP) / rail.size).coerceAtLeast(14)
			rail.forEachIndexed { i, b ->
				val by = cy + i * step
				if (by + step - GAP <= cy + ch) out += BzPlaced(b, cx, by, RAIL_W, step - GAP, sky)
			}
			gx = cx + RAIL_W + GAP; gw = cw - RAIL_W - GAP
		}
		if (inv == true) {

			return Result(out, c, 0, intArrayOf(gx, cy, gw, ch))
		}
		val (cols, cardH) = when (p.kind) {
			BzPageKind.CATEGORY -> maxOf(1, gw / 120) to 30
			BzPageKind.ORDERS -> maxOf(1, gw / 200) to 50
			BzPageKind.PRODUCT_GROUP -> maxOf(1, gw / 140) to BzPrices.CARD_H
			BzPageKind.PRODUCT -> maxOf(1, minOf(4, gw / 100)) to minOf(ch, 64)
			else -> maxOf(1, minOf(p.cards.size, 4)) to minOf(ch, 120)
		}

		val infoN = if (p.kind == BzPageKind.PRODUCT) p.cards.count { it.name !in BzPages.PRODUCT_ACTIONS } else 0
		val rowSizes = (if (infoN > 0) balancedRows(infoN, cols) else emptyList()) + balancedRows(p.cards.size - infoN, cols)
		val cardRows = rowSizes.size
		val total = cardRows * (cardH + GAP) - GAP

		val usableW = if (total > ch) gw - BzScroll.RESERVE else gw
		val cardW = (usableW - GAP * (cols - 1)) / cols
		val sc = scroll.coerceIn(0, maxOf(0, total - ch))
		val cells = gridCells(rowSizes, cols, usableW, cardW)
		p.cards.forEachIndexed { i, b ->
			val (col, row) = cells[i]
			val bx = gx + col
			val by = cy + row * (cardH + GAP) - sc

			if (by < cy + ch && by + cardH > cy) out += BzPlaced(b, bx, by, cardW, cardH, true, scrolled = true)
		}
		return Result(out, c, maxOf(0, total), view = intArrayOf(gx, cy, gw, ch))
	}

	fun balancedRows(n: Int, maxCols: Int): List<Int> {
		if (n <= 0) return emptyList()
		val rows = (n + maxCols - 1) / maxOf(1, maxCols)
		val base = n / rows; val extra = n % rows
		return List(rows) { if (it < extra) base + 1 else base }
	}

	fun gridCells(rowSizes: List<Int>, maxCols: Int, gridW: Int, cardW: Int): List<Pair<Int, Int>> =
		rowSizes.flatMapIndexed { r, k ->
			val rowW = k * cardW + (k - 1) * GAP
			val left = maxOf(0, (gridW - rowW) / 2)
			List(k) { c -> left + c * (cardW + GAP) to r }
		}

	data class CardText(val nameX: Int, val nameY: Int, val detailX: Int, val detailY: Int, val step: Int, val detailBottom: Int, val actionY: Int?)

	fun cardText(pl: BzPlaced, hasAction: Boolean): CardText {
		val col = if (pl.icon) pl.x + 21 else pl.x + 4
		val action = if (hasAction && pl.h >= 40) pl.y + pl.h - 11 else null
		return CardText(col, pl.y + 4, col, pl.y + 16, 10, (action ?: (pl.y + pl.h - 1)) - 1, action)
	}

	fun fit(s: String, width: Int, measure: (String) -> Int): String {
		if (measure(s) <= width) return s
		var t = s
		while (t.isNotEmpty() && measure("$t…") > width) t = t.dropLast(1)
		return if (t.isEmpty()) "" else "$t…"
	}
}

object BzColor {
	fun mix(a: Int, b: Int, t: Float): Int {
		fun ch(sh: Int) = (((a ushr sh) and 0xFF) + (((b ushr sh) and 0xFF) - ((a ushr sh) and 0xFF)) * t).toInt().coerceIn(0, 255) shl sh
		return ch(24) or ch(16) or ch(8) or ch(0)
	}
}

object BzHeld {

	enum class Source(val tag: String) {

		BAZAAR(""),

		LOWEST_BIN("BIN"),

		AVG_BIN("avg BIN"),
	}

	data class Item(val key: String, val name: String, val count: Int, val unit: Double?, val source: Source?, val stacks: Int = 1, val slot: Int = -1,

		val menuSlot: Int? = null) {
		val total: Double get() = (unit ?: 0.0) * count
	}

	fun bazaarItem(key: String, name: String, count: Int, isProduct: Boolean, instantSell: Double?, slot: Int): Item? =
		if (!isProduct) null else Item(key, name, count, maxOf(0.0, instantSell ?: 0.0), Source.BAZAAR, slot = slot)

	fun bazaarRows(items: List<Item?>): List<Item> = rows(items.filterNotNull().filter { it.source == Source.BAZAAR && it.unit != null })

	const val EMPTY_TEXT = "No Bazaar items in your inventory"

	fun price(bazaarSell: Double?, lowestBin: Double?, avgBin: Double?): Pair<Double, Source>? = when {
		bazaarSell != null && bazaarSell > 0 -> bazaarSell to Source.BAZAAR
		lowestBin != null && lowestBin > 0 -> lowestBin to Source.LOWEST_BIN
		avgBin != null && avgBin > 0 -> avgBin to Source.AVG_BIN
		else -> null
	}

	fun rows(items: List<Item>): List<Item> =
		items.filter { it.count > 0 && it.name.isNotEmpty() }.groupBy { it.key }
			.map { (_, l) -> l.first().copy(count = l.sumOf { it.count }, stacks = l.size) }
			.sortedWith(compareByDescending<Item> { it.total }.thenBy { it.name })

	fun slotRows(items: List<Item?>): List<Item> =
		items.filterNotNull().filter { it.source == Source.BAZAAR && it.unit != null && it.count > 0 && it.name.isNotEmpty() }
			.sortedWith(compareByDescending<Item> { it.total }.thenBy { it.name }.thenBy { it.menuSlot ?: Int.MAX_VALUE })

	fun playerSlots(windowSlots: Int): IntRange = maxOf(0, windowSlots - 36) until windowSlots

	fun clickFor(row: Item, liveKey: String?, packetButton: Int, shift: Boolean): Triple<Int, Int, net.minecraft.world.inventory.ContainerInput>? {
		val slot = row.menuSlot ?: return null
		if (liveKey != row.key) return null
		val (b, input) = dev.ashtonite365.apex.features.menus.support.InventoryPanel.route(packetButton, shift)
		return Triple(slot, b, input)
	}

	fun sendRowClick(menu: net.minecraft.world.inventory.AbstractContainerMenu, row: Item, liveKey: String?, packetButton: Int, shift: Boolean): Boolean {
		val (slot, b, input) = clickFor(row, liveKey, packetButton, shift) ?: return false
		val s = menu.slots.getOrNull(slot) ?: return false
		dev.ashtonite365.apex.util.ApexClickDelay.send(menu, s, b, input)
		return true
	}

	fun grandTotal(rows: List<Item>) = rows.sumOf { it.total }

	fun coins(d: Double): String = when {
		d >= 1e9 -> "%.2fB".format(d / 1e9)
		d >= 1e6 -> "%.2fM".format(d / 1e6)
		d >= 1e4 -> "%,.0f".format(d)
		d % 1.0 == 0.0 -> "%,.0f".format(d)
		else -> "%,.1f".format(d)
	}

	fun unitText(i: Item) = i.unit?.let { u -> coins(u) + (i.source?.tag?.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "") } ?: "—"
	fun totalText(i: Item) = if (i.unit == null) "—" else coins(i.total)
	fun countText(i: Item) = "x${i.count}" + if (i.stacks > 1) " (${i.stacks} stacks)" else ""

	const val HEADER_H = 26
	const val ROW_H = 20
	const val ROW_GAP = 2
	const val EACH_W = 76
	const val TOTAL_W = 64

	fun rowRects(list: IntArray, n: Int, scroll: Int): List<Pair<Int, IntArray>> {
		val top = list[1] + HEADER_H; val bottom = list[1] + list[3]
		val w = if (listHeight(n) > bottom - top) list[2] - BzScroll.RESERVE else list[2]
		return (0 until n).mapNotNull { i ->
			val ry = top + i * (ROW_H + ROW_GAP) - scroll

			if (ry < bottom && ry + ROW_H > top) i to intArrayOf(list[0], ry, w, ROW_H) else null
		}
	}

	fun body(list: IntArray) = intArrayOf(list[0], list[1] + HEADER_H, list[2], maxOf(0, list[3] - HEADER_H))

	fun listHeight(n: Int) = maxOf(0, n * (ROW_H + ROW_GAP) - ROW_GAP)
}

object BzScroll {
	const val W = 3

	const val RESERVE = W + 2

	fun bar(view: IntArray, total: Int, scroll: Int): Pair<IntArray, IntArray>? {
		val vh = view[3]
		if (total <= vh || vh <= 0) return null
		val (top, kh) = dev.ashtonite365.apex.features.menus.MenuTheme.scrollKnob(vh, vh, total, scroll)
		val x = view[0] + view[2] - W
		return intArrayOf(x, view[1], W, vh) to intArrayOf(x, view[1] + top, W, kh)
	}
}

object BzPrices {

	const val CARD_H = 62
	val LABELS = listOf("Buy Order", "Instant Buy", "Sell Order", "Instant Sell")
	private val num = "([0-9][0-9,]*(?:\\.[0-9]+)?)"
	private val loreBuy = Regex("^(?:Buy price|Instant Buy|Buy Price)[^0-9]*$num", RegexOption.IGNORE_CASE)
	private val loreSell = Regex("^(?:Sell price|Instant Sell|Sell Price)[^0-9]*$num", RegexOption.IGNORE_CASE)
	private val loreBuyOrder = Regex("^(?:Top )?Buy Orders?:?[^0-9]*$num", RegexOption.IGNORE_CASE)
	private val loreSellOrder = Regex("^(?:Top )?Sell (?:Orders?|Offers?):?[^0-9]*$num", RegexOption.IGNORE_CASE)

	enum class Source { LORE, BAZAAR_DATA }
	data class Quote(val buyOrder: Double, val instantBuy: Double, val sellOrder: Double, val instantSell: Double, val source: Source) {
		fun values() = listOf(buyOrder, instantBuy, sellOrder, instantSell)
	}

	private fun find(lore: List<String>, r: Regex) = lore.firstNotNullOfOrNull { r.find(it.trim())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }

	fun fromLore(lore: List<String>): Quote? {
		val ib = find(lore, loreBuy); val isell = find(lore, loreSell)
		if (ib == null || isell == null) return null
		return Quote(find(lore, loreBuyOrder) ?: isell, ib, find(lore, loreSellOrder) ?: ib, isell, Source.LORE)
	}

	fun fromData(buyPrice: Double?, sellPrice: Double?): Quote? {
		if (buyPrice == null || sellPrice == null) return null
		return Quote(sellPrice, buyPrice, buyPrice, sellPrice, Source.BAZAAR_DATA)
	}

	fun of(lore: List<String>, buyPrice: Double?, sellPrice: Double?) = fromLore(lore) ?: fromData(buyPrice, sellPrice)

	fun lines(q: Quote) = LABELS.zip(q.values().map { BzHeld.coins(it) })

	fun otherLines(lore: List<String>) = lore.filter { l ->
		val t = l.trim()
		!t.endsWith("commodity", true) && listOf(loreBuy, loreSell, loreBuyOrder, loreSellOrder).none { it.containsMatchIn(t) }
	}
}
