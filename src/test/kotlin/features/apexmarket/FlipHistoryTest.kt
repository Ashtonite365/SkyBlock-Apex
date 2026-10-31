package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.FlipHistory

class FlipHistoryTest {
	@Test
	fun fifoMatchAndProfit() {
		val d = FlipHistory.Data()
		FlipHistory.recordBuy(d, "Coal", 10, 2.0)
		FlipHistory.recordBuy(d, "Coal", 10, 4.0)
		val f = FlipHistory.recordSale(d, "Coal", 15, 5.0, "BZ", 0, 10)!!
		Assertions.assertEquals(15, f.amount)
		Assertions.assertEquals(15 * 5.0 - (20.0 + 20.0), f.profit, 1e-9)
		Assertions.assertEquals(5, d.lots["Coal"]!!.single().amount)
		Assertions.assertNull(FlipHistory.recordSale(d, "Iron", 1, 1.0, "AH", 0, 10))
	}

	@Test
	fun historyIsCapped() {
		val d = FlipHistory.Data()
		repeat(5) { FlipHistory.recordBuy(d, "X", 1, 1.0); FlipHistory.recordSale(d, "X", 1, 2.0, "AH", it.toLong(), 3) }
		Assertions.assertEquals(3, d.flips.size)
		Assertions.assertEquals(4L, d.flips.first().time)
		Assertions.assertFalse(d.lots.containsKey("X"))
	}
}
