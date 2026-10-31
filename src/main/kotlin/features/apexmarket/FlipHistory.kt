/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): completed-flip history. Original implementation. Reads chat only: bazaar fills and
 * instant trades, AH purchases and sales. Buys are kept as FIFO lots per item; a later sale of the same
 * item is matched against them and recorded as a flip (buy, sell, profit after tax). Nothing is automated.
 */
package dev.ashtonite365.apex.features.apexmarket

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.auction.AhSoldPrice
import dev.ashtonite365.apex.features.auction.BazaarOrders
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

object FlipHistory {
	@Config
	object TConfig : ManagedConfig("flip-history", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }
		val maxEntries by integer("max-entries", 10, 1000) { 200 }
	}

	@Serializable
	data class Lot(var amount: Long, val pricePerUnit: Double)

	@Serializable
	data class Flip(val item: String, val amount: Long, val buyPerUnit: Double, val sellPerUnit: Double, val profit: Double,
	                val market: String, val time: Long)

	@Serializable
	data class Data(
		val lots: MutableMap<String, MutableList<Lot>> = mutableMapOf(),
		val flips: MutableList<Flip> = mutableListOf(),
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "flip-history", ::Data)

	fun takeFifo(lots: MutableList<Lot>, amount: Long): Pair<Long, Double> {
		var left = amount; var cost = 0.0
		while (left > 0 && lots.isNotEmpty()) {
			val l = lots.first()
			val n = minOf(left, l.amount)
			cost += n * l.pricePerUnit; l.amount -= n; left -= n
			if (l.amount <= 0) lots.removeAt(0)
		}
		return (amount - left) to cost
	}

	fun recordSale(data: Data, item: String, amount: Long, netPerUnit: Double, market: String, now: Long, max: Int): Flip? {
		val lots = data.lots[item] ?: return null
		val (n, cost) = takeFifo(lots, amount)
		if (lots.isEmpty()) data.lots.remove(item)
		if (n <= 0) return null
		val f = Flip(item, n, cost / n, netPerUnit, netPerUnit * n - cost, market, now)
		data.flips.add(0, f)
		while (data.flips.size > max) data.flips.removeAt(data.flips.size - 1)
		return f
	}

	fun recordAhSale(data: Data, item: String, netPerUnit: Double, now: Long, max: Int): Flip? {
		val key = data.lots.keys.firstOrNull { it == item }
			?: data.lots.keys.firstOrNull { AhSoldPrice.normaliseName(it) == AhSoldPrice.normaliseName(item) }
			?: data.lots.keys.firstOrNull { AhSoldPrice.sameItem(it, item) }
			?: return null
		return recordSale(data, key, 1, netPerUnit, "AH", now, max)
	}

	fun applyCollected(data: Data, item: String, collected: Double): Flip? {
		val i = data.flips.indexOfFirst { it.market == "AH" && it.amount == 1L && AhSoldPrice.sameItem(it.item, item) }
		if (i < 0) return null
		val f = data.flips[i]
		val n = f.copy(sellPerUnit = collected, profit = collected - f.buyPerUnit * f.amount)
		data.flips[i] = n
		return n
	}

	fun recordBuy(data: Data, item: String, amount: Long, perUnit: Double) {
		if (amount > 0) data.lots.getOrPut(item) { mutableListOf() }.add(Lot(amount, perUnit))
	}

	private val BZ_BOUGHT = Regex("\\[Bazaar] Bought ([0-9,]+)x (.+) for ([0-9,.]+) coins!")
	private val BZ_SOLD = Regex("\\[Bazaar] Sold ([0-9,]+)x (.+) for ([0-9,.]+) coins!")
	private val BZ_FILLED = Regex("\\[Bazaar] Your (Buy Order|Sell Offer) for ([0-9,]+)x (.+) was filled!")
	private val AH_BOUGHT = Regex("You purchased (.+) for ([0-9,]+) coins!")
	private fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		val data = DConfig.data ?: return
		val line = event.bareText.trim()
		val now = System.currentTimeMillis()
		val max = TConfig.maxEntries
		val bzTax = FlipFinder.TConfig.bzTaxTenths / 1000.0
		val ahTax = FlipFinder.TConfig.ahTaxPercent / 100.0
		var changed = true
		BZ_BOUGHT.matchEntire(line)?.let { m ->
			val n = num(m.groupValues[1]).toLong(); recordBuy(data, m.groupValues[2], n, num(m.groupValues[3]) / n.coerceAtLeast(1))
		} ?: BZ_SOLD.matchEntire(line)?.let { m ->
			val n = num(m.groupValues[1]).toLong(); recordSale(data, m.groupValues[2], n, num(m.groupValues[3]) / n.coerceAtLeast(1), "BZ", now, max)
		} ?: BZ_FILLED.matchEntire(line)?.let { m ->
			val n = num(m.groupValues[2]).toLong(); val item = m.groupValues[3]; val buy = m.groupValues[1] == "Buy Order"
			val ppu = BazaarOrders.currentOrders.firstOrNull { it.isBuy == buy && it.itemName == item }?.pricePerUnit ?: return@let null
			if (buy) recordBuy(data, item, n, ppu) else recordSale(data, item, n, ppu * (1 - bzTax), "BZ", now, max)
		} ?: AH_BOUGHT.matchEntire(line)?.let { m ->
			recordBuy(data, m.groupValues[1], 1, num(m.groupValues[2]))
		} ?: AhSoldPrice.parseSoldChat(line)?.let { s ->

			recordAhSale(data, s.item, (s.gross ?: return@let null) * (1 - ahTax), now, max)
		} ?: AhSoldPrice.parseCollectedChat(line)?.let { s ->
			applyCollected(data, s.item, (s.net ?: return@let null).toDouble())
		} ?: run { changed = false }
		if (changed) DConfig.markDirty()
	}
}
