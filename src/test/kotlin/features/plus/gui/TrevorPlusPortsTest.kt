// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.gui.TrevorPlusPorts

class TrevorPlusPortsTest {
	@Test
	fun pelts() {
		assertEquals(12 to false, TrevorPlusPorts.parsePelts("Killing the animal rewarded you 12 pelts."))
		assertEquals(5 to true, TrevorPlusPorts.parsePelts("Your mob died randomly, you are rewarded 5 pelts."))
		assertNull(TrevorPlusPorts.parsePelts("nope"))
	}
}
