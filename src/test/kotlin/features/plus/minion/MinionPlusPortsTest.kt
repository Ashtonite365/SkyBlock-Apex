// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.minion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.minion.MinionPlusPorts

class MinionPlusPortsTest {
	@Test
	fun titles() {
		assertTrue(MinionPlusPorts.isInfernoTitle("Inferno Minion XI"))
		assertTrue(MinionPlusPorts.isInfernoTitle("Inferno Minion 3"))
		assertFalse(MinionPlusPorts.isInfernoTitle("Cobblestone Minion XI"))
		assertTrue(MinionPlusPorts.isInfernoFuel("Inferno Minion Fuel (Glowstone Distillate)"))
	}

	@Test
	fun upgrade() {
		org.junit.jupiter.api.Assertions.assertEquals("Nether Quartz" to 512, MinionPlusPorts.upgradeNeed(listOf("Upgrade", "You need 512 more Nether Quartz.")))
	}
}
