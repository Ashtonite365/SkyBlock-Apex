/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): margin summary across your open bazaar orders (from the BazaarOrders tracker).
 * Buy orders: coins locked plus the projected margin if flipped at today's best sell offer after tax.
 * Sell offers: proceeds after tax, and profit where FlipHistory knows what you paid (FIFO cost basis).
 * Shown at the end of `/apex bzorders` and as a line in the orders HUD. Display only.
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.ApexFormatters

object OrderMargin {
	data class OpenOrder(val item: String, val id: String?, val isBuy: Boolean, val amount: Long, val pricePerUnit: Double)
	data class Summary(val lockedInBuys: Double, val projectedBuyMargin: Double, val sellProceeds: Double,
	                   val knownSellProfit: Double, val unknownCostSells: Int)

	fun summarize(orders: List<OpenOrder>, taxTenths: Int, bestSellOffer: (String) -> Double?,
	              costBasis: (String, Long) -> Double?): Summary {
		val keep = 1 - taxTenths / 1000.0
		var locked = 0.0; var margin = 0.0; var proceeds = 0.0; var profit = 0.0; var unknown = 0
		for (o in orders) {
			if (o.isBuy) {
				locked += o.amount * o.pricePerUnit
				val offer = o.id?.let(bestSellOffer) ?: continue
				margin += (offer * keep - o.pricePerUnit) * o.amount
			} else {
				val p = o.amount * o.pricePerUnit * keep
				proceeds += p
				val cost = costBasis(o.item, o.amount)
				if (cost == null) unknown++ else profit += p - cost
			}
		}
		return Summary(locked, margin, proceeds, profit, unknown)
	}

	fun peekCost(lots: List<FlipHistory.Lot>?, amount: Long): Double? {
		var left = amount; var cost = 0.0
		for (l in lots.orEmpty()) { if (left <= 0) break; val n = minOf(left, l.amount); cost += n * l.pricePerUnit; left -= n }
		return if (left > 0) null else cost
	}

	fun current(): Summary {
		val orders = dev.ashtonite365.apex.features.auction.BazaarOrders.currentOrders.filter { it.lastState != "FILLED" }
			.map { OpenOrder(it.itemName, it.id, it.isBuy, it.amount.toLong(), it.pricePerUnit) }
		val lots = FlipHistory.DConfig.data?.lots
		return summarize(orders, FlipFinder.TConfig.bzTaxTenths,
			{ id -> dev.ashtonite365.apex.repo.HypixelStaticData.bazaarData[dev.ashtonite365.apex.util.bazaarStockOf(dev.ashtonite365.apex.util.ItemId(id))]?.quickStatus?.buyPrice },
			{ item, n -> peekCost(lots?.get(item), n) })
	}

	fun lines(s: Summary): List<String> {
		val f = ApexFormatters::compactNumber
		fun sign(v: Double) = (if (v >= 0) "§a+" else "§c") + f(v)
		return listOf(
			"§7Buys: §6${f(s.lockedInBuys)} §7locked, margin at best offer ${sign(s.projectedBuyMargin)}",
			"§7Sells: §6${f(s.sellProceeds)} §7after tax, known profit ${sign(s.knownSellProfit)}" +
				(if (s.unknownCostSells > 0) " §8(${s.unknownCostSells} without cost basis)" else ""),
		)
	}
}
