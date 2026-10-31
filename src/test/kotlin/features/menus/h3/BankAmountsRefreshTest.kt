package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankAmounts
import dev.ashtonite365.apex.features.menus.bank.BankChains
import dev.ashtonite365.apex.features.menus.bank.BankMemory
import dev.ashtonite365.apex.features.menus.bank.BankModel
import dev.ashtonite365.apex.features.menus.bank.BankPage
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.bank.BankRefresh
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankAmountsRefreshTest {
	private fun main(balance: Double) = BankModel(BankPage.MAIN, null, balance, null, null, null, emptyList(), emptyList())
	private val wd = BankParser.parse("Bank Withdrawal", listOf(
		SlotText(10, "Everything in the account", listOf("Amount to withdraw: 1,000")),
		SlotText(12, "Half of the account", listOf("Amount to withdraw: 500")),
		SlotText(31, "Go Back")))!!

	@Test
	fun chatLineDropsStaleSubPageAmountsAndRecomputes() {
		BankMemory.reset()
		BankMemory.main = main(1000.0)
		BankMemory.remember(wd)
		assertEquals(500.0, BankAmounts.shown(BankAction.WITHDRAW_HALF, BankMemory.page(BankPage.WITHDRAW), null, BankMemory.balance(wd)))
		BankMemory.onChat("You have withdrawn 500 coins! You now have 500 coins in your account!")
		assertNull(BankMemory.page(BankPage.WITHDRAW))
		val bal = BankMemory.balance(wd)
		assertEquals(500.0, bal)
		assertEquals(250.0, BankAmounts.shown(BankAction.WITHDRAW_HALF, BankMemory.page(BankPage.WITHDRAW), null, bal))
		assertEquals(500.0, BankAmounts.shown(BankAction.WITHDRAW_ALL, BankMemory.page(BankPage.WITHDRAW), null, bal))
		assertEquals(125.0, BankAmounts.shown(BankAction.DEPOSIT_HALF, null, 250.0, bal))
	}

	@Test
	fun newWindowLoreWinsOverAbbreviatedChat() {
		BankMemory.reset()
		BankMemory.main = main(373_600_000.0)
		BankMemory.remember(wd)
		BankMemory.remember(main(373_567_431.0))
		assertNull(BankMemory.page(BankPage.WITHDRAW))
		BankMemory.onChat("You have withdrawn 50 coins! You now have 373.6M coins in your account!")
		assertEquals(373_567_431.0, BankMemory.balance(main(1.0)))
		assertTrue(BankRefresh.loreIsCurrent(373_567_431.0, "373.6M"))
		assertFalse(BankRefresh.loreIsCurrent(380_000_000.0, "373.6M"))
		assertNull(BankRefresh.chatBalance("Withdrawing coins..."))
		BankMemory.reset()
	}

	@Test
	fun chatThenWindowPrefersWindow() {
		BankMemory.reset()
		BankMemory.main = main(1000.0)
		BankMemory.onChat("You have deposited 33 coins! You now have 1,033 coins in your account!")
		assertEquals(1033.0, BankMemory.balance(main(0.0)))
		BankMemory.remember(main(1033.0))
		assertNull(BankMemory.chatBalance)
		BankMemory.reset()
	}

	@Test
	fun displayIsFlooredWholeNumber() {
		assertEquals(1234L, BankAmounts.whole(1234.99))
		assertEquals("1,234", BankAmounts.wholeText(1234.99))
		assertEquals(1.0, BankAmounts.amount(BankAction.WITHDRAW_HALF, null, 3.0))
		assertEquals("Half (1)", BankAmounts.label("Half", BankAmounts.amount(BankAction.WITHDRAW_HALF, null, 3.0)))
		assertEquals(BankAmounts.AmountLine("-74M", BankAmounts.RED), BankAmounts.amountLine(BankAction.WITHDRAW_20, 74_713_496.8))
		assertEquals(BankAmounts.AmountLine("+1K", BankAmounts.GREEN), BankAmounts.amountLine(BankAction.DEPOSIT_ALL, 1234.99))
		assertFalse(BankAmounts.wholeText(0.5).contains('.'))
	}

	@Test
	fun sentValueIsExact() {

		assertEquals(16, BankChains.press(wd.copy(page = BankPage.WITHDRAW, buttons = wd.buttons + wd.buttons.first().copy(action = BankAction.WITHDRAW_CUSTOM, slot = 16)),
			BankAction.WITHDRAW_CUSTOM).single)

		assertEquals(12, BankChains.press(wd, BankAction.WITHDRAW_HALF).single)
		assertEquals(1234.99, BankAmounts.amount(BankAction.DEPOSIT_ALL, 1234.99, null))
	}
}
