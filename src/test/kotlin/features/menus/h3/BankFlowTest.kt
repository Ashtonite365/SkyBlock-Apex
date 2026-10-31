// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankAmounts
import dev.ashtonite365.apex.features.menus.bank.BankChains
import dev.ashtonite365.apex.features.menus.bank.BankModel
import dev.ashtonite365.apex.features.menus.bank.BankLayout
import dev.ashtonite365.apex.features.menus.bank.BankMenuSettings
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankFlowTest {
	private val main = BankParser.parse("Personal Bank Account", listOf(
		SlotText(11, "Deposit Coins", listOf("Current balance: 2,500,000")), SlotText(13, "Withdraw Coins"), SlotText(31, "Close")))!!
	private val dep = BankParser.parse("Bank Deposit", listOf(
		SlotText(11, "Your whole purse"), SlotText(13, "Half your purse"), SlotText(15, "Specific amount"), SlotText(31, "Go Back")))!!
	private val wd = BankParser.parse("Bank Withdrawal", listOf(
		SlotText(10, "Everything in the account"), SlotText(12, "Half the account"), SlotText(14, "Withdraw 20%"),
		SlotText(16, "Specific amount"), SlotText(31, "Go Back")))!!
	private val wdNoBack = BankParser.parse("Bank Withdrawal", listOf(SlotText(10, "Everything in the account")))!!

	private val mainSlots = listOf(SlotText(11, "Deposit Coins"), SlotText(13, "Withdraw Coins"), SlotText(31, "Close"))
	private val depSlots = listOf(SlotText(11, "Your whole purse"), SlotText(13, "Half your purse"), SlotText(15, "Specific amount"), SlotText(31, "Go Back"))
	private val wdSlots = listOf(SlotText(10, "Everything in the account"), SlotText(12, "Half the account"), SlotText(14, "Withdraw 20%"),
		SlotText(16, "Specific amount"), SlotText(31, "Go Back"))

	@Test fun presetsHaveNoWarning() {
		assertTrue(!BankMenuSettings.PRESETS_SHOW_WARNING)
		assertTrue(BankMenuSettings.allOptions.keys.none { it == "chain-presets" || it == "return-to-main" })
	}

	@Test
	fun openDepositOnlyOpens() {
		assertEquals(11, BankChains.press(main, BankAction.OPEN_DEPOSIT).single)
		assertEquals(13, BankChains.press(main, BankAction.OPEN_WITHDRAW).single)
	}

	@Test
	fun everyPlayerClickIsExactlyOneSentClick() {
		val sent = mutableListOf<Int>()
		fun press(m: dev.ashtonite365.apex.features.menus.bank.BankModel, a: BankAction) { BankChains.press(m, a).single?.let { sent += it } }
		press(main, BankAction.DEPOSIT_HALF)
		assertEquals(listOf(11), sent)
		press(dep, BankAction.DEPOSIT_HALF)
		assertEquals(listOf(11, 13), sent)
		press(main, BankAction.WITHDRAW_20); press(wd, BankAction.WITHDRAW_20)
		assertEquals(listOf(11, 13, 13, 14), sent)
		for (a in BankAction.values()) for (m in listOf(main, dep, wd, wdNoBack)) {
			val r = BankChains.press(m, a)
			assertTrue(r.single == null || r.single is Int)
		}
	}

	@Test
	fun withdrawAllIsOneClickNoConfirm() {
		assertTrue(BankMenuSettings.allOptions.keys.none { it == "confirm-withdraw-all" })
		assertEquals(10, BankChains.press(wd, BankAction.WITHDRAW_ALL).single)
		assertEquals(31, BankChains.press(wd, BankAction.BACK).single)
		val src = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/features/menus/bank/BankMenu.kt").readText()
		assertTrue(!src.contains("armedUntil") && !src.contains("Sure?") && !src.contains("3000"))
	}

	@Test
	fun customClicksSpecificAmountTheUserTypesTheSign() {
		assertEquals(11, BankChains.press(main, BankAction.DEPOSIT_CUSTOM).single)
		assertEquals(15, BankChains.press(dep, BankAction.DEPOSIT_CUSTOM).single)
		assertEquals(16, BankChains.press(wd, BankAction.WITHDRAW_CUSTOM).single)
	}

	@Test
	fun wrongPageSendsNothing() {
		assertNull(BankChains.press(dep, BankAction.WITHDRAW_ALL).single)
		assertNull(BankChains.press(wdNoBack, BankAction.BACK).single)
	}

	@Test
	fun chatPatterns() {

		assertTrue(BankChains.DEPOSIT_CHAT.matches("You have deposited 33 coins! You now have 373.6M coins in your account!"))
		for (n in listOf("50", "500", "500", "20M", "74.7M")) assertTrue(BankChains.WITHDRAW_CHAT.matches("You have withdrawn $n coins! You now have 373.6M coins in your account!"), n)
		assertTrue(!BankChains.WITHDRAW_CHAT.containsMatchIn("Withdrawing coins..."))
		assertTrue(!BankChains.DEPOSIT_CHAT.containsMatchIn("Depositing coins..."))
		assertTrue(!BankChains.DEPOSIT_CHAT.containsMatchIn("You have no coins to deposit"))
	}

	@Test
	fun amountParsing() {
		assertEquals(1_500_000L, MenuText.parseAmount("1.5m"))
		assertEquals(250_000L, MenuText.parseAmount("250k"))
		assertEquals(1000L, MenuText.parseAmount("1,000"))
		assertEquals(42L, MenuText.parseAmount(" 42 "))
		assertEquals(2_000_000_000L, MenuText.parseAmount("2B"))
		for (bad in listOf("", "abc", "0", "-5", "1.5x", "k", "1m5", "0.4")) assertNull(MenuText.parseAmount(bad), bad)
	}

	@Test
	fun amountsAndLabels() {
		assertEquals("All (2M)", BankAmounts.label("All", BankAmounts.amount(BankAction.DEPOSIT_ALL, 2_500_000.0, 0.0)))
		assertEquals("Half (1M)", BankAmounts.label("Half", BankAmounts.amount(BankAction.DEPOSIT_HALF, 2_500_000.0, 0.0)))
		assertEquals("20% (500K)", BankAmounts.label("20%", BankAmounts.amount(BankAction.WITHDRAW_20, null, 2_500_000.0)))
		assertEquals("All (0)", BankAmounts.label("All", BankAmounts.amount(BankAction.WITHDRAW_ALL, 9.0, 0.0)))
		assertEquals("Half (0)", BankAmounts.label("Half", BankAmounts.amount(BankAction.WITHDRAW_HALF, 9.0, 0.0)))
		assertEquals("All", BankAmounts.label("All", BankAmounts.amount(BankAction.WITHDRAW_ALL, 9.0, null)))
		assertEquals(1_000_000.0, BankAmounts.amount(BankAction.WITHDRAW_CUSTOM, null, 5e6, 1e6))
		assertEquals(5e6, BankAmounts.amount(BankAction.WITHDRAW_CUSTOM, null, 5e6, 9e6))
	}

	@Test
	fun purseSources() {
		assertEquals(1_234_567.0, BankParser.purseFromSidebar(listOf("§7Bank", "§fPurse: §61,234,567 §e(+50)")))
		assertEquals(12_345.0, BankParser.purseFromSidebar(listOf("Piggy: 12,345")))
		assertNull(BankParser.purseFromSidebar(listOf("Bits: 100")))
		val depWithLore = BankParser.parse("Bank Deposit", listOf(SlotText(11, "Your whole purse", listOf("Amount to deposit: 777")), SlotText(31, "Go Back")))!!
		assertEquals(777.0, BankParser.livePurse(emptyList(), depWithLore))
		assertEquals(5.0, BankParser.livePurse(listOf("Purse: 5"), depWithLore))
	}

	@Test
	fun twentyPercentMapsByNameAndEveryPillHitsItsRect() {
		assertEquals(14, wd.button(BankAction.WITHDRAW_20)?.slot)
		val actions = listOf(BankAction.WITHDRAW_ALL, BankAction.WITHDRAW_HALF, BankAction.WITHDRAW_20, BankAction.WITHDRAW_CUSTOM)
		assertEquals(listOf(10, 12, 14, 16), actions.map { wd.button(it)?.slot })
		assertEquals(listOf(11, 13, 15), listOf(BankAction.DEPOSIT_ALL, BankAction.DEPOSIT_HALF, BankAction.DEPOSIT_CUSTOM).map { dep.button(it)?.slot })
		val rects = BankLayout.pills(10, 50, 150, actions.size)
		rects.forEachIndexed { i, r ->
			for (px in listOf(r.x.toDouble(), r.x + r.w / 2.0, r.x + r.w - 0.01)) for (py in listOf(r.y.toDouble(), r.y + r.h - 0.01))
				assertEquals(i, BankLayout.hit(rects, px, py), "pill $i at $px,$py")
		}
		assertNull(BankLayout.hit(rects, rects[0].x + rects[0].w + 1.0, 55.0))
	}

}
