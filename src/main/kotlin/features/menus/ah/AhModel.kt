// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.ah

import dev.ashtonite365.apex.features.auction.AhSoldPrice
import dev.ashtonite365.apex.features.auction.AuctionListing
import dev.ashtonite365.apex.features.auction.AuctionListingParser

data class AhSlot(val index: Int, val name: String, val lore: List<String>, val hasItem: Boolean, val filler: Boolean = false)

enum class AhKind { BROWSER, VIEW, CONFIRM, CREATE, MANAGE }

enum class AhControl { PREV, NEXT, SEARCH, SORT, RARITY, BIN_FILTER, RESET, BACK, CLOSE, CLAIM_ALL, CREATE, PRICE, DURATION, BID, BUY, CONFIRM, CANCEL, COLLECT, CANCEL_AUCTION, SWITCH_TYPE }

enum class AhLane { ACTIVE, ENDED, OUTBID }

data class AhCard(val slot: Int, val name: String, val listing: AuctionListing?, val lane: AhLane)
data class AhCategory(val slot: Int, val name: String, val selected: Boolean)
data class AhButton(val slot: Int, val control: AhControl, val name: String, val lore: List<String>) {

	val selection: String? get() = lore.firstOrNull { it.trimStart().startsWith("▶") || it.trimStart().startsWith("➜") }
		?.trimStart('▶', '➜', ' ')?.trim()
}

data class AhPage(
	val kind: AhKind,
	val title: String,
	val cards: List<AhCard>,
	val categories: List<AhCategory>,
	val buttons: Map<AhControl, AhButton>,

	val hero: AhSlot?,
	val page: Int?, val pages: Int?,
) {
	fun button(c: AhControl) = buttons[c]
}

object AhModel {

	val BROWSER = Regex("^(?i)(?:Auctions\\s+Browser|Auctions:\\s*\".*\"?|.{1,16}'s\\s+Auctions)\\s*(?:\\(\\d+/\\d+\\))?$")
	val VIEW = Regex("^(?i)(?:BIN\\s+)?Auction\\s+View$")
	val CONFIRM = Regex("^(?i)Confirm\\s+(?:Purchase|Bid)$")
	val CREATE = Regex("^(?i)Create\\s+(?:BIN\\s+)?Auction$")
	val MANAGE = Regex("^(?i)(?:Manage\\s+Auctions|Your\\s+Bids|View\\s+Bids)$")

	fun searchQuery(title: String): String? {
		val m = Regex("Auctions:\\s*\"(.*)$").find(title.trimEnd()) ?: return null
		val raw = m.groupValues[1]
		val closed = Regex("^(.*)\"\\s*(?:\\(\\d+/\\d+\\))?$").find(raw)
		if (closed != null) return closed.groupValues[1]
		return if (title.length >= dev.ashtonite365.apex.features.menus.MenuTitles.MIN_CUT) raw.trimEnd() + "…" else raw
	}

	private val FILTERED = Regex("^\\s*Filtered:\\s*(.+?)\\s*$")

	fun searchTerm(p: AhPage): String? =
		p.button(AhControl.SEARCH)?.lore?.firstNotNullOfOrNull { FILTERED.find(it)?.groupValues?.get(1) } ?: searchQuery(p.title)

	val ANY = Regex(listOf(BROWSER, VIEW, CONFIRM, CREATE, MANAGE).joinToString("|") { "(?:${it.pattern.removePrefix("^(?i)").removeSuffix("$")})" }.let { "^(?i)(?:$it)$" })

	fun kindOf(title: String): AhKind? {
		val t = dev.ashtonite365.apex.features.menus.support.MenuText.clean(title)
		return when {
			BROWSER.matches(t) -> AhKind.BROWSER
			VIEW.matches(t) -> AhKind.VIEW
			CONFIRM.matches(t) -> AhKind.CONFIRM
			CREATE.matches(t) -> AhKind.CREATE
			MANAGE.matches(t) -> AhKind.MANAGE
			else -> null
		}
	}

	fun controlOf(name: String): AhControl? {
		val n = name.lowercase().trim()
		return when {
			n.startsWith("previous page") || n == "prev page" -> AhControl.PREV
			n.startsWith("next page") -> AhControl.NEXT
			n == "search" || n.startsWith("search ") -> AhControl.SEARCH
			n.startsWith("sort") -> AhControl.SORT
			n.startsWith("item tier") || n.startsWith("rarity") -> AhControl.RARITY
			n.startsWith("bin filter") || n == "bin only" -> AhControl.BIN_FILTER
			n.startsWith("reset") -> AhControl.RESET
			n.startsWith("go back") || n == "back" -> AhControl.BACK
			n == "close" -> AhControl.CLOSE
			n.startsWith("claim all") -> AhControl.CLAIM_ALL

			n.startsWith("switch to ") -> AhControl.SWITCH_TYPE
			n.startsWith("create ") && n.contains("auction") -> AhControl.CREATE
			n.startsWith("item price") || n.startsWith("starting bid") || n.startsWith("set price") -> AhControl.PRICE
			n.startsWith("duration") -> AhControl.DURATION
			n.startsWith("submit bid") || n.startsWith("place bid") || n == "bid" -> AhControl.BID
			n.startsWith("buy item") || n.startsWith("buy it now") -> AhControl.BUY
			n.startsWith("confirm") -> AhControl.CONFIRM
			n == "cancel" -> AhControl.CANCEL
			n.startsWith("collect") || n.startsWith("claim") -> AhControl.COLLECT
			n.startsWith("cancel auction") -> AhControl.CANCEL_AUCTION
			else -> null
		}
	}

	enum class AuctionType { AUCTION, BIN }
	private val CREATE_BIN_TITLE = dev.ashtonite365.apex.features.menus.MenuTitles.anyOf(
		Regex("^Create\\s+BIN\\s+Auction\\s*$"), dev.ashtonite365.apex.features.menus.MenuTitles.cutPrefixOf("Create BIN Auction"))

	fun auctionType(p: AhPage): AuctionType? {
		if (p.kind != AhKind.CREATE) return null
		val sw = p.button(AhControl.SWITCH_TYPE)?.name?.let { dev.ashtonite365.apex.features.menus.support.MenuText.clean(it).lowercase() }
		return when {
			sw != null && sw.contains("bin") -> AuctionType.AUCTION
			sw != null && sw.contains("auction") -> AuctionType.BIN
			CREATE_BIN_TITLE.containsMatchIn(p.title.trim()) -> AuctionType.BIN
			else -> AuctionType.AUCTION
		}
	}

	fun priceLabel(b: AhButton): String {
		val n = dev.ashtonite365.apex.features.menus.support.MenuText.clean(b.name)
		if (Regex("\\d").containsMatchIn(n)) return n.substringAfter(":").trim().ifEmpty { n }
		return b.lore.map { dev.ashtonite365.apex.features.menus.support.MenuText.clean(it) }
			.firstOrNull { it.contains("coins", true) && !it.contains("fee", true) } ?: n
	}

	fun laneOf(lore: List<String>): AhLane {
		val l = lore.joinToString("\n").lowercase()
		return when {
			"outbid" in l -> AhLane.OUTBID
			"status: sold" in l || "status: expired" in l || "status: ended" in l || "ended!" in l || "click to collect" in l || "click to claim" in l -> AhLane.ENDED
			else -> AhLane.ACTIVE
		}
	}

	private val PAGE = Regex("\\((\\d+)/(\\d+)\\)")

	private val ARROW_PAGE = Regex("^\\((\\d+)\\s*/\\s*(\\d+)\\)$")

	fun parse(title: String, slots: List<AhSlot>): AhPage? {
		val kind = kindOf(title) ?: return null
		val t = dev.ashtonite365.apex.features.menus.support.MenuText.clean(title)
		val real = slots.filter { it.hasItem && !it.filler && it.name.isNotBlank() }
		if (real.isEmpty()) return null
		val buttons = linkedMapOf<AhControl, AhButton>()
		val used = mutableSetOf<Int>()
		for (s in real) controlOf(s.name)?.let { c -> if (c !in buttons) { buttons[c] = AhButton(s.index, c, s.name, s.lore); used += s.index } }

		val pm = PAGE.find(t) ?: listOfNotNull(buttons[AhControl.NEXT], buttons[AhControl.PREV]).firstNotNullOfOrNull { b ->
			b.lore.firstNotNullOfOrNull { ARROW_PAGE.find(it.trim()) } }
		val page = pm?.groupValues?.get(1)?.toIntOrNull()
		val pages = pm?.groupValues?.get(2)?.toIntOrNull()

		return when (kind) {
			AhKind.BROWSER -> {
				val categories = real.filter { it.index % 9 == 0 && it.index !in used }.map { s ->
					AhCategory(s.index, s.name, s.lore.any { it.contains("Currently browsing", true) || it.contains("selected", true) })
				}
				val cards = real.filter { it.index !in used && it.index % 9 != 0 }.mapNotNull { s ->
					AuctionListingParser.parse(s.name, s.lore)?.let { AhCard(s.index, s.name, it, AhLane.ACTIVE) }
				}

				if (buttons.keys.none { it in setOf(AhControl.SEARCH, AhControl.SORT, AhControl.NEXT, AhControl.PREV, AhControl.BACK) }) return null
				AhPage(kind, t, cards, categories, buttons, null, page, pages)
			}
			AhKind.MANAGE -> {
				val cards = real.filter { it.index !in used }.mapNotNull { s ->
					val listing = AuctionListingParser.parse(s.name, s.lore)
					if (listing == null && s.lore.none { it.contains("Status", true) || it.contains("bid", true) }) null
					else AhCard(s.index, s.name, listing, laneOf(s.lore))
				}
				if (cards.isEmpty() && buttons.isEmpty()) return null
				AhPage(kind, t, cards, emptyList(), buttons, null, page, pages)
			}
			AhKind.VIEW, AhKind.CONFIRM, AhKind.CREATE -> {
				if (buttons.isEmpty()) return null

				val hero = real.firstOrNull { it.index !in used && !it.name.trim().startsWith("Click an item", true) }
				AhPage(kind, t, emptyList(), emptyList(), buttons, hero, page, pages)
			}
		}
	}

	fun columns(w: Int, minCard: Int, fixedColumns: Int, gap: Int = 4): Int =
		if (fixedColumns > 0) fixedColumns else ((w + gap) / (minCard + gap)).coerceAtLeast(1)

	fun playerInvSlotIds(topSize: Int): List<Int> = (0 until 36).map { topSize + it }

	fun gridToSlotId(gridIndex: Int, ids: List<Int>): Int? = ids.getOrNull(gridIndex)

	fun soldOnView(p: AhPage): AhSoldPrice.Sale? {
		if (p.kind != AhKind.VIEW) return null
		val hero = p.hero ?: return null
		AhSoldPrice.parseLore(hero.name, hero.lore)?.let { return it }
		val collect = p.button(AhControl.COLLECT) ?: return null
		return AhSoldPrice.parseLore(hero.name, collect.lore)
	}

	fun soldLine(name: String, l: AuctionListing?): Pair<String, String?>? {
		val sold = l?.soldFor ?: return null
		val net = AhSoldPrice.tracker.find(name, l.buyer)?.net
		return (dev.ashtonite365.apex.util.ApexFormatters.withThousands(sold, 0) + " coins") to
			net?.let { dev.ashtonite365.apex.util.ApexFormatters.withThousands(it.toDouble(), 0) + " coins" }
	}

	fun shortCoins(v: Double?): String {
		if (v == null) return "?"
		return when {
			v >= 1e9 -> "%.2fB".format(v / 1e9)
			v >= 1e6 -> "%.2fM".format(v / 1e6)
			v >= 1e3 -> "%.1fk".format(v / 1e3)
			else -> "%.0f".format(v)
		}
	}
}

class AhStateCache<T> {
	data class Shown<T>(val page: AhPage?, val extra: T?, val loading: Boolean)
	private var last: AhPage? = null
	private var lastExtra: T? = null
	private var pendingSince = -1L
	companion object { const val HOLD_MS = 1500L }

	fun complete(p: AhPage) = when (p.kind) {
		AhKind.BROWSER -> p.categories.isNotEmpty()
		else -> true
	}

	fun update(parsed: AhPage?, extra: T, now: Long, titleKind: AhKind? = parsed?.kind, titleText: String? = null): Shown<T> {
		if (parsed != null && complete(parsed)) { last = parsed; lastExtra = extra; pendingSince = -1; return Shown(parsed, extra, false) }
		val cached = last
		if (pendingSince < 0) pendingSince = now

		val sameFamily = cached != null && titleKind == cached.kind &&
			(cached.kind == AhKind.BROWSER || cached.kind == AhKind.MANAGE || titleText == null || titleText == cached.title)
		if (cached != null && sameFamily && now - pendingSince < HOLD_MS) return Shown(cached, lastExtra, true)
		if (parsed != null) { last = parsed; lastExtra = extra; pendingSince = -1 }
		return Shown(parsed, if (parsed != null) extra else null, parsed == null)
	}

	fun covering(now: Long) = last != null && pendingSince >= 0 && now - pendingSince < HOLD_MS
	fun clear() { last = null; lastExtra = null; pendingSince = -1 }
}

object AhGrid {
	fun rowsFit(bodyH: Int, cardH: Int, gap: Int = 4) = maxOf(1, (bodyH + gap) / (cardH + gap))
	fun totalRows(cards: Int, cols: Int) = (cards + cols - 1) / maxOf(1, cols)
	fun maxScroll(cards: Int, cols: Int, fit: Int) = maxOf(0, totalRows(cards, cols) - fit)
	fun clamp(scroll: Int, cards: Int, cols: Int, fit: Int) = scroll.coerceIn(0, maxScroll(cards, cols, fit))

	fun visible(cards: Int, cols: Int, fit: Int, scroll: Int): IntRange {
		val s = clamp(scroll, cards, cols, fit) * cols
		return s until minOf(cards, s + fit * cols)
	}
	fun hiddenBelow(cards: Int, cols: Int, fit: Int, scroll: Int) = maxOf(0, cards - visible(cards, cols, fit, scroll).let { it.last + 1 })
}
