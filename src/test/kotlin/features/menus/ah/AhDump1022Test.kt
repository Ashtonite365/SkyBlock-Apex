// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.ah

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.auction.BinWarning
import dev.ashtonite365.apex.features.menus.ah.AhControl
import dev.ashtonite365.apex.features.menus.ah.AhKind
import dev.ashtonite365.apex.features.menus.ah.AhLane
import dev.ashtonite365.apex.features.menus.ah.AhModel
import dev.ashtonite365.apex.features.menus.ah.AhSlot
import dev.ashtonite365.apex.features.menus.ah.AhStatsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class AhDump1022Test {
	private fun ah(name: String): Pair<String, List<AhSlot>> = RealDumpFixtures.load(name).let { d ->
		d.title to d.slots.map { AhSlot(it.slot, it.name.trim(), it.lore, it.name.isNotBlank(), it.name.isBlank()) }
	}

	@Test
	fun createAuction() {
		val (t, s) = ah("ah-create-auction")
		val p = AhModel.parse(t, s)!!
		assertEquals(AhKind.CREATE, p.kind)
		assertEquals(29, p.button(AhControl.CREATE)?.slot)
		assertEquals(31, p.button(AhControl.PRICE)?.slot)
		assertEquals(33, p.button(AhControl.DURATION)?.slot)
		assertEquals(48, p.button(AhControl.SWITCH_TYPE)?.slot, "Switch to BIN must be reachable")
		assertEquals(49, p.button(AhControl.BACK)?.slot)
		assertNull(p.hero, "the 'Click an item' placeholder is not a hero item")
		assertEquals("500 coins", AhModel.priceLabel(p.button(AhControl.PRICE)!!), "not the 'Extra fee' lore line")
	}

	@Test
	fun binWarningSlots() {
		val s = RealDumpFixtures.load("ah-create-auction").slots.associateBy { it.slot }
		assertEquals("Create Auction", s[BinWarning.CONFIRM_SLOT]?.cleanName)
		assertTrue(s[BinWarning.PRICE_SLOT]!!.cleanName.startsWith("Starting bid:"))
		assertTrue(s[BinWarning.ITEM_SLOT]!!.cleanName.startsWith("Click an item"))
	}

	@Test
	fun yourBids() {
		val (t, s) = ah("ah-your-bids")
		val p = AhModel.parse(t, s)!!
		assertEquals(AhKind.MANAGE, p.kind)
		assertEquals(listOf(10), p.cards.map { it.slot })
		assertEquals(AhLane.ENDED, p.cards[0].lane)
		assertEquals(22, p.button(AhControl.BACK)?.slot)
	}

	@Test
	fun auctionStats() {
		val d = RealDumpFixtures.load("ah-stats-1022")
		val m = AhStatsParser.parse(d.title, d.slots)!!
		assertEquals(84, m.created); assertEquals(59, m.withBids); assertEquals(14, m.withoutBids)
		assertEquals(173_380_953, m.earned); assertEquals(3_966_716, m.fees)
		assertEquals(204, m.won); assertEquals(265, m.bids); assertEquals(26_000_000, m.highestBid); assertEquals(498_835_013, m.spent)
		assertEquals(25L, m.sold["Epic"]); assertEquals(59L, m.bought["Rare"]); assertEquals(0L, m.bought["Ultimate"])
		assertEquals(31, m.backSlot)
	}

	private fun binVariant(): Pair<String, List<AhSlot>> = ah("ah-create-auction").let { (_, s) ->
		"Create BIN Auction" to s.map { if (it.index == 48) it.copy(name = "Switch to Auction", lore = listOf("Click to switch!")) else it }
	}

	@Test
	fun switchAuctionToBin() {
		val (t, s) = ah("ah-create-auction")
		val p = AhModel.parse(t, s)!!
		assertEquals(AhModel.AuctionType.AUCTION, AhModel.auctionType(p))
		assertEquals(48, p.button(AhControl.SWITCH_TYPE)?.slot)

		val c = dev.ashtonite365.apex.features.menus.ah.AhStateCache<String>()
		c.update(p, "a", 0, AhKind.CREATE, p.title)
		val (bt, bs) = binVariant()
		val empty = c.update(null, "b", 10, AhKind.CREATE, bt)
		assertNull(empty.page, "Create Auction must not stand in for Create BIN Auction")
		val bin = c.update(AhModel.parse(bt, bs), "b", 20, AhKind.CREATE, bt)
		assertEquals(false, bin.loading)
		assertEquals(AhModel.AuctionType.BIN, AhModel.auctionType(bin.page!!))
		assertEquals(48, bin.page!!.button(AhControl.SWITCH_TYPE)?.slot)
	}

	@Test
	fun switchBinToAuction() {
		val (bt, bs) = binVariant()
		val b = AhModel.parse(bt, bs)!!
		assertEquals(AhKind.CREATE, b.kind, "Create BIN Auction is claimed by the AH menu")
		assertTrue(AhModel.ANY.matches(bt))
		assertEquals(AhModel.AuctionType.BIN, AhModel.auctionType(b))
		assertEquals(48, b.button(AhControl.SWITCH_TYPE)?.slot)
		val c = dev.ashtonite365.apex.features.menus.ah.AhStateCache<String>()
		c.update(b, "b", 0, AhKind.CREATE, bt)
		val (t, s) = ah("ah-create-auction")
		assertNull(c.update(null, "a", 10, AhKind.CREATE, t).page, "Create BIN Auction must not stand in for Create Auction")
		val a = c.update(AhModel.parse(t, s), "a", 20, AhKind.CREATE, t)
		assertEquals(false, a.loading)
		assertEquals(AhModel.AuctionType.AUCTION, AhModel.auctionType(a.page!!))
	}
}
