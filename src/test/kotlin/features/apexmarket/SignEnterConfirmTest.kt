package dev.ashtonite365.apex.test.features.apexmarket

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.SignEnterConfirm

class SignEnterConfirmTest {
	@Test
	fun detection() {
		val price = listOf("12.5", "^^^^^^^^^^^^^^^", "Your price", "per unit")
		Assertions.assertTrue(SignEnterConfirm.isMarketSign(price, "How much do you want to pay?"))
		Assertions.assertTrue(SignEnterConfirm.isMarketSign(price, "Create BIN Auction"))
		Assertions.assertFalse(SignEnterConfirm.isMarketSign(price, "Chest"))
		Assertions.assertFalse(SignEnterConfirm.isMarketSign(listOf("", "^^^^", "Enter query", ""), "Bazaar ➜ Farming"))
		Assertions.assertFalse(SignEnterConfirm.isMarketSign(listOf("hello", "world", "", ""), "Bazaar"))
	}
}
