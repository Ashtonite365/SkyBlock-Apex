// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.museum

import dev.ashtonite365.apex.features.auction.AuctionListing

object AhSeenPrices {

	const val FRESH_MS = 10 * 60_000L

	data class Seen(val price: Double, val atMs: Long)
	private val seen = HashMap<String, Seen>()

	@Synchronized
	fun record(cards: List<Pair<String?, AuctionListing?>>, nowMs: Long) {
		val page = HashMap<String, Double>()
		for ((id, l) in cards) {
			if (id == null || l == null || !l.isBin) continue
			val p = l.price?.takeIf { it > 0 } ?: continue
			page[id] = minOf(page[id] ?: p, p)
		}
		for ((id, p) in page) {
			val old = seen[id]
			seen[id] = if (old != null && nowMs - old.atMs <= FRESH_MS && old.price < p) old.copy(atMs = nowMs) else Seen(p, nowMs)
		}
	}

	@Synchronized
	fun bin(id: String, nowMs: Long): Double? = seen[id]?.takeIf { nowMs - it.atMs <= FRESH_MS }?.price

	@Synchronized fun clear() = seen.clear()
}
