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
import dev.ashtonite365.apex.repo.LiveAuctionPrices
import dev.ashtonite365.apex.util.ItemId

class MuseumExactIdTest {
	private val now = 1_800_000_000_000L
	private val at = java.time.Instant.ofEpochMilli(now).toString()
	private fun v(raw: Number, vol: Int, d3: Number? = null, d7: Number? = null) =
		"""[{"rawLowest":$raw,"lowestVolume":$vol${d3?.let { ",\"lowest3Day\":$it" } ?: ""}${d7?.let { ",\"lowest7Day\":$it" } ?: ""},"calculatedAt":"$at"}]"""
	private val json = """{"items":{
		"TITANIUM_PICKAXE":${v(900000, 1, 490000, 490000)},
		"RUSTY_TITANIUM_PICKAXE":${v(450000, 23, 2521)},
		"REFINED_TITANIUM_PICKAXE":${v(-1, 0, 1299000, 800000)},
		"ONLY_7D":${v(900000, 1, null, 500000)},
		"MANY":${v(900000, 5, 490000)}
	}}"""
	private val snap = LiveAuctionPrices.parseAll(json, now)
	private val fmt: (Double) -> String = { "${Math.round(it / 1000)}k" }
	private fun row(name: String, id: String, s: LiveAuctionPrices.Snapshot = snap) =
		Missing(name, listOf(ItemId(id)), "Mining", 0, { s.bin(it.skyblockId, now) }, { null }, { s.staleBin(it.skyblockId, now) }, { false },
			{ s.lowConfidenceBin(it.skyblockId) }, { s.noLiveListing(it.skyblockId) })

	@Test fun lowConfidenceSingleListing() {
		assertTrue(LiveAuctionPrices.isLowConfidence(900_000.0, 1, 490_000.0, 490_000.0))
		assertFalse(LiveAuctionPrices.isLowConfidence(700_000.0, 1, 490_000.0, null))
		assertFalse(LiveAuctionPrices.isLowConfidence(900_000.0, 2, 490_000.0, null))
		assertTrue(LiveAuctionPrices.isLowConfidence(900_000.0, 1, null, 500_000.0))
		assertNull(snap.bin("TITANIUM_PICKAXE", now)); assertNull(snap.staleBin("TITANIUM_PICKAXE", now))
		assertEquals(900_000.0, snap.lowConfidenceBin("TITANIUM_PICKAXE"))
		assertEquals(900_000.0, snap.lowConfidenceBin("ONLY_7D"))
		assertEquals(900_000.0, snap.bin("MANY", now))
		val r = row("Titanium Pickaxe", "TITANIUM_PICKAXE")
		assertTrue(r.estimate); assertNull(r.price)
		assertEquals("§8~900k estimate (1 listing)", MuseumHelper.priceText(r, fmt))
		val live = row("Many", "MANY")
		assertEquals(listOf("Many", "Titanium Pickaxe"), MuseumHelper.listedOnly(listOf(r, live), MuseumHelper.Sort.CHEAPEST).map { it.name })
	}

	@Test fun rawLowestMinusOneIsNoLiveListing() {
		assertTrue(snap.noLiveListing("REFINED_TITANIUM_PICKAXE"))
		assertNull(snap.bin("REFINED_TITANIUM_PICKAXE", now)); assertNull(snap.staleBin("REFINED_TITANIUM_PICKAXE", now))
		val r = row("Refined Titanium Pickaxe", "REFINED_TITANIUM_PICKAXE")
		assertTrue(r.noLive); assertFalse(r.bidOnly)
		assertEquals("§8no live listing", MuseumHelper.priceText(r, fmt))
		assertFalse(MuseumHelper.priceText(r, fmt).contains("-1"))
		assertEquals(listOf(r), MuseumHelper.listedOnly(listOf(r), MuseumHelper.Sort.CHEAPEST))
	}

	@Test fun rustyDataNeverFeedsTitaniumRow() {

		val onlyRusty = LiveAuctionPrices.parseAll("""{"items":{"RUSTY_TITANIUM_PICKAXE":${v(450000, 23, 2521)}}}""", now)
		val r = row("Titanium Pickaxe", "TITANIUM_PICKAXE", onlyRusty)
		assertNull(r.price); assertNull(r.staleQuote); assertNull(r.lowConfQuote)
		assertNull(onlyRusty.bin("TITANIUM_PICKAXE", now))
		assertEquals(450_000.0, onlyRusty.bin("RUSTY_TITANIUM_PICKAXE", now))
	}

	@Test fun ahListingsMatchByExactIdOnly() {
		val t = setOf("TITANIUM_PICKAXE")
		assertTrue(MuseumAhTarget.matches("TITANIUM_PICKAXE", t))
		assertFalse(MuseumAhTarget.matches("RUSTY_TITANIUM_PICKAXE", t))
		assertFalse(MuseumAhTarget.matches("REFINED_TITANIUM_PICKAXE", t))
		assertFalse(MuseumAhTarget.matches(null, t))
		val old = MuseumAhTarget.now
		try {
			var clock = now; MuseumAhTarget.now = { clock }
			MuseumAhTarget.clear()
			assertFalse(MuseumAhTarget.blocked("RUSTY_TITANIUM_PICKAXE"))
			MuseumAhTarget.set(t)
			assertTrue(MuseumAhTarget.blocked("RUSTY_TITANIUM_PICKAXE"))
			assertTrue(MuseumAhTarget.blocked(null))
			assertFalse(MuseumAhTarget.blocked("TITANIUM_PICKAXE"))
			clock += MuseumAhTarget.TTL_MS + 1
			assertFalse(MuseumAhTarget.blocked("RUSTY_TITANIUM_PICKAXE"))
		} finally { MuseumAhTarget.now = old; MuseumAhTarget.clear() }
	}

	@Test fun museumBuySetsExactTarget() {
		val old = MuseumHelper.sendCommand; val sent = mutableListOf<String>()
		MuseumHelper.sendCommand = { sent += it }
		try {
			MuseumHelper.buyAction(row("Titanium Pickaxe", "TITANIUM_PICKAXE"))()
			assertEquals(listOf("ahs Titanium Pickaxe"), sent)
			assertEquals(setOf("TITANIUM_PICKAXE"), MuseumAhTarget.ids)
		} finally { MuseumHelper.sendCommand = old; MuseumAhTarget.clear() }
	}
}
