/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.util

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.auction.AuctionListingParser
import dev.ashtonite365.apex.features.auction.BinWarning
import dev.ashtonite365.apex.features.dungeon.CroesusHelpers
import dev.ashtonite365.apex.features.inventory.AnvilReforgeHelpers
import dev.ashtonite365.apex.features.auction.BazaarExtras
import dev.ashtonite365.apex.features.auction.BazaarOrders
import dev.ashtonite365.apex.features.parsers.CrimsonParser
import dev.ashtonite365.apex.features.parsers.JerryParser
import dev.ashtonite365.apex.features.parsers.RiftParser

class IslandParserTest {
	@Test
	fun jerry() {
		val d = JerryParser.JerryData()
		assertTrue(JerryParser.apply(d, "☺ You discovered a Golden Jerry!", 1000))
		assertTrue(JerryParser.apply(d, "You located a Green Jerry!", 5000))
		assertFalse(JerryParser.apply(d, "You discovered a Red Jerry!", 6000))
		assertEquals(1, d.counts[JerryParser.Colour.GOLDEN])
		assertEquals(65.seconds, JerryParser.timeSinceLast(d, 70000))
	}

	@Test
	fun crimson() {
		assertEquals(CrimsonParser.Miniboss.BARBARIAN_DUKE_X, CrimsonParser.parseMinibossDown("  BARBARIAN DUKE X DOWN!"))
		assertNull(CrimsonParser.parseMinibossDown("SOMEONE DOWN!"))
		assertEquals(CrimsonParser.ReputationGain(CrimsonParser.Faction.MAGE, 1250), CrimsonParser.parseReputation("+1,250 Mage Reputation"))
		assertNull(CrimsonParser.parseReputation("+5 Coins"))
	}

	@Test
	fun rift() {
		assertEquals(12.minutes + 30.seconds, RiftParser.parseTimeLeft("ф 12m 30s Left"))
		assertEquals(45.seconds, RiftParser.timeLeftFromScoreboard(listOf("Hypixel", "ф 45s Left")))
		assertNull(RiftParser.parseTimeLeft("Purse: 100"))
	}

	@Test
	fun auctionListing() {
		val l = AuctionListingParser.parse("Hyperion", listOf("Seller: [MVP+] Notch", "Buy it now: 1,200,000,000 coins", "Ends in: 1h 5m"))!!
		assertTrue(l.isBin)
		assertEquals("Notch", l.seller)
		assertEquals(1.2e9, l.price)
		assertEquals(65.minutes, l.endsIn)
		val b = AuctionListingParser.parse("Dirt", listOf("Seller: Steve", "Bids: 3 bids", "Top bid: 50 coins"))!!
		assertEquals(3, b.bids)
		assertEquals(listOf("Dirt", "Hyperion"), AuctionListingParser.sort(listOf(l, b), AuctionListingParser.Sort.PRICE_ASC).map { it.itemName })
		assertNull(AuctionListingParser.parse("Stick", listOf("A stick")))
	}

	@Test
	fun buyGuard() {
		assertTrue(BinWarning.shouldWarnBuy(200, 100.0, 50))
		assertFalse(BinWarning.shouldWarnBuy(140, 100.0, 50))
		assertFalse(BinWarning.shouldWarnBuy(200, 0.0, 50))
	}

	@Test
	fun bazaarExtras() {
		assertEquals(9.9, BazaarExtras.suggestedSellPrice(10.0, BazaarExtras.FlipMode.COMPETITIVE), 1e-9)
		assertEquals(10.0, BazaarExtras.suggestedSellPrice(10.0, BazaarExtras.FlipMode.MATCHED))
		val orders = listOf(BazaarOrders.Order("A", null, true, 10, 2.0), BazaarOrders.Order("B", null, false, 5, 4.0),
			BazaarOrders.Order("C", null, true, 1, 100.0, "FILLED"))
		assertEquals(20.0 to 20.0, BazaarExtras.orderValue(orders))
		val d = BazaarExtras.Data()
		BazaarExtras.addSpent(d, 100.0, "2026-10-13"); BazaarExtras.addSpent(d, 50.0, "2026-10-13")
		assertEquals(150.0, d.spentToday)
		BazaarExtras.addSpent(d, 5.0, "2026-10-14")
		assertEquals(5.0, d.spentToday)
	}

	@Test
	fun queuedHelpers() {
		assertEquals(CroesusHelpers.RunState.UNOPENED, CroesusHelpers.runState(listOf("No Chests Opened!")))
		assertEquals(CroesusHelpers.RunState.PARTIAL, CroesusHelpers.runState(listOf("Opened Chest: Gold")))
		assertNull(CroesusHelpers.runState(listOf("x")))
		assertEquals("Withered", AnvilReforgeHelpers.matchedReforge("Withered Hyperion ✪✪✪", "Fabled|Withered"))
		assertNull(AnvilReforgeHelpers.matchedReforge("Heroic Hyperion", "Fabled|Withered"))
	}
}
