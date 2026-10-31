package dev.ashtonite365.apex.test.features.plus.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.inventory.PlusInventorySmall
import dev.ashtonite365.apex.util.plus.matches

class PlusInventorySmallTest {
	@Test
	fun patterns() {
		assertTrue(PlusInventorySmall.outbidPattern.matches("§6[Auction] §aMrBaiacu §eoutbid you by §659,083 coins §efor §fFiredust Dagger §e§lCLICK"))
		assertFalse(PlusInventorySmall.outbidPattern.matches("[Auction] You bought Firedust Dagger"))
		assertTrue(PlusInventorySmall.allDonatedPattern.matches("§7Items Donated: §a100%"))
	}

	@Test
	fun evolving() {
		assertEquals("rift_discrite_seconds", PlusInventorySmall.secondsAttribute("DISCRITE"))
		assertEquals("seconds_held", PlusInventorySmall.secondsAttribute(null))
		assertEquals(PlusInventorySmall.EVOLVE_SECONDS - 100, PlusInventorySmall.timeLeft(100))
		assertEquals(0, PlusInventorySmall.timeLeft(Int.MAX_VALUE))
	}
}
