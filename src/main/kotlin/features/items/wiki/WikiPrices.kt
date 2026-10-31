// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import dev.ashtonite365.apex.util.bazaarStockOf
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ItemId

object WikiPrices {

	fun interface Bazaar { fun price(id: String, buy: Boolean): Double? }

	fun interface Auction { fun price(id: String, average: Boolean): Double? }

	fun auctionKey(id: String): String = id.substringBefore('+').trim()

	fun price(live: WikiDoc.Live, bz: Bazaar, ah: Auction): Double? {
		fun any(id: String, buy: Boolean): Double? = bz.price(id, buy) ?: ah.price(auctionKey(id), false)
		val v = when (live.kind) {
			"bazaar" -> bz.price(live.id, live.buy)
			"spread" -> { val b = bz.price(live.id, true); val s = bz.price(live.id, false); if (b != null && s != null) b - s else null }
			"auction" -> ah.price(auctionKey(live.id), live.average) ?: if (live.average) ah.price(auctionKey(live.id), false) else null
			"recipe" -> {
				var sum = 0.0
				for ((id, q) in live.entries) sum += (any(id, live.buy) ?: return null) * q
				sum
			}
			else -> null
		}
		return v?.takeIf { it.isFinite() && it >= 0 }
	}

	val Live = WikiLayout.Prices { live ->
		price(live,
			{ id, buy ->
				val q = HypixelStaticData.bazaarData[ItemId.BazaarProduct(id)]?.quickStatus
					?: runCatching { HypixelStaticData.bazaarData[bazaarStockOf(ItemId(id))]?.quickStatus }.getOrNull()
				q?.let { if (buy) it.buyPrice else it.sellPrice }?.takeIf { it > 0 }
			},
			{ id, avg -> (if (avg) HypixelStaticData.avg1dlowestBin else HypixelStaticData.lowestBin)[ItemId(id)]?.takeIf { it > 0 } },
		)
	}
}
