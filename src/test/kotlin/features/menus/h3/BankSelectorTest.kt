// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.bank.BankMenu
import dev.ashtonite365.apex.features.menus.bank.BankPage
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.bank.BankSelectorMenu
import dev.ashtonite365.apex.features.menus.bank.BankSelectorParser
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankSelectorTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val d = RealDumpFixtures.load("bank-selector-1017-3")

	@Test fun routing() {
		MenuRegistry.register(BankMenu); MenuRegistry.register(BankSelectorMenu)
		assertSame(BankSelectorMenu, MenuRegistry.match("Bank"))
		assertNull(BankParser.page("Bank"))
		assertSame(BankMenu, MenuRegistry.match("Co-op Bank Account"))
		assertEquals(BankPage.MAIN, BankParser.page("Co-op Bank Account"))
		assertSame(BankMenu, MenuRegistry.match("Personal Bank Account"))
		for (t in listOf("Bank Deposit", "Bank Withdrawal")) assertSame(BankMenu, MenuRegistry.match(t), t)
		assertSame(BankMenu.settings, BankSelectorMenu.settings)
	}

	@Test fun coopChoiceIsOneDelayedClickOnSlot11() {
		val m = BankSelectorParser.parse(d.title, d.slots)!!
		val acc = m.sections.first { it.title == "Accounts" }.tiles
		assertEquals(listOf(11 to "Co-op Bank Account"), acc.map { it.slot to it.name })
		assertTrue(acc.all { it.clickable && it.delayed && it.button == 0 })
	}

	@Test fun personalAndCoopAndCloseAreFoundByName() {

		val slots = listOf(SlotText(11, "Co-op Bank Account"), SlotText(15, "Personal Bank Account"), SlotText(40, "Close"))
		val m = BankSelectorParser.parse("Bank", slots)!!
		assertEquals(setOf("Co-op Bank Account", "Personal Bank Account"), m.sections.first().tiles.map { it.name }.toSet())
		assertEquals(listOf("Close" to 40), m.nav)
	}

	@Test fun unreadableIsVanilla() {
		assertNull(BankSelectorParser.parse("Bank", listOf(SlotText(40, "Close"))))
		assertNull(BankSelectorParser.parse("Personal Bank Account", d.slots))
	}

	@Test fun coopAccountPageParses() {
		val main = RealDumpFixtures.load("bank-main")
		val m = BankParser.parse("Co-op Bank Account", main.slots)!!
		assertEquals(BankPage.MAIN, m.page)
		assertEquals("Co-op", m.account)
		assertNotNull(m.balance)
	}
}
