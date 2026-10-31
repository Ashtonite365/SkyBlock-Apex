/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * APEX-26.3 PORT: data model and lore parser for a future fancy auction house browser .
 * Original implementation written for this port; no code copied. No GUI yet.
 */
package dev.ashtonite365.apex.features.auction

import kotlin.time.Duration
import dev.ashtonite365.apex.features.parsers.parseShortDuration

data class AuctionListing(
	val itemName: String,
	val seller: String?,
	val isBin: Boolean,

	val price: Double?,
	val bids: Int,
	val endsIn: Duration?,

	val soldFor: Double? = null,
	val buyer: String? = null,
)

object AuctionListingParser {
	private val SELLER = Regex("^Seller: (?:\\[[^]]+] )?(\\S+).*")
	private val BIN = Regex("^Buy it now: ([0-9,.]+) coins.*")
	private val TOP_BID = Regex("^Top bid: ([0-9,.]+) coins.*")
	private val STARTING = Regex("^Starting bid: ([0-9,.]+) coins.*")
	private val BIDS = Regex("^Bids: ([0-9,]+) bids?.*")
	private val ENDS = Regex("^Ends in: (.+)$")

	private val SOLD_FOR = Regex("^Sold for:?\\s*([0-9,.]+) coins.*")
	private val BUYER = Regex("^Buyer:?\\s*(?:\\[[^]]+] )?(\\S+).*")

	private fun num(s: String) = s.replace(",", "").toDoubleOrNull()

	fun parse(itemName: String, lore: List<String>): AuctionListing? {
		var seller: String? = null
		var bin: Double? = null
		var top: Double? = null
		var start: Double? = null
		var bids = 0
		var ends: Duration? = null
		var sold: Double? = null
		var buyer: String? = null
		for (raw in lore) {
			val l = raw.trim()
			SELLER.matchEntire(l)?.let { seller = it.groupValues[1] }
			BIN.matchEntire(l)?.let { bin = num(it.groupValues[1]) }
			TOP_BID.matchEntire(l)?.let { top = num(it.groupValues[1]) }
			STARTING.matchEntire(l)?.let { start = num(it.groupValues[1]) }
			BIDS.matchEntire(l)?.let { bids = num(it.groupValues[1])?.toInt() ?: 0 }
			ENDS.matchEntire(l)?.let { ends = parseShortDuration(it.groupValues[1]) }
			SOLD_FOR.matchEntire(l)?.let { sold = num(it.groupValues[1]) }
			BUYER.matchEntire(l)?.let { buyer = it.groupValues[1] }
		}
		if (seller == null && bin == null && top == null && start == null && sold == null) return null
		return AuctionListing(itemName, seller, bin != null, sold ?: bin ?: top ?: start, bids, ends, sold, buyer)
	}

	enum class Sort { PRICE_ASC, PRICE_DESC, ENDING_SOON, MOST_BIDS }

	fun sort(list: List<AuctionListing>, sort: Sort): List<AuctionListing> = when (sort) {
		Sort.PRICE_ASC -> list.sortedBy { it.price ?: Double.MAX_VALUE }
		Sort.PRICE_DESC -> list.sortedByDescending { it.price ?: -1.0 }
		Sort.ENDING_SOON -> list.sortedBy { it.endsIn ?: Duration.INFINITE }
		Sort.MOST_BIDS -> list.sortedByDescending { it.bids }
	}
}
