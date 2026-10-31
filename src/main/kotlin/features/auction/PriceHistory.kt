// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.auction

import dev.ashtonite365.apex.util.bazaarStockOf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.ApexPackEncoder
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ItemId

object PriceHistory {

	const val MIN_INTERVAL_SECONDS = 10 * 60L

	@Serializable
	data class Point(val t: Long, val buy: Double, val sell: Double? = null)

	@Serializable
	data class DayFile(val items: MutableMap<String, MutableList<Point>> = mutableMapOf())

	private val format = Json { ignoreUnknownKeys = true }
	private const val FILE_GLOB = "prices_*.json.{apack,gz}"
	val dir: Path get() = Apex.DATA_DIR.resolve("price-history")
	private val fileLock = Any()

	private var auctionsAt = 0L
	private var bazaarAt = 0L

	internal fun encode(day: DayFile): String = format.encodeToString(DayFile.serializer(), day)
	internal fun decode(text: String): DayFile = format.decodeFromString(DayFile.serializer(), text)

	private fun dayFile(folder: Path, date: LocalDate): Path = folder.resolve("prices_$date.json.apack")

	private fun legacyDayFile(folder: Path, date: LocalDate): Path = folder.resolve("prices_$date.json.gz")

	internal fun pack(text: String): ByteArray {
		val bytes = text.encodeToByteArray()
		return ApexPackEncoder.compress(bytes, 0, bytes.size, ApexPackEncoder.Options.fast())
	}

	internal fun unpack(bytes: ByteArray): String =
		if (ApexPack.isApexPack(bytes, 0, bytes.size)) ApexPack.decompress(bytes).decodeToString()
		else GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }

	private fun load(file: Path): DayFile? = try {
		decode(unpack(Files.readAllBytes(file)))
	} catch (e: Exception) {
		Apex.logger.warn("Discarding unreadable price history file $file", e)
		runCatching { Files.deleteIfExists(file) }
		null
	}

	private fun save(file: Path, day: DayFile) {
		Files.createDirectories(file.parent)
		val partial = file.resolveSibling(file.name + ".tmp")
		Files.write(partial, pack(encode(day)))
		Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	private fun dropExpired(folder: Path, nowMs: Long, keepDays: Int) {
		if (!folder.exists()) return
		val oldest = nowMs - keepDays * 86_400_000L
		folder.listDirectoryEntries(FILE_GLOB).filter { it.getLastModifiedTime().toMillis() < oldest }
			.forEach { runCatching { Files.deleteIfExists(it) } }
	}

	internal fun append(folder: Path, samples: Map<String, Point>, now: Instant, keepDays: Int) {
		if (samples.isEmpty()) return
		synchronized(fileLock) {
			dropExpired(folder, now.toEpochMilli(), keepDays)
			val date = LocalDate.ofInstant(now, ZoneOffset.UTC)
			val file = dayFile(folder, date)
			val legacy = legacyDayFile(folder, date)
			val day = (file.takeIf { it.exists() } ?: legacy.takeIf { it.exists() })?.let(::load) ?: DayFile()
			samples.forEach { (key, point) -> day.items.getOrPut(key) { mutableListOf() } += point }
			save(file, day)
			runCatching { Files.deleteIfExists(legacy) }
		}
	}

	internal fun readAll(folder: Path, key: String): List<Point> {
		if (!folder.exists()) return emptyList()
		val found = synchronized(fileLock) { folder.listDirectoryEntries(FILE_GLOB).flatMap { load(it)?.items?.get(key).orEmpty() } }
		return found.sortedBy { it.t }
	}

	private inline fun recordEvery(last: Long, setLast: (Long) -> Unit, what: String, samples: (Long) -> Map<String, Point>) {
		if (!AuctionTools.TConfig.recordPriceHistory) return
		val now = Instant.now()
		val seconds = now.epochSecond
		if (seconds - last < MIN_INTERVAL_SECONDS) return
		setLast(seconds)
		runCatching { append(dir, samples(seconds), now, AuctionTools.TConfig.priceHistoryRetention) }
			.onFailure { Apex.logger.warn("Could not record $what price history", it) }
	}

	fun onAuctionPrices(lowestBin: Map<ItemId, Double>) =
		recordEvery(auctionsAt, { auctionsAt = it }, "auction") { t -> lowestBin.entries.associate { it.key.skyblockId to Point(t, it.value) } }

	fun onBazaarPrices(bazaar: Map<ItemId.BazaarProduct, HypixelStaticData.BazaarData>) =
		recordEvery(bazaarAt, { bazaarAt = it }, "bazaar") { t ->
			bazaar.values.associate { "BZ:" + it.productId.stockKey to Point(t, it.quickStatus.buyPrice, it.quickStatus.sellPrice) }
		}

	fun keyFor(id: ItemId): String {
		val stock = bazaarStockOf(id)
		return if (HypixelStaticData.hasBazaarStock(stock)) "BZ:" + stock.stockKey else id.skyblockId
	}

	fun load(key: String): List<Point> = readAll(dir, key)
}
