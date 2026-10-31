package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankAmounts
import dev.ashtonite365.apex.features.menus.bank.BankChains
import dev.ashtonite365.apex.features.menus.bank.BankPage
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankShortAmountsTest {
	@Test
	fun displayIsShortFloored() {
		assertEquals(BankAmounts.AmountLine("+187K", BankAmounts.GREEN), BankAmounts.amountLine(BankAction.DEPOSIT_ALL, 187_608.0))
		assertEquals(BankAmounts.AmountLine("-187K", BankAmounts.RED), BankAmounts.amountLine(BankAction.WITHDRAW_ALL, 187_608.0))
		assertEquals("1M", BankAmounts.shortText(1_999_999.0))
		assertEquals("2B", BankAmounts.shortText(2_750_000_000.0))
		assertEquals("375M", BankAmounts.shortText(375_600_000.0))
		assertEquals("187K", BankAmounts.shortText(187_900.0))
		assertEquals("-375M", BankAmounts.amountLine(BankAction.WITHDRAW_HALF, 375_600_000.0)!!.text)
		assertEquals("+2B", BankAmounts.amountLine(BankAction.DEPOSIT_ALL, 2_750_000_000.0)!!.text)
		assertEquals("All (187K)", BankAmounts.label("All", 187_900.0))
		assertEquals("2B", BankAmounts.shortText(2_999_999_999.0))
		assertEquals("999", BankAmounts.shortText(999.99))
		assertEquals("1K", BankAmounts.shortText(1000.0))
		assertEquals("0", BankAmounts.shortText(0.4))
		assertEquals("Half (93K)", BankAmounts.label("Half", BankAmounts.amount(BankAction.DEPOSIT_HALF, 187_608.0, null)))
	}

	@Test
	fun sentValueStaysExact() {
		val wd = BankParser.parse("Bank Withdrawal", listOf(
			SlotText(10, "Everything in the account", listOf("Amount to withdraw: 187,608")),
			SlotText(12, "Half of the account"), SlotText(16, "Specific amount"), SlotText(31, "Go Back")))!!
		assertEquals(BankPage.WITHDRAW, wd.page)
		assertEquals(187_608.0, wd.button(BankAction.WITHDRAW_ALL)!!.amount)
		assertEquals(187_608.0, BankAmounts.amount(BankAction.WITHDRAW_ALL, null, 187_608.0))
		assertEquals(10, BankChains.press(wd, BankAction.WITHDRAW_ALL).single)
		assertEquals(16, BankChains.press(wd, BankAction.WITHDRAW_CUSTOM).single)
	}

	private val dep = BankParser.parse("Bank Deposit", listOf(SlotText(11, "Your whole purse", listOf("Amount to deposit: 1,234")),
		SlotText(13, "Half your purse", listOf("Amount to deposit: 617")), SlotText(15, "Specific amount"), SlotText(31, "Go Back")))!!
	private val wd = BankParser.parse("Bank Withdrawal", listOf(
		SlotText(10, "Everything in the account", listOf("Amount to withdraw: 373,567,481")),
		SlotText(12, "Half the account", listOf("Amount to withdraw: 186,783,740")),
		SlotText(14, "Withdraw 20%", listOf("Amount to withdraw: 74,713,496")),
		SlotText(16, "Specific amount"), SlotText(31, "Go Back")))!!

	@Test
	fun allClicksHypixelsOwnSlot() {
		assertEquals(11, BankChains.press(dep, BankAction.DEPOSIT_ALL).single)
		assertEquals(10, BankChains.press(wd, BankAction.WITHDRAW_ALL).single)
	}

	@Test
	fun everyButtonLabelIsShort() {
		val w = listOf(BankAction.WITHDRAW_ALL, BankAction.WITHDRAW_HALF, BankAction.WITHDRAW_20).map { BankAmounts.amountLine(it, wd.button(it)!!.amount)!!.text }
		assertEquals(listOf("-373M", "-186M", "-74M"), w)
		val d = listOf(BankAction.DEPOSIT_ALL, BankAction.DEPOSIT_HALF).map { BankAmounts.amountLine(it, dep.button(it)!!.amount)!!.text }
		assertEquals(listOf("+1K", "+617"), d)
		assertEquals("-375M", BankAmounts.amountLine(BankAction.WITHDRAW_ALL, 375_210_000.0)!!.text)
		for (t in w + d) org.junit.jupiter.api.Assertions.assertFalse(t.contains(',') || t.contains('.'), t)
	}

	@Test
	fun fitScalesToButtonWidth() {
		assertEquals(1f, BankAmounts.fitScale(20, 40))
		assertEquals(0.5f, BankAmounts.fitScale(80, 40))
		org.junit.jupiter.api.Assertions.assertTrue(BankAmounts.fitScale(55, 30) * 55 <= 30f)
	}

	@Test
	fun emptyIsGreyedAndSendsNoClick() {
		assertEquals("purse empty", BankAmounts.emptyReason(BankPage.DEPOSIT, 0.0, 5.0))
		assertEquals("bank empty", BankAmounts.emptyReason(BankPage.WITHDRAW, 5.0, 0.4))
		org.junit.jupiter.api.Assertions.assertNull(BankAmounts.emptyReason(BankPage.DEPOSIT, null, 0.0))
		org.junit.jupiter.api.Assertions.assertNull(BankAmounts.emptyReason(BankPage.WITHDRAW, 0.0, 1.0))
		for (a in listOf(BankAction.DEPOSIT_ALL, BankAction.DEPOSIT_HALF)) {
			org.junit.jupiter.api.Assertions.assertNull(BankChains.press(dep, a, purse = 0.0, balance = 9.0).single)
		}
		for (a in listOf(BankAction.WITHDRAW_ALL, BankAction.WITHDRAW_HALF, BankAction.WITHDRAW_20, BankAction.WITHDRAW_CUSTOM)) {
			org.junit.jupiter.api.Assertions.assertNull(BankChains.press(wd, a, purse = 9.0, balance = 0.0).single)
		}
		assertEquals(10, BankChains.press(wd, BankAction.WITHDRAW_ALL, purse = 0.0, balance = 9.0).single)
	}
}
