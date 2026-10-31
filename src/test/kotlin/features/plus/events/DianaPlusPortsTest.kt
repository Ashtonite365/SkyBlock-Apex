// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.DianaPlusPorts
import dev.ashtonite365.apex.features.plus.events.DianaPlusPorts.Gain

class DianaPlusPortsTest {
	@Test
	fun parse() {
		assertEquals(Gain.Coins(1000.0), DianaPlusPorts.parse("Wow! You dug out 1,000 coins!"))
		assertEquals(Gain.Item("GRIFFIN_FEATHER", "Griffin Feather", 1), DianaPlusPorts.parse("RARE DROP! You dug out a Griffin Feather!"))
		assertEquals(Gain.Burrow, DianaPlusPorts.parse("You finished the Griffin burrow chain! (4/4)"))
		assertEquals(Gain.Item("DWARF_TURTLE_SHELMET", "Dwarf Turtle Shelmet", 1), DianaPlusPorts.parse("RARE DROP! Dwarf Turtle Shelmet (+250% ✯ Magic Find)"))
		assertNull(DianaPlusPorts.parse("hello"))
		assertEquals("Minos Inquisitor", DianaPlusPorts.rareMobName("§2Minos Inquisitor §a40M§c❤"))
	}
}
