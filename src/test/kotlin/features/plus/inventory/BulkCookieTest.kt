package dev.ashtonite365.apex.test.features.plus.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.CookieConverter

class BulkCookieTest {
	@Test
	fun amount() {
		assertEquals(6, CookieConverter.parseCookieAmount(listOf("Booster Cookie", "Amount: 6x")))
		assertEquals(6, CookieConverter.parseCookieAmount(listOf("Booster Cookie x6")))
		assertEquals(1, CookieConverter.parseCookieAmount(listOf("Booster Cookie")))
	}
}
