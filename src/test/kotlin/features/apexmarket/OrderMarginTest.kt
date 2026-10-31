package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.FlipHistory
import dev.ashtonite365.apex.features.apexmarket.OrderMargin

class OrderMarginTest {
	@Test
	fun summary() {
		val orders = listOf(OrderMargin.OpenOrder("Coal", "COAL", true, 100, 2.0),
			OrderMargin.OpenOrder("Iron", "IRON_INGOT", false, 10, 10.0),
			OrderMargin.OpenOrder("Gold", "GOLD_INGOT", false, 1, 5.0))
		val s = OrderMargin.summarize(orders, 0, { if (it == "COAL") 3.0 else null }, { item, _ -> if (item == "Iron") 60.0 else null })
		Assertions.assertEquals(200.0, s.lockedInBuys, 1e-9)
		Assertions.assertEquals(100.0, s.projectedBuyMargin, 1e-9)
		Assertions.assertEquals(105.0, s.sellProceeds, 1e-9)
		Assertions.assertEquals(40.0, s.knownSellProfit, 1e-9)
		Assertions.assertEquals(1, s.unknownCostSells)
	}

	@Test
	fun peek() {
		val lots = listOf(FlipHistory.Lot(5, 1.0), FlipHistory.Lot(5, 3.0))
		Assertions.assertEquals(11.0, OrderMargin.peekCost(lots, 7)!!, 1e-9)
		Assertions.assertNull(OrderMargin.peekCost(lots, 11))
		Assertions.assertEquals(5, lots[0].amount)
	}
}
