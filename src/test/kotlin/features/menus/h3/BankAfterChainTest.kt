// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChainRunner
import dev.ashtonite365.apex.features.menus.bank.BankAction
import dev.ashtonite365.apex.features.menus.bank.BankChains
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.features.menus.support.SlotText

class BankAfterChainTest {
	private val SLOT = Regex("^slot=(\\d+) name=(.*?) lore=\\[(.*)]$")
	private val w4 = javaClass.getResource("/apex-fixtures/bank-window4.txt")!!.readText().lines().drop(1).mapNotNull { SLOT.matchEntire(it) }
		.map { m -> SlotText(m.groupValues[1].toInt(), m.groupValues[2], if (m.groupValues[3].isEmpty()) emptyList() else m.groupValues[3].split(", ")) }

	@Test
	fun window4PressesAreOneClickEach() {
		val m4 = BankParser.parse("Personal Bank Account", w4)!!
		assertEquals(373_567_515.3, m4.balance!!, 0.01)
		assertEquals(13, BankChains.press(m4, BankAction.OPEN_WITHDRAW).single)
		assertEquals(11, BankChains.press(m4, BankAction.OPEN_DEPOSIT).single)
		assertEquals(11, BankChains.press(m4, BankAction.DEPOSIT_ALL).single)
		assertEquals(13, BankChains.press(m4, BankAction.WITHDRAW_HALF).single)
	}

	@Test
	fun onlyTheOwnersOverlayCoversNewWindows() {
		assertFalse(MenuChainRunner.ownsCover("bank", "bazaar"))
		assertFalse(MenuChainRunner.ownsCover("museum", "bazaar"))
		assertTrue(MenuChainRunner.ownsCover("bazaar", "bazaar"))
		assertTrue(MenuChainRunner.ownsCover("bazaar-orders", "bazaar"))
		assertFalse(MenuChainRunner.ownsCover(null, "bazaar"))
		assertFalse(MenuChainRunner.coveringFor("bazaar"))
	}
}
