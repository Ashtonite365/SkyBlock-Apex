package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.repo.HypixelStaticData

enum class BazaarPriceStrategy {

	BUY_ORDER,

	SELL_ORDER,
	NPC_SELL;

	fun getSellPrice(catalogItemId: ItemId): Double {
		val quote = HypixelStaticData.bazaarData[catalogItemId.bazaarProduct]?.quickStatus ?: return 0.0
		if (this == NPC_SELL) TODO()
		return if (this == BUY_ORDER) quote.sellPrice else quote.buyPrice
	}
}
