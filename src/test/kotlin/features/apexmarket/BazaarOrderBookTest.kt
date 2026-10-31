package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.BazaarOrderBook
import dev.ashtonite365.apex.repo.HypixelStaticData.BazaarOrderLevel

class BazaarOrderBookTest {
	private val bids = listOf(BazaarOrderLevel(100, 10.0), BazaarOrderLevel(200, 9.5), BazaarOrderLevel(300, 9.0))
	private val asks = listOf(BazaarOrderLevel(50, 11.0), BazaarOrderLevel(150, 11.5))

	@Test
	fun buyOrderQueuesBehindEqualOrBetter() {
		val e = BazaarOrderBook.estimate(true, 9.5, 0, bids, asks, 168 * 100)
		Assertions.assertEquals(300, e.queueAhead)
		Assertions.assertEquals(100.0, e.ratePerHour, 1e-9)
		Assertions.assertEquals(3.0, e.etaHours!!, 1e-9)
		Assertions.assertFalse(e.wouldInstaFill)
	}

	@Test
	fun topBidAndAmount() {
		val e = BazaarOrderBook.estimate(true, 10.1, 50, bids, asks, 168 * 100)
		Assertions.assertEquals(0, e.queueAhead)
		Assertions.assertEquals(0.5, e.etaHours!!, 1e-9)
	}

	@Test
	fun sellOfferAndCrossing() {
		val e = BazaarOrderBook.estimate(false, 11.5, 0, bids, asks, 0)
		Assertions.assertEquals(200, e.queueAhead)
		Assertions.assertNull(e.etaHours)
		Assertions.assertTrue(BazaarOrderBook.estimate(false, 10.0, 0, bids, asks, 1000).wouldInstaFill)
		Assertions.assertEquals(0.0, BazaarOrderBook.estimate(true, 11.0, 0, bids, asks, 1000).etaHours)
	}

	@Test
	fun parseAndFormat() {
		Assertions.assertEquals(1500.0, BazaarOrderBook.parsePrice("1.5k"))
		Assertions.assertEquals(1234.5, BazaarOrderBook.parsePrice("1,234.5"))
		Assertions.assertNull(BazaarOrderBook.parsePrice("abc"))
		Assertions.assertEquals("30m", BazaarOrderBook.formatEta(0.5))
		Assertions.assertEquals("2.0d", BazaarOrderBook.formatEta(48.0))
	}
}

class BazaarWarningsTest {
	@Test
	fun liquidity() {
		Assertions.assertEquals(10.0, BazaarOrderBook.spreadPercent(100.0, 110.0)!!, 1e-9)
		Assertions.assertEquals(2, BazaarOrderBook.liquidityIssues(10, 50.0, 1000, 15).size)
		Assertions.assertTrue(BazaarOrderBook.liquidityIssues(5000, 10.0, 1000, 15).isEmpty())
	}

	@Test
	fun selfCompetition() {
		val own = listOf(BazaarOrderBook.OwnOrder("A", true, 10.0, false), BazaarOrderBook.OwnOrder("A", false, 12.0, false),
			BazaarOrderBook.OwnOrder("A", true, 9.0, true))
		val w = BazaarOrderBook.selfCompetition(own, "A", true, 10.5)
		Assertions.assertEquals(listOf("Undercuts your own order at 10"), w.map { it.replace(".0", "") })
		Assertions.assertTrue(BazaarOrderBook.selfCompetition(own, "A", false, 13.0)[0].startsWith("Competes"))
		Assertions.assertTrue(BazaarOrderBook.selfCompetition(own, "B", true, 1.0).isEmpty())
	}
}
