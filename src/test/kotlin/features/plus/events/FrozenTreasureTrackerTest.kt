// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.FrozenTreasureTracker
import dev.ashtonite365.apex.util.plus.ProfitTracker

class FrozenTreasureTrackerTest {
	@Test
	fun parseAndIce() {
		assertEquals("PACKED_ICE", FrozenTreasureTracker.parse("FROZEN TREASURE! You found Packed Ice!"))
		assertEquals(FrozenTreasureTracker.COMPACT, FrozenTreasureTracker.parse("COMPACT! You found an Enchanted Ice!"))
		assertNull(FrozenTreasureTracker.parse("FROZEN TREASURE! You found Dirt!"))
		val d = ProfitTracker.Data(items = linkedMapOf("PACKED_ICE" to 32L, FrozenTreasureTracker.COMPACT to 2L))
		assertEquals(32 * 9L + 2 * 160L, FrozenTreasureTracker.estimatedIce(d))
	}
}
