/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: final sale price of your own auctions, read LIVE from chat and from the AH pages. Original
 * implementation. Nothing is clicked or sent: it only reads chat lines and the lore Hypixel already shows.
 *
 * Real lines (ashpc-latest-auction-1427.log):
 *  chat  "§6[Auction] §b_YaoNexxus §ebought §fLegendary Spirit Sceptre ✪✪✪✪✪ §efor §610,000,000 coins §lCLICK"
 *  chat  "You collected 1,980,000 coins from selling Fabled Aspect of the Dragons ✪✪✪✪✪ to [MVP+] kuyyf in an auction!"
 *  lore  Manage Auctions / BIN Auction View: "Buyer: [MVP+] _YaoNexxus", "Sold for: 10,000,000 coins", "Status: Sold!"
 *  lore  BIN Auction View slot 31 "Collect Auction": "Item sold to [MVP+] _YaoNexxus, for a marvelous 10,000,000 coins, gg!"
 *  lore  Create BIN Auction: "Item: Legendary Spirit Sceptre ✪✪✪✪✪", "Item price: 10,000,000 coins"
 */
package dev.ashtonite365.apex.features.auction

import dev.ashtonite365.apex.Apex
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object AhSoldPrice {

	data class Sale(val item: String, val buyer: String?, val gross: Long?, val net: Long? = null, val time: Long = 0)

	data class Bid(val item: String, val bidder: String, val amount: Long)

	private val STARS = Regex("[✪➊➋➌➍➎➏➐➑➒]+")
	private val RANK = "(?:\\[[^\\]]+]\\s*)?"

	fun cleanLine(s: String): String = MenuText.clean(s).replace(Regex("\\s*CLICK\\s*$"), "").trim()

	fun normaliseName(s: String): String = MenuText.clean(s)
		.replace(STARS, " ")
		.replace(Regex("^\\[Lvl \\d+]\\s*", RegexOption.IGNORE_CASE), "")
		.replace(Regex("^\\d[\\d,]*x\\s+"), "")
		.replace(Regex("[!.]+$"), "")
		.replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim().lowercase()

	fun sameItem(a: String, b: String): Boolean {
		val x = normaliseName(a); val y = normaliseName(b)
		if (x.isEmpty() || y.isEmpty()) return false
		if (x == y) return true
		return x.endsWith(" $y") || y.endsWith(" $x")
	}

	fun coins(s: String): Long? = s.replace(",", "").trim().toDoubleOrNull()?.toLong()

	private val CHAT_BOUGHT = Regex("^\\[Auction]\\s+$RANK(\\S+)\\s+bought\\s+(.+?)\\s+for\\s+([\\d,]+(?:\\.\\d+)?)\\s+coins!?$")

	private val CHAT_BID = Regex("^\\[Auction]\\s+$RANK(\\S+)\\s+bid\\s+([\\d,]+(?:\\.\\d+)?)\\s+coins\\s+on\\s+(.+?)!?$")
	private val CHAT_COLLECTED = Regex("^You collected ([\\d,]+(?:\\.\\d+)?) coins from selling (.+?) to $RANK(\\S+) in an auction!?$")

	fun parseSoldChat(raw: String): Sale? {
		val m = CHAT_BOUGHT.matchEntire(cleanLine(raw)) ?: return null
		return Sale(m.groupValues[2].trim(), m.groupValues[1], coins(m.groupValues[3]))
	}

	fun parseBidChat(raw: String): Bid? {
		val m = CHAT_BID.matchEntire(cleanLine(raw)) ?: return null
		return Bid(m.groupValues[3].trim(), m.groupValues[1], coins(m.groupValues[2]) ?: return null)
	}

	fun parseCollectedChat(raw: String): Sale? {
		val m = CHAT_COLLECTED.matchEntire(cleanLine(raw)) ?: return null
		return Sale(m.groupValues[2].trim(), m.groupValues[3], null, coins(m.groupValues[1]))
	}

	private val SOLD_FOR = Regex("^Sold for:?\\s*([\\d,]+(?:\\.\\d+)?)\\s*coins", RegexOption.IGNORE_CASE)
	private val BUYER = Regex("^(?:Buyer|Bidder):?\\s*$RANK(\\S+)", RegexOption.IGNORE_CASE)
	private val TOP_BID = Regex("^Top bid:?\\s*([\\d,]+(?:\\.\\d+)?)\\s*coins", RegexOption.IGNORE_CASE)
	private val STATUS = Regex("^Status:?\\s*(.+)$", RegexOption.IGNORE_CASE)
	private val COLLECT = Regex("Item sold to\\s+$RANK(\\S+?),?\\s+for a marvelous\\s+([\\d,]+(?:\\.\\d+)?)\\s+coins", RegexOption.IGNORE_CASE)
	private val ITEM_PRICE = Regex("^Item price:?\\s*([\\d,]+(?:\\.\\d+)?)\\s*coins", RegexOption.IGNORE_CASE)
	private val ITEM = Regex("^(?:Item|Selling):\\s*(.+)$")

	fun parseLore(itemName: String, lore: List<String>): Sale? {
		val lines = lore.map(MenuText::clean)
		var sold: Long? = null; var top: Long? = null; var buyer: String? = null; var status: String? = null
		for (l in lines) {
			SOLD_FOR.find(l)?.let { sold = coins(it.groupValues[1]) }
			TOP_BID.find(l)?.let { top = coins(it.groupValues[1]) }
			BUYER.find(l)?.let { buyer = it.groupValues[1].trimEnd(',') }
			STATUS.find(l)?.let { status = it.groupValues[1].lowercase() }
		}

		COLLECT.find(lines.joinToString(" "))?.let { m ->
			return Sale(itemName, buyer ?: m.groupValues[1].trimEnd(','), sold ?: coins(m.groupValues[2]))
		}
		sold?.let { return Sale(itemName, buyer, it) }
		val ended = status?.let { "sold" in it || "ended" in it } == true
		if (ended && buyer != null && top != null) return Sale(itemName, buyer, top)
		return null
	}

	fun parseListedPrice(lore: List<String>, name: String = ""): Pair<String?, Long>? {
		val lines = lore.map(MenuText::clean) + MenuText.clean(name)
		val price = lines.firstNotNullOfOrNull { ITEM_PRICE.find(it)?.groupValues?.get(1)?.let(::coins) } ?: return null
		return lines.firstNotNullOfOrNull { ITEM.find(it)?.groupValues?.get(1)?.trim() } to price
	}

	private val PAGE_TITLE = Regex("^(?i)(?:Manage\\s+Auctions|(?:BIN\\s+)?Auction\\s+View)$")

	fun isSoldPage(title: String) = PAGE_TITLE.matches(MenuText.clean(title))

	fun readPage(title: String, slots: List<Pair<String, List<String>>>): List<Sale> {
		if (!isSoldPage(title)) return emptyList()
		val out = mutableListOf<Sale>()
		var hero: String? = null
		val collect = mutableListOf<List<String>>()
		for ((rawName, lore) in slots) {
			val name = MenuText.clean(rawName)
			if (name.isEmpty()) continue
			if (name.startsWith("Collect Auction", true) || name.startsWith("Claim", true)) { collect += lore; continue }
			if (hero == null && lore.any { MenuText.clean(it).startsWith("Seller:") }) hero = name
			parseLore(name, lore)?.let { out += it }
		}
		for (lore in collect) {
			val itemName = hero ?: continue
			val s = parseLore(itemName, lore) ?: continue
			if (out.none { sameItem(it.item, s.item) }) out += s
		}
		return out
	}

	class Tracker(private val max: Int = 50) {
		val sales = ArrayList<Sale>()

		fun find(item: String, buyer: String? = null): Sale? =
			sales.firstOrNull { sameItem(it.item, item) && (buyer == null || it.buyer == null || it.buyer.equals(buyer, true)) }

		fun record(s: Sale): Sale {
			val old = find(s.item, s.buyer)
			val merged = if (old == null) s else Sale(
				old.item, old.buyer ?: s.buyer, s.gross ?: old.gross, s.net ?: old.net, maxOf(old.time, s.time))
			if (old != null) sales.remove(old)
			sales.add(0, merged)
			while (sales.size > max) sales.removeAt(sales.size - 1)
			return merged
		}

		fun collected(s: Sale): Sale = record(s)
	}

	val tracker = Tracker()

	private fun warn(reason: String) = Apex.logger.warn("[Apex] ah-sold-price: $reason")

	fun onChatLine(raw: String, now: Long = System.currentTimeMillis()): Sale? {
		val line = cleanLine(raw)
		parseSoldChat(line)?.let { s ->
			if (s.gross == null) { warn("could not parse the price in '$line'"); return null }
			return tracker.record(s.copy(time = now))
		}
		parseCollectedChat(line)?.let { s ->
			if (s.net == null) { warn("could not parse the collected amount in '$line'"); return null }
			return tracker.collected(s.copy(time = now))
		}
		if (line.startsWith("[Auction]") && line.contains(" bought ")) warn("unrecognised auction sale line '$line'")
		else if (line.startsWith("You collected") && line.contains("from selling")) warn("unrecognised collected line '$line'")
		return null
	}

	@Subscribe
	fun onChestUpdate(event: ChestInventoryUpdateEvent) {
		val screen = event.inventory as? ContainerScreen ?: return
		val title = screen.title.bareText
		if (!isSoldPage(title)) return
		val inv = screen.menu.container
		val slots = (0 until inv.containerSize).map { i ->
			val st = inv.getItem(i)
			if (st.isEmpty) "" to emptyList() else st.hoverName.bareText to st.view().loreAccordingToNbt.map { it.bareText }
		}
		val now = System.currentTimeMillis()
		readPage(title, slots).forEach { tracker.record(it.copy(time = now)) }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		onChatLine(event.bareText)
	}
}
