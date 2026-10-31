package dev.ashtonite365.apex.test.features.plus.foraging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.foraging.PlusForagingProfit

class PlusForagingProfitTest {
	@Test
	fun axe() {
		assertTrue(PlusForagingProfit.isAxe("TREECAPITATOR_AXE"))
		assertFalse(PlusForagingProfit.isAxe("DIAMOND_PICKAXE"))
	}
}
