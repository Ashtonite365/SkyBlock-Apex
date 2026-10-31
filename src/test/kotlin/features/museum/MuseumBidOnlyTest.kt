// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.museum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.museum.CheapestList
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.features.museum.MuseumHelper.Missing
import dev.ashtonite365.apex.util.ItemId

class MuseumBidOnlyTest {
	private val fmt: (Double) -> String = { "${Math.round(it / 1000)}k" }
	private fun row(name: String, id: String, bin: Double? = null, bid: Double? = null, stale: Double? = null) =
		Missing(name, listOf(ItemId(id)), "Special", 0, { bin }, { bid }, { stale }, { false })

	@Test fun bidOnlyWithBidShowsGreyBidEstimate() {
		val r = row("Frosty the Snow Cannon", "FROSTY_THE_SNOW_CANNON", bid = 2_500_000.0)
		assertTrue(r.bidOnly); assertTrue(r.estimate); assertNull(r.price)
		assertEquals("§7~2500k (bid)", MuseumHelper.priceText(r, fmt))
	}

	@Test fun bidOnlyWithoutBidShowsNoNumber() {
		val r = row("Frosty the Snow Cannon", "FROSTY_THE_SNOW_CANNON")
		assertTrue(r.bidOnly); assertTrue(r.estimate); assertNull(r.bidQuote)
		assertEquals("§7bid only", MuseumHelper.priceText(r, fmt))
	}

	@Test fun bazaarItemIsNotBidOnly() {
		val r = Missing("Rotten Flesh", listOf(ItemId("ROTTEN_FLESH")), "x", 0, { null }, { null }, { null }, { true })
		assertFalse(r.bidOnly)
		assertEquals(emptyList<Missing>(), MuseumHelper.listedOnly(listOf(r), MuseumHelper.Sort.CHEAPEST))
	}

	@Test fun bidOnlySortsWithStaleRows() {
		val live = row("Live", "LIVE", bin = 900_000.0)
		val stale = row("Stale", "STALE", stale = 50_000.0)
		val bid = row("Bid", "BID", bid = 10_000.0)
		val none = row("Frosty", "FROSTY")
		for (sort in MuseumHelper.Sort.entries) {
			val l = MuseumHelper.listedOnly(listOf(none, bid, stale, live), sort)
			assertEquals(listOf("Live", "Bid", "Stale", "Frosty"), l.map { it.name })
		}
	}

	@Test fun buyStillOpensAhSearch() {
		assertEquals("ahs Frosty the Snow Cannon", MuseumHelper.buyCommand(row("§6Frosty the Snow Cannon", "FROSTY")))
	}

	@Test fun truncationAddsEllipsisAndTooltip() {
		val measure: (String) -> Int = { it.length * 6 }
		val full = "Frosty the Snow Cannon"
		val shown = CheapestList.truncate(full, 20 * 6, measure)
		assertTrue(shown.endsWith("…")); assertTrue(measure(shown) <= 120)
		assertEquals("Frosty the Snow Can…", shown)
		assertEquals(full, CheapestList.tooltip(full, shown))
		assertEquals(full, CheapestList.truncate(full, 1000, measure))
		assertNull(CheapestList.tooltip(full, full))
	}
}
