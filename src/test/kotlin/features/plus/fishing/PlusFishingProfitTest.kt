package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingProfit
import dev.ashtonite365.apex.features.plus.fishing.PlusInventoryGains

class PlusFishingProfitTest {
	@Test
	fun gains() {
		assertEquals(mapOf("A" to 2L, "C" to 1L), PlusInventoryGains.gains(mapOf("A" to 1L, "B" to 5L), mapOf("A" to 3L, "B" to 4L, "C" to 1L)))
	}

	@Test
	fun rod() {
		assertTrue(PlusFishingProfit.isRod("ROD_OF_THE_SEA"))
		assertTrue(PlusFishingProfit.isRod("CHALLENGE_ROD"))
		assertFalse(PlusFishingProfit.isRod("RAW_FISH"))
	}
}
