/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.auction.BazaarOrders
import dev.ashtonite365.apex.features.events.helpers.EventHelpers
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip

class CorePortParserTest {
	private fun order(name: String, buy: Boolean, amount: Int, ppu: Double) =
		BazaarOrders.Order(name, null, buy, amount, ppu)

	@Test
	fun bazaarFilledAndCancelled() {
		val list = mutableListOf(order("Enchanted Coal", true, 64, 10.0), order("Enchanted Iron", false, 10, 5.0))
		assertTrue(BazaarOrders.applyChat(list, "[Bazaar] Your Buy Order for 64x Enchanted Coal was filled!"))
		assertEquals("FILLED", list[0].lastState)
		assertTrue(BazaarOrders.applyChat(list, "[Bazaar] Cancelled! Refunded 10x Enchanted Iron from cancelling Sell Offer!"))
		assertEquals(1, list.size)
		assertTrue(BazaarOrders.applyChat(list, "[Bazaar] Cancelled! Refunded 640 coins from cancelling Buy Order!"))
		assertTrue(list.isEmpty())
		assertFalse(BazaarOrders.applyChat(list, "[Bazaar] Your Buy Order for 1x Nothing was filled!"))
		assertFalse(BazaarOrders.applyChat(list, "hello"))
	}

	@Test
	fun diana() {
		val d = EventHelpers.DianaData()
		assertTrue(EventHelpers.applyDiana(d, "Wow! You dug out 12,500 coins!"))
		assertTrue(EventHelpers.applyDiana(d, "Uh oh! You dug out a Minos Hunter!"))
		assertTrue(EventHelpers.applyDiana(d, "RARE DROP! You dug out a Griffin Feather!"))
		assertTrue(EventHelpers.applyDiana(d, "You dug out a Griffin Burrow! (1/4)"))
		assertFalse(EventHelpers.applyDiana(d, "You dug out nothing"))
		assertEquals(12500L, d.coins)
		assertEquals(1, d.mobs["Minos Hunter"])
		assertEquals(1, d.drops["Griffin Feather"])
		assertEquals(1, d.burrows)
	}

	@Test
	fun resourcePackHost() {
		assertTrue(ServerResourcePackSkip.isHypixel("https://cdn.hypixel.net/pack.zip", null))
		assertTrue(ServerResourcePackSkip.isHypixel("https://x/y.zip", "mc.hypixel.net"))
		assertFalse(ServerResourcePackSkip.isHypixel("https://x/y.zip", "play.example.org"))
	}

	@Test
	fun itemModelFallback() {
		val known = setOf(net.minecraft.resources.Identifier.parse("firmskyblock:known"))
		assertTrue(ServerResourcePackSkip.shouldFallBack(net.minecraft.resources.Identifier.parse("hypixel:aspect_of_the_end"), known))
		assertFalse(ServerResourcePackSkip.shouldFallBack(net.minecraft.resources.Identifier.parse("firmskyblock:known"), known))
		assertFalse(ServerResourcePackSkip.shouldFallBack(net.minecraft.resources.Identifier.parse("minecraft:diamond_sword"), known))
		assertFalse(ServerResourcePackSkip.shouldFallBack(null, known))
	}
}
