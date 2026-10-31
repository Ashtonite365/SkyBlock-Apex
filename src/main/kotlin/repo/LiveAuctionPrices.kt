// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object LiveAuctionPrices {
	const val URL = "https://api.eliteskyblock.com/resources/auctions"

	const val FRESH_MS = 10 * 60_000L

	const val ITEM_FRESH_MS = 30 * 60_000L

	data class Snapshot(val prices: Map<String, Double>, val fetchedAtMs: Long, val failed: Boolean, val error: String? = null,
	                    val observedAtMs: Map<String, Long> = emptyMap(),

	                    val lowConfidence: Set<String> = emptySet(),

	                    val noLive: Set<String> = emptySet()) {
		fun fresh(nowMs: Long) = fetchedAtMs > 0 && !failed && nowMs - fetchedAtMs <= FRESH_MS
		private fun itemFresh(id: String, nowMs: Long) = observedAtMs[id]?.let { nowMs - it <= ITEM_FRESH_MS } ?: true

		fun bin(id: String, nowMs: Long): Double? = normId(id).let { k -> if (k !in lowConfidence && fresh(nowMs) && itemFresh(k, nowMs)) prices[k] else null }

		fun staleBin(id: String, nowMs: Long): Double? = normId(id).let { k -> if (k !in lowConfidence && bin(k, nowMs) == null) prices[k] else null }

		fun lowConfidenceBin(id: String): Double? = normId(id).let { k -> if (k in lowConfidence) prices[k] else null }

		fun noLiveListing(id: String): Boolean = normId(id).let { k -> k in noLive && k !in prices }

		fun suspicious(id: String, nowMs: Long) = PriceSanity.isOutlier(bin(id, nowMs), avgOf(normId(id)))
		fun ageLabel(nowMs: Long): String = when {
			fetchedAtMs <= 0L -> if (failed) "prices unavailable" else "prices loading…"
			failed -> "prices stale (${ageText(nowMs - fetchedAtMs)} old, last fetch failed)"
			!fresh(nowMs) -> "prices stale (${ageText(nowMs - fetchedAtMs)} old)"
			else -> "prices ${ageText(nowMs - fetchedAtMs)} old"
		}
	}

	@Volatile var avgOf: (String) -> Double? = { id ->
		val k = dev.ashtonite365.apex.util.ItemId(id)
		HypixelStaticData.avg1dlowestBin[k] ?: HypixelStaticData.avg3dlowestBin[k]
	}

	val EMPTY = Snapshot(emptyMap(), 0L, false)
	@Volatile var current: Snapshot = EMPTY; private set

	fun ageText(ms: Long): String { val s = maxOf(0L, ms / 1000); return if (s < 60) "${s}s" else if (s < 3600) "${s / 60}m" else "${s / 3600}h" }

	fun normId(id: String) = id.replace(':', '-')

	fun parse(json: String): Map<String, Double> = parseFull(json).first

	fun parseFull(json: String): Pair<Map<String, Double>, Map<String, Long>> = parseAll(json).let { it.prices to it.observedAtMs }

	fun isLowConfidence(rawLowest: Double, volume: Long, lowest3Day: Double?, lowest7Day: Double?): Boolean {
		val ref = lowest3Day?.takeIf { it > 0 } ?: lowest7Day?.takeIf { it > 0 } ?: return false
		return volume <= 1 && rawLowest > 1.5 * ref
	}

	fun parseAll(json: String, nowMs: Long = 0L): Snapshot {
		val items = Json.parseToJsonElement(json).jsonObject["items"]?.jsonObject ?: error("no 'items' in auctions response")
		val out = HashMap<String, Double>(items.size)
		val seen = HashMap<String, Long>(items.size)
		val low = HashSet<String>(); val none = HashSet<String>()
		for ((id, variants) in items) {
			var best: Double? = null; var at: Long? = null; var bestLow = false; var anyMinus = false
			for (v in (variants as? JsonArray).orEmpty()) {
				val o = v as? JsonObject ?: continue

				val lowest = o["rawLowest"]?.jsonPrimitive?.doubleOrNull ?: continue
				if (lowest < 0) { anyMinus = true; continue }
				val vol = o["lowestVolume"]?.jsonPrimitive?.longOrNull ?: 0L
				if (lowest > 0 && vol > 0 && (best == null || lowest < best)) {
					best = lowest
					bestLow = isLowConfidence(lowest, vol, o["lowest3Day"]?.jsonPrimitive?.doubleOrNull, o["lowest7Day"]?.jsonPrimitive?.doubleOrNull)
					at = (o["calculatedAt"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
						?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
				}
			}
			best?.let { out[normId(id)] = it; at?.let { a -> seen[normId(id)] = a }; if (bestLow) low += normId(id) }
			if (best == null && anyMinus) none += normId(id)
		}
		return Snapshot(out, nowMs, false, observedAtMs = seen, lowConfidence = low, noLive = none)
	}

	fun next(old: Snapshot, body: Result<String>, nowMs: Long): Snapshot =
		body.mapCatching { parseAll(it, nowMs) }
			.getOrElse { e -> old.copy(failed = true, error = e.javaClass.simpleName + (e.message?.let { m -> ": $m" } ?: "")) }

	internal fun update(body: Result<String>, nowMs: Long) { current = next(current, body, nowMs) }
}
