// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import dev.ashtonite365.apex.util.bazaarStockOf
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import java.net.http.HttpResponse
import java.nio.file.Files
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.apis.CollectionResponse
import dev.ashtonite365.apex.apis.CollectionSkillData
import dev.ashtonite365.apex.features.auction.PriceHistory
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.net.HttpUtil
import dev.ashtonite365.apex.util.net.NetRetry

object HypixelStaticData {
	private val logger: Logger = LogManager.getLogger("Apex.HypixelStaticData")
	private const val HYPIXEL = "https://api.hypixel.net"
	private const val LBIN_API = "https://api.eliteskyblock.com"

	var lowestBin: Map<ItemId, Double> = emptyMap()

	@Volatile var lowestBinFetchedAtMs = 0L
	var avg1dlowestBin: Map<ItemId, Double> = emptyMap()
	var avg3dlowestBin: Map<ItemId, Double> = emptyMap()
	var avg7dlowestBin: Map<ItemId, Double> = emptyMap()

	fun hasAuctionHouseOffers(item: ItemId): Boolean = item in lowestBin

	@Volatile var bazaarData: Map<ItemId.BazaarProduct, BazaarData> = emptyMap()
		private set

	fun hasBazaarStock(item: ItemId.BazaarProduct): Boolean = item in bazaarData

	fun getPriceOfItem(item: ItemId): Double? {
		val bazaar = bazaarData[bazaarStockOf(item)]
		return bazaar?.quickStatus?.buyPrice ?: lowestBin[item]
	}

	@Serializable
	data class BazaarData(@SerialName("product_id") val productId: ItemId.BazaarProduct,
		@SerialName("quick_status") val quickStatus: BazaarStatus,

		@SerialName("sell_summary")
		val sellSummary: List<BazaarOrderLevel> = emptyList(),

		@SerialName("buy_summary")
		val buySummary: List<BazaarOrderLevel> = emptyList(),
	)

	@Serializable
	data class BazaarOrderLevel(val amount: Long, val pricePerUnit: Double, val orders: Long = 0)

	@Serializable
	data class BazaarStatus(val sellPrice: Double, val sellVolume: Long, val sellMovingWeek: Long, val sellOrders: Long, val buyPrice: Double, val buyVolume: Long, val buyMovingWeek: Long, val buyOrders: Long)

	@Serializable
	private data class BazaarResponse(val success: Boolean, val products: Map<ItemId.BazaarProduct, BazaarData> = emptyMap())

	private val bazaarCacheFile get() = Apex.DATA_DIR.resolve("bazaar-cache.json")

	private fun saveBazaarCache(r: BazaarResponse) {
		runCatching { Files.writeString(bazaarCacheFile, Apex.json.encodeToString(BazaarResponse.serializer(), r)) }
			.onFailure { logger.warn("Could not save the bazaar cache: $it") }
	}

	private fun loadBazaarCache() {
		val f = bazaarCacheFile
		if (!Files.isRegularFile(f)) return
		runCatching {
			val r = Apex.json.decodeFromString(BazaarResponse.serializer(), Files.readString(f))
			if (bazaarData.isEmpty() && r.products.isNotEmpty()) {
				bazaarData = r.products
				logger.info("Bazaar prices: ${r.products.size} products from the cache")
			}
		}.onFailure { logger.warn("Could not read the bazaar cache: $it") }
	}

	@Volatile var npcSellPrices: Map<String, Double> = emptyMap()
	fun npcSellPrice(item: ItemId): Double? = npcSellPrices[item.skyblockId]

	@Serializable
	data class ItemsResponse(val success: Boolean, val items: List<ApiItem> = emptyList())
	@Serializable
	data class ApiItem(val id: String, @kotlinx.serialization.SerialName("npc_sell_price") val npcSellPrice: Double? = null, val skin: ApiSkin? = null)
	@Serializable
	data class ApiSkin(val value: String? = null, val signature: String? = null)

	private suspend fun fetchNpcSellPrices() {
		val r = HttpUtil.request("$HYPIXEL/v2/resources/skyblock/items").forJson<ItemsResponse>().await()
		runCatching { OfficialItemSkins.onFetched(OfficialItemSkins.fromResponse(r)) }
			.onFailure { logger.warn("[Apex] official item skins could not be applied: $it") }
		val m = r.items.mapNotNull { item -> item.npcSellPrice?.let { item.id to it } }.toMap()
		if (m.isEmpty()) error("items response has no NPC prices (success=${r.success})")
		npcSellPrices = m
		logger.info("NPC sell prices: ${m.size} items")
	}

	var collectionData: Map<String, CollectionSkillData> = emptyMap()
		private set

	enum class CollectionDataState { MISSING, LOADING, LOADED, FAILED }

	@Volatile
	var collectionDataState = CollectionDataState.MISSING
		private set

	fun requestCollectionData() {
		synchronized(this) {
			if (collectionDataState == CollectionDataState.LOADING || collectionDataState == CollectionDataState.LOADED) return
			collectionDataState = CollectionDataState.LOADING
		}
		Apex.coroutineScope.launch { refreshCollections() }
	}

	fun spawnDataCollectionLoop(): Unit {
		loadBazaarCache()
		runCatching { OfficialItemSkins.loadCache() }.onFailure { logger.warn("Could not read the official skins cache: $it") }
		logger.info("Downloading collection data")
		requestCollectionData()
		Apex.coroutineScope.launch {
			while (isActive) {
				logger.info("Refreshing auction prices")
				val ok = NetRetry.attempt(logger, "auction house prices") { fetchAuctionHousePrices() } != null
				NetRetry.attempt(logger, "live auction prices") { fetchLiveAuctionPrices() }
				delay(if (ok) 2.minutes else 1.minutes)
			}
		}
		Apex.coroutineScope.launch {
			while (isActive) {
				if (NetRetry.attempt(logger, "NPC sell prices") { fetchNpcSellPrices() } != null) break
				delay(1.minutes)
			}
		}
		Apex.coroutineScope.launch {
			while (isActive) {
				logger.info("Refreshing bazaar prices")
				val ok = NetRetry.attempt(logger, "bazaar prices") { fetchBazaarPrices() } != null
				delay(if (ok) 2.minutes else 1.minutes)
			}
		}
	}

	@Volatile private var liveRefreshAtMs = 0L

	fun refreshLiveAuctionsNow(nowMs: Long = System.currentTimeMillis()) {
		synchronized(this) {
			if (liveRefreshAtMs != 0L && nowMs - liveRefreshAtMs < 30_000L) return
			liveRefreshAtMs = nowMs
		}
		Apex.coroutineScope.launch { NetRetry.attempt(logger, "live auction prices (on open)", tries = 1) { fetchLiveAuctionPrices() } }
	}

	private suspend fun fetchLiveAuctionPrices() {
		val body = runCatching { HttpUtil.request(LiveAuctionPrices.URL).forBody(HttpResponse.BodyHandlers.ofString()).await() }
		LiveAuctionPrices.update(body, System.currentTimeMillis())
		body.onFailure { logger.warn("Live auction prices failed: $it") }.getOrThrow()
	}

	private suspend fun binTable(path: String): Map<ItemId, Double> =
		HttpUtil.request("$LBIN_API/resources/auctions/neu$path").forJson<Map<ItemId, Double>>().await()

	private suspend fun fetchAuctionHousePrices(): Unit {
		val lowest = binTable("")
		lowestBin = lowest
		lowestBinFetchedAtMs = System.currentTimeMillis()
		PriceHistory.onAuctionPrices(lowest)

		val avg = listOf("/average-lbin/1day", "/average-lbin/3day", "/average-lbin/7day").map { runCatching { binTable(it) }.getOrNull() }
		avg[0]?.let { avg1dlowestBin = it }
		avg[1]?.let { avg3dlowestBin = it }
		avg[2]?.let { avg7dlowestBin = it }
	}

	private suspend fun fetchBazaarPrices(): Unit {
		val response = HttpUtil.request("$HYPIXEL/skyblock/bazaar").forJson<BazaarResponse>().await()
		if (!response.success) error("Bazaar API answered success=false")
		if (response.products.isEmpty()) error("bazaar response has no products (success=${response.success})")
		bazaarData = response.products
		logger.info("Bazaar prices: ${response.products.size} products")
		PriceHistory.onBazaarPrices(response.products)
		saveBazaarCache(response)
	}

	private suspend fun refreshCollections() {
		try {
			updateCollectionData0()
		} catch (e: kotlinx.coroutines.CancellationException) {
			collectionDataState = CollectionDataState.MISSING
			throw e
		} catch (e: Exception) {
			logger.error("Could not download collection data", e)
			collectionDataState = CollectionDataState.FAILED
		}
	}

	private suspend fun updateCollectionData0() {
		val answer: CollectionResponse = HttpUtil.request("$HYPIXEL/resources/skyblock/collections").forJson<CollectionResponse>().await()
		if (!answer.success) error("Collections API answered success=false")
		collectionData = answer.collections
		collectionDataState = CollectionDataState.LOADED
		logger.info("Downloaded ${collectionData.values.sumOf { skill -> skill.items.size }} collections")
	}
}
