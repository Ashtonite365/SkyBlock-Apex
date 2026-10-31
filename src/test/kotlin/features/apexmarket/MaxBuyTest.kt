package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.MaxBuy

class MaxBuyTest {
	@Test
	fun helpers() {
		Assertions.assertEquals(1234567.0, MaxBuy.parsePurse(listOf("Bank", "Purse: 1,234,567 (+5)")))
		Assertions.assertEquals(10.5, MaxBuy.parsePurse(listOf("Piggy: 10.5")))
		Assertions.assertNull(MaxBuy.parsePurse(listOf("Bits: 5")))
		Assertions.assertEquals(33L, MaxBuy.maxAffordable(100.0, 3.0))
		Assertions.assertEquals(0L, MaxBuy.maxAffordable(100.0, 0.0))
		Assertions.assertEquals(2 * 64L + 14 + 64, MaxBuy.maxCarry(2, listOf(50, 0)))
	}

	@org.junit.jupiter.api.Test fun boxHiddenUnderApexBazaar() {
		Assertions.assertFalse(MaxBuy.showBox(true))
		Assertions.assertTrue(MaxBuy.showBox(false))
		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/apexmarket/MaxBuy.kt").readText()
		Assertions.assertTrue(src.contains("if (!showBox(dev.ashtonite365.apex.features.apexbzmenu.BzMenu.switchOn())) return"))
	}
}
