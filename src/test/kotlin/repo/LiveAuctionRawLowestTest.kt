package dev.ashtonite365.apex.repo

import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.util.ItemId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LiveAuctionRawLowestTest {
	private val now = java.time.OffsetDateTime.parse("2026-10-16T10:05:00+00:00").toInstant().toEpochMilli()
	private fun v(lowest: Double, raw: Double, at: String = "2026-10-16T10:02:26.757462+00:00") =
		"""[{"variantKey":"","variedBy":{},"lowest":$lowest,"lowestVolume":20,"lowest3Day":10,"last":$lowest,"rawLowest":$raw,"calculatedAt":"$at"}]"""
	private val feed = """{"items":{
		"RABBIT_HAT":${v(50_000.0, 99_999.0)},
		"SMALL_HUNT_TRAP":${v(94_000.0, 100_000.0)},
		"DUNGEON_ORB":${v(100_000.0, 80_000.0)},
		"OBSIDIAN_CHESTPLATE":${v(100_000.0, 220_000.0)},
		"OLD_ITEM":${v(100_000.0, 70_000.0, "2026-10-16T08:00:00+00:00")},
		"NO_RAW":[{"lowest":100000,"lowestVolume":3}]}}"""
	private fun snap(fetchedAt: Long = now, failed: Boolean = false) =
		LiveAuctionPrices.next(LiveAuctionPrices.EMPTY, Result.success(feed), fetchedAt).copy(failed = failed)
	private fun withAvg(block: () -> Unit) {
		val old = LiveAuctionPrices.avgOf
		try { LiveAuctionPrices.avgOf = { 100_000.0 }; block() } finally { LiveAuctionPrices.avgOf = old }
	}

	@Test fun allFourCasesShowTheRealCheapestListing() = withAvg {
		val s = snap()
		assertEquals(99_999.0, s.bin("RABBIT_HAT", now))
		assertEquals(100_000.0, s.bin("SMALL_HUNT_TRAP", now))
		assertEquals(80_000.0, s.bin("DUNGEON_ORB", now))
		assertEquals(220_000.0, s.bin("OBSIDIAN_CHESTPLATE", now))
		assertNull(s.bin("NO_RAW", now), "no rawLowest = no price, never the smoothed 'lowest'")
	}

	@Test fun museumRowsUseLivePriceAndSortByIt() = withAvg {
		val s = snap()
		val rows = listOf("RABBIT_HAT", "SMALL_HUNT_TRAP", "DUNGEON_ORB", "OBSIDIAN_CHESTPLATE").map { id ->
			MuseumHelper.Missing(id, listOf(ItemId(id)), "x", 0, { s.bin(it.skyblockId, now) }, { null }, { s.staleBin(it.skyblockId, now) })
		}
		val sorted = MuseumHelper.listedOnly(rows, MuseumHelper.Sort.CHEAPEST)
		assertEquals(listOf(80_000.0, 99_999.0, 100_000.0, 220_000.0), sorted.map { it.price })
		assertEquals("§6100k §7BIN", MuseumHelper.priceText(sorted[1]) { "${Math.round(it / 1000)}k" })
	}

	@Test fun staleDataIsMarkedNeverShownAsCurrent() = withAvg {
		val old = snap(fetchedAt = now - 20 * 60_000L)
		assertNull(old.bin("RABBIT_HAT", now))
		assertEquals(99_999.0, old.staleBin("RABBIT_HAT", now))
		val failed = snap(failed = true)
		assertNull(failed.bin("OBSIDIAN_CHESTPLATE", now))

		assertNull(snap().bin("OLD_ITEM", now))
		val row = MuseumHelper.Missing("Rabbit Hat", listOf(ItemId("RABBIT_HAT")), "x", 0,
			{ old.bin(it.skyblockId, now) }, { null }, { old.staleBin(it.skyblockId, now) })
		assertNull(row.price)
		assertEquals("§8~100k estimate", MuseumHelper.priceText(row) { "${Math.round(it / 1000)}k" })
		assertEquals(listOf(row), MuseumHelper.listedOnly(listOf(row), MuseumHelper.Sort.CHEAPEST))
	}
}
