// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.bank.BankLayout

class BankHeaderLayoutTest {
	private val w = { s: String -> s.length * 6 }

	private val colX = 258; private val right = 330; private val purseEnd = 6 + 164 + 6 + w("373.57M") + 6

	@Test
	fun shiftedLeftAndProjectedRightOfTimer() {
		val r = BankLayout.interestRow(colX, right, purseEnd, w("Interest in"), w("11h"), w("+413.4k (0.111%)"), w("+413.4k"))
		assertTrue(r.x < colX)
		assertEquals(r.x + w("11h") + BankLayout.GAP, r.projX)
		val projW = if (r.full) w("+413.4k (0.111%)") else w("+413.4k")
		assertTrue(r.projX + projW <= right)
		assertTrue(r.x >= purseEnd)
		assertTrue(r.x + w("Interest in") <= right)
	}

	@Test
	fun shortFormWhenPctCannotFit() {
		val r = BankLayout.interestRow(colX, right, colX - 20, w("Interest in"), w("1d 11h"), w("+413.4k (0.111%)"), w("+413.4k"))
		assertFalse(r.full)
		assertTrue(r.projX + w("+413.4k") <= right)
	}

	@Test
	fun defaultShiftWhenRoomy() {
		val r = BankLayout.interestRow(colX, 1000, 0, 60, 18, 40, 30)
		assertEquals(colX - BankLayout.INTEREST_SHIFT, r.x); assertTrue(r.full)
	}
}
