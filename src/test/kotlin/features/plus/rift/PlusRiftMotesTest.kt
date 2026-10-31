package dev.ashtonite365.apex.test.features.plus.rift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.PlusRiftMotes

class PlusRiftMotesTest {
	@Test
	fun parse() {
		assertEquals(1200.0, PlusRiftMotes.sellPriceOf(listOf("foo", "Motes Sell Price: 1,200 Motes")))
		assertNull(PlusRiftMotes.sellPriceOf(listOf("Sell Price: 5 coins")))
		assertEquals(3456L, PlusRiftMotes.motesOf(listOf("Motes: 3,456")))
		assertEquals(723, PlusRiftMotes.parseTime("12m 3s"))
		assertEquals(45, PlusRiftMotes.parseTime("45s"))
	}
}
