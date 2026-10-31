// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankPage
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.bank.BankTx
import dev.ashtonite365.apex.features.menus.bank.TxSort
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankModelTest {
	private val main = listOf(
		SlotText(11, "§aDeposit Coins", listOf("§7Current balance: §61,234,567.8", "", "§eClick to make a deposit!")),
		SlotText(13, "§aWithdraw Coins", listOf("§7Current balance: §61,234,567.8")),
		SlotText(15, "§aRecent transactions", listOf("§a+ §65.2M§7, §e3 hours ago §7by §bSteve", "§c- §6100k§7, §e2 days ago", "§a+ §61,000§7, §ejust now?", "garbage line")),
		SlotText(32, "§aInformation", listOf("§7Interest in: §b1d 2h", "§7Projected interest: §6 50,000 coins")),
		SlotText(31, "§cClose"),
		SlotText(0, " "),
	)

	@Test
	fun titlesAreTolerant() {
		for (t in listOf("Personal Bank Account", "§8Co-op Bank Account", "Coop Bank Account", "  Bank  Account ", "Island Bank"))
			assertEquals(BankPage.MAIN, BankParser.page(t), t)
		for (t in listOf("Bank Deposit", "§0Bank Deposits", "Deposit Coins")) assertEquals(BankPage.DEPOSIT, BankParser.page(t), t)
		for (t in listOf("Bank Withdrawal", "Bank Withdraw", "Withdrawal")) assertEquals(BankPage.WITHDRAW, BankParser.page(t), t)
		for (t in listOf("Bazaar ➜ Farming", "Bank of Steve", "Piggy Bank Upgrade", "")) assertNull(BankParser.page(t), t)
		assertNull(BankParser.page("Bank"))
		assertTrue(BankParser.ANY_TITLE.containsMatchIn("Bank Withdrawal"))
		assertTrue(!BankParser.ANY_TITLE.containsMatchIn("Withdrawal"))
	}

	@Test
	fun parsesMainPage() {
		val m = BankParser.parse("Personal Bank Account", main).also { assertNotNull(it) }!!
		assertEquals(BankPage.MAIN, m.page)
		assertEquals("Personal", m.account)
		assertEquals(1_234_567.8, m.balance!!, 0.01)
		assertEquals(11, m.button(BankAction.OPEN_DEPOSIT)?.slot)
		assertEquals(13, m.button(BankAction.OPEN_WITHDRAW)?.slot)
		assertEquals(31, m.button(BankAction.CLOSE)?.slot)
		assertEquals(26 * 3600L, m.interestInSeconds)
		assertEquals(50_000.0, m.projectedInterest!!, 0.1)
		assertEquals(3, m.transactions.size)
		assertEquals(BankTx(1, 5_200_000.0, 3 * 3600L, "3 hours ago", "Steve"), m.transactions[0])
		assertEquals(-1, m.transactions[1].sign)
		assertEquals(2 * 86400L, m.transactions[1].ageSeconds)
		assertNull(m.transactions[2].ageSeconds)
	}

	@Test
	fun parsesSubPagesAndAmounts() {
		val dep = BankParser.parse("Bank Deposit", listOf(
			SlotText(11, "Your whole purse", listOf("Amount to deposit: §62,000,000")),
			SlotText(13, "Half your purse", listOf("Amount to deposit: 1M")),
			SlotText(15, "Specific amount"),
			SlotText(31, "Go Back"),
		))!!
		assertEquals(BankPage.DEPOSIT, dep.page)
		assertEquals(2_000_000.0, dep.button(BankAction.DEPOSIT_ALL)!!.amount!!, 0.1)
		assertEquals(1_000_000.0, dep.button(BankAction.DEPOSIT_HALF)!!.amount!!, 0.1)
		assertEquals(15, dep.button(BankAction.DEPOSIT_CUSTOM)?.slot)
		assertEquals(31, dep.button(BankAction.BACK)?.slot)
		val wd = BankParser.parse("Bank Withdrawal", listOf(
			SlotText(10, "Everything in the account"), SlotText(12, "Half the account"),
			SlotText(14, "20% of the account"), SlotText(16, "Specific amount"),
		))!!
		assertEquals(listOf(10, 12, 14, 16), listOf(BankAction.WITHDRAW_ALL, BankAction.WITHDRAW_HALF, BankAction.WITHDRAW_20, BankAction.WITHDRAW_CUSTOM).map { wd.button(it)?.slot })
		assertNull(wd.button(BankAction.DEPOSIT_CUSTOM))
	}

	@Test
	fun garbageFallsBackToVanilla() {
		assertNull(BankParser.parse("Personal Bank Account", emptyList()))
		assertNull(BankParser.parse("Personal Bank Account", listOf(SlotText(0, "§k???"), SlotText(1, "Stone", listOf("§7Balance: lots")))))
		assertNull(BankParser.parse("Bank Deposit", listOf(SlotText(11, "Withdraw Coins"))))
		assertNull(BankParser.parse("Not A Bank", main))
		assertNull(BankParser.parseTx("hello"))
		assertNull(BankParser.parseTx("+ lots, yesterday"))
	}

	@Test
	fun transactionLinesAreTolerant() {
		assertEquals(BankTx(1, 1500.0, 60, "a minute ago", null), BankParser.parseTx("§a+§6 1.5k coins§7, §ea minute ago"))
		assertEquals(BankTx(-1, 3e9, 5 * 60, "5m ago", "Alex_2"), BankParser.parseTx("  -  3B ,  5m ago  by Alex_2 "))
	}

	@Test
	fun sorting() {
		val a = BankTx(1, 10.0, 100, "", null); val b = BankTx(-1, 50.0, 10, "", null)
		val c = BankTx(1, 30.0, null, "", null); val d = BankTx(-1, 5.0, 5, "", null)
		val l = listOf(a, b, c, d)
		assertEquals(listOf(d, b, a, c), BankParser.sort(l, TxSort.NEWEST))
		assertEquals(listOf(b, c, a, d), BankParser.sort(l, TxSort.LARGEST))
		assertEquals(listOf(a, c, d, b), BankParser.sort(l, TxSort.DEPOSITS_FIRST))
		assertEquals(10.0 - 50 + 30 - 5, BankParser.net(l), 1e-9)
	}

}
