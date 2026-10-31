// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.foraging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.foraging.StarlynCouponProfit
import dev.ashtonite365.apex.features.plus.foraging.WhispersPerHotfPerk
import dev.ashtonite365.apex.features.plus.foraging.WhispersPerHotfPerk.Perk

class StarlynWhispersTest {
	@Test
	fun starlynCosts() {
		val lore = listOf("Some item", "", "Cost", "Agatha's Coupon x5", "Enchanted Oak Log x1,280", "", "Click to trade!")
		assertEquals(listOf("Agatha's Coupon" to 5L, "Enchanted Oak Log" to 1280L), StarlynCouponProfit.readCosts(lore))
		assertEquals(emptyList<Pair<String, Long>>(), StarlynCouponProfit.readCosts(listOf("no cost")))
		assertTrue(StarlynCouponProfit.isShopSlot(10)); assertFalse(StarlynCouponProfit.isShopSlot(17)); assertFalse(StarlynCouponProfit.isShopSlot(45))
	}

	@Test
	fun starlynProfit() {

		assertEquals(160.0, StarlynCouponProfit.profitPerCoupon(1000.0, listOf(Triple(true, 5L, null), Triple(false, 10L, 20.0))))
		assertNull(StarlynCouponProfit.profitPerCoupon(1000.0, listOf(Triple(false, 10L, 20.0))))
	}

	@Test
	fun whispers() {
		assertEquals(27L, Perk.SWEEP.totalCost(2))
		assertEquals(27L + 64L, Perk.SWEEP.totalCost(3))
		assertEquals(236L, Perk.DAILY_WISHES.totalCost(2))
		assertEquals(Perk.SWEEP, Perk.byName("Sweep"))
		assertEquals(12, WhispersPerHotfPerk.level(listOf("Level 12/50", "")))
		assertEquals(5 to (Perk.RICOCHET.totalCost(10) - Perk.RICOCHET.totalCost(5)), WhispersPerHotfPerk.next10(Perk.RICOCHET, 5))
		assertTrue(WhispersPerHotfPerk.spentLine(Perk.SWEEP, 50).contains("Max level"))
	}
}
