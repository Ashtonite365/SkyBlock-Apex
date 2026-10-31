package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.CookieConverter

class CookieConverterTest {
	@Test
	fun conversions() {
		Assertions.assertEquals(4800.0, CookieConverter.bitsPerCookie(10), 1e-9)
		Assertions.assertEquals(8640.0, CookieConverter.bitsPerCookie(18), 1e-9)
		Assertions.assertEquals(500.0, CookieConverter.coinsPerBit(1000.0, 2.0)!!, 1e-9)
		Assertions.assertNull(CookieConverter.coinsPerBit(1.0, 0.0))
		Assertions.assertEquals(2000L, CookieConverter.parseBitsCost(listOf("Cost", "2,000 Bits", "", "Click to trade!")))
		Assertions.assertNull(CookieConverter.parseBitsCost(listOf("Cost", "5 Coins")))
	}
}
