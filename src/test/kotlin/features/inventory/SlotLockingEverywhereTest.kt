// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.SlotLocking
import dev.ashtonite365.apex.util.ApexClickDelay

class SlotLockingEverywhereTest {
	@Test
	fun guardJudgesOnlyPlayerInventorySlots() {
		assertTrue(ApexClickDelay.guardApplies(isPlayerInventorySlot = true))

		assertFalse(ApexClickDelay.guardApplies(isPlayerInventorySlot = false))
	}

	@Test
	fun lockKeyTargetsHoveredThenDrawnSlot() {
		assertEquals("vanilla", SlotLocking.lockTarget("vanilla", "drawn"))
		assertEquals("drawn", SlotLocking.lockTarget(null, "drawn"))
		assertNull(SlotLocking.lockTarget<String>(null, null))
	}
}
