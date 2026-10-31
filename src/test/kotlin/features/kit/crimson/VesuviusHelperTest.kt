// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.crimson

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.crimson.VesuviusHelper

class VesuviusHelperTest {
	@Test
	fun lore() {
		val lore = listOf("Paid Chest", "", "Contents", "Kuudra Teeth x3", "Crimson Essence x500", "", "Cost", "1,000,000 Coins", "Infernal Kuudra Key", "", "Click to open!")
		assertEquals(listOf("Kuudra Teeth x3", "Crimson Essence x500"), VesuviusHelper.contents(lore))
		assertEquals(1_000_000.0, VesuviusHelper.coinCost(lore))
		assertTrue(VesuviusHelper.isClaimed("Kuudra's Hollow", listOf("No more chests to open!")))
	}
}
