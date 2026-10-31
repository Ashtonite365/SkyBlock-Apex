package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.FlipFinder
import dev.ashtonite365.apex.util.ItemId

class FlipFinderTest {
	@Test
	fun binFlipsRespectTaxAndMin() {
		val l = mapOf(ItemId("A") to 1000.0, ItemId("B") to 100.0)
		val a = mapOf(ItemId("A") to 2000.0, ItemId("B") to 101.0)
		val f = FlipFinder.binFlips(l, a, 2, 50.0)
		Assertions.assertEquals(listOf("A"), f.map { it.id })
		Assertions.assertEquals(960.0, f[0].profit, 1e-6)
	}

	@Test
	fun bzMarginFiltersVolume() {
		val q = listOf(FlipFinder.BzQuote("X", 110.0, 100.0, 50_000), FlipFinder.BzQuote("Y", 200.0, 100.0, 10))
		val f = FlipFinder.bzFlips(q, 11, 1000)
		Assertions.assertEquals(listOf("X"), f.map { it.id })
		Assertions.assertEquals(110 * 0.989 - 100, f[0].profit, 1e-6)
	}

	@Test
	fun craftSellAppliesBazaarTaxOnBazaarItems() {

		Assertions.assertEquals(989.0, FlipFinder.craftSellNet(1000.0, 5000.0, 2, 11)!!, 1e-9)

		Assertions.assertEquals(4900.0, FlipFinder.craftSellNet(null, 5000.0, 2, 11)!!, 1e-9)
		Assertions.assertNull(FlipFinder.craftSellNet(null, null, 2, 11))
	}
}
