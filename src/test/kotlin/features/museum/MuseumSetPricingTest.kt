// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.museum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.museum.MuseumAhTarget
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.features.museum.MuseumHelper.Missing
import dev.ashtonite365.apex.features.museum.MuseumHelper.PieceStatus
import dev.ashtonite365.apex.util.ItemId

class MuseumSetPricingTest {
	private val fmt: (Double) -> String = { "${Math.round(it / 1000)}k" }
	private val ids = listOf("X_HELMET", "X_CHESTPLATE", "X_LEGGINGS", "X_BOOTS")
	private fun set(live: Map<String, Double>, stale: Map<String, Double> = emptyMap(), low: Map<String, Double> = emptyMap(),
	                bazaar: Set<String> = emptySet()) =
		Missing("X Armor", ids.map { ItemId(it) }, "Armor Sets", 0, { live[it.skyblockId] }, { null }, { stale[it.skyblockId] },
			{ it.skyblockId in bazaar }, { low[it.skyblockId] }, { false }, { it.skyblockId.lowercase() })

	@Test fun allPiecesLiveIsConfident() {
		val r = set(ids.associateWith { 100_000.0 })
		assertEquals(400_000.0, r.price); assertFalse(r.estimate)
		assertEquals("§6400k §7BIN", MuseumHelper.priceText(r, fmt))
		assertTrue(r.pieces.all { it.status == PieceStatus.LIVE })
	}

	@Test fun lowConfidencePieceMakesSetAnEstimate() {
		val r = set(ids.drop(1).associateWith { 100_000.0 }, low = mapOf("X_HELMET" to 900_000.0))
		assertNull(r.price); assertTrue(r.estimate)
		assertEquals("§8~1200k estimate", MuseumHelper.priceText(r, fmt))
	}

	@Test fun mixedLiveStaleIsKeptAsGreyEstimate() {
		val r = set(mapOf("X_HELMET" to 100_000.0, "X_CHESTPLATE" to 200_000.0), stale = mapOf("X_LEGGINGS" to 150_000.0, "X_BOOTS" to 50_000.0))
		assertTrue(r.estimate); assertEquals(500_000.0, r.setEstimate)
		assertEquals("§8~500k estimate", MuseumHelper.priceText(r, fmt))
		val live = Missing("Live", listOf(ItemId("L")), "x", 0, { 900_000_000.0 }, { null }, { null }, { false }, { null }, { false })
		assertEquals(listOf("Live", "X Armor"), MuseumHelper.listedOnly(listOf(r, live), MuseumHelper.Sort.CHEAPEST).map { it.name })
		val tip = MuseumHelper.rowTooltip(r, "X Armor", fmt) { it.skyblockId }
		assertEquals("X Armor", tip[0])
		assertEquals(listOf("§7X_HELMET: §f100k §8(live)", "§7X_CHESTPLATE: §f200k §8(live)", "§7X_LEGGINGS: §f150k §8(stale)", "§7X_BOOTS: §f50k §8(stale)"), tip.drop(1))
	}

	@Test fun onePieceMissing() {
		val r = set(mapOf("X_HELMET" to 100_000.0, "X_CHESTPLATE" to 200_000.0, "X_BOOTS" to 50_000.0))
		assertEquals(1, r.missingPieces)
		assertEquals("§8~350k+ (1 piece missing)", MuseumHelper.priceText(r, fmt))
		assertEquals(listOf(r), MuseumHelper.listedOnly(listOf(r), MuseumHelper.Sort.CHEAPEST))
		assertEquals("§7X_LEGGINGS: §f— §8(bid-only)", MuseumHelper.rowTooltip(r, "X Armor", fmt) { it.skyblockId }[3])

		val old = MuseumHelper.sendCommand; val sent = mutableListOf<String>()
		MuseumHelper.sendCommand = { sent += it }
		try {
			MuseumHelper.buyAction(r)()
			assertEquals(listOf("ahs x_leggings"), sent)
			assertEquals(ids.toSet(), MuseumAhTarget.ids)
		} finally { MuseumHelper.sendCommand = old; MuseumAhTarget.clear() }
	}

	@Test fun noPricedPieceIsNoLiveListingNotDropped() {
		val r = set(emptyMap(), bazaar = ids.toSet())
		assertEquals("§8no live listing", MuseumHelper.priceText(r, fmt))
		assertEquals(listOf(r), MuseumHelper.listedOnly(listOf(r), MuseumHelper.Sort.CHEAPEST))
	}

	@Test fun allLiveSetBuysCheapestPiece() {
		val r = set(mapOf("X_HELMET" to 100_000.0, "X_CHESTPLATE" to 200_000.0, "X_LEGGINGS" to 30_000.0, "X_BOOTS" to 50_000.0))
		assertEquals("ahs x_leggings", MuseumHelper.buyCommand(r))
	}
}
