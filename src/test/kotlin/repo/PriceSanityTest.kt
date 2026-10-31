package dev.ashtonite365.apex.repo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
class PriceSanityTest {
	@Test fun lapisOutlierUsesAverage() = assertEquals(200_000.0, PriceSanity.guard(15_000.0, 200_000.0))
	@Test fun normalKept() = assertEquals(180_000.0, PriceSanity.guard(180_000.0, 200_000.0))
	@Test fun noAvgKept() = assertEquals(15_000.0, PriceSanity.guard(15_000.0, null))
	@Test fun mapGuarded() = assertEquals(mapOf("LAPIS_PICKAXE" to 200_000.0, "X" to 5.0),
		PriceSanity.guardAll(mapOf("LAPIS_PICKAXE" to 15_000.0, "X" to 5.0)) { if (it == "LAPIS_PICKAXE") 200_000.0 else null })

	@Test fun liveSnapshotRealPriceFlagged() {
		val old = LiveAuctionPrices.avgOf
		try {
			LiveAuctionPrices.avgOf = { if (it == "LAPIS_PICKAXE") 200_000.0 else null }
			val s = LiveAuctionPrices.Snapshot(mapOf("LAPIS_PICKAXE" to 15_000.0), 1000L, false)
			assertEquals(15_000.0, s.bin("LAPIS_PICKAXE", 2000L))
			org.junit.jupiter.api.Assertions.assertTrue(s.suspicious("LAPIS_PICKAXE", 2000L))
		} finally { LiveAuctionPrices.avgOf = old }
	}
}
