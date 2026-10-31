package dev.ashtonite365.apex.test.features.plus.hunting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.hunting.PlusHuntingProfit

class PlusHuntingProfitTest {
	@Test
	fun shard() {
		assertEquals("SHARD_BIRRIES" to 1L, PlusHuntingProfit.parseShard("You caught a Birries Shard!"))
		assertEquals("SHARD_SEA_SERPENT" to 3L, PlusHuntingProfit.parseShard("You caught x3 Sea Serpent Shards!"))
		assertNull(PlusHuntingProfit.parseShard("hello there"))
	}
}
