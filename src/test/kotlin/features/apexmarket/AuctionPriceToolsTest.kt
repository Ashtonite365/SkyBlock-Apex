package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.AuctionPriceTools

class AuctionPriceToolsTest {
	@Test
	fun helpers() {
		Assertions.assertEquals(100.0, AuctionPriceTools.salesPerDay(400, 300), 1e-9)
		Assertions.assertTrue(AuctionPriceTools.isStillLowest(1000, 1000.0, 1))
		Assertions.assertFalse(AuctionPriceTools.isStillLowest(1001, 500.0, 2))
		Assertions.assertEquals(1999L, AuctionPriceTools.lbinMinusOne(1000.0, 2))
		Assertions.assertNull(AuctionPriceTools.lbinMinusOne(0.5, 1))
	}
}
