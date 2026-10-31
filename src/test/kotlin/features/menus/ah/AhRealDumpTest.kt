package dev.ashtonite365.apex.test.features.menus.ah

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.ah.AhControl
import dev.ashtonite365.apex.features.menus.ah.AhHubModel
import dev.ashtonite365.apex.features.menus.ah.AhKind
import dev.ashtonite365.apex.features.menus.ah.AhModel
import dev.ashtonite365.apex.features.menus.ah.AhSlot
import dev.ashtonite365.apex.features.menus.ah.AhStatsParser
import dev.ashtonite365.apex.features.menus.ah.AhTileCounts
import dev.ashtonite365.apex.features.menus.ah.HubRole
import dev.ashtonite365.apex.features.menus.ah.HubSlot
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class AhRealDumpTest {
	private fun ah(name: String): Pair<String, List<AhSlot>> = RealDumpFixtures.load(name).let { d ->
		d.title to d.slots.map { AhSlot(it.slot, it.name.trim(), it.lore, it.name.isNotBlank(), it.name.isBlank()) }
	}

	@Test
	fun hub() {
		val d = RealDumpFixtures.load("ah-hub")
		assertEquals("Auction House", d.title)
		val roles = AhHubModel.parse(d.title, d.slots.map { HubSlot(it.slot, it.name, "") })
		assertNotNull(roles)
		assertEquals(mapOf(HubRole.BROWSE to 11, HubRole.BIDS to 13, HubRole.CREATE to 15, HubRole.CLOSE to 31, HubRole.EXTRA to 32),
			roles!!.mapValues { it.value.index })
		assertEquals("Auction Stats", roles[HubRole.EXTRA]!!.name)
	}

	@Test
	fun browser() {
		val (t, s) = ah("ah-browser")
		val p = AhModel.parse(t, s); assertNotNull(p); p!!
		assertEquals(AhKind.BROWSER, p.kind)
		assertEquals(listOf("Weapons", "Armor", "Accessories", "Consumables", "Cosmetics", "Tools & Misc"), p.categories.map { it.name })
		for (c in listOf(AhControl.SEARCH, AhControl.BACK, AhControl.SORT, AhControl.RARITY, AhControl.BIN_FILTER, AhControl.NEXT))
			assertNotNull(p.button(c), "$c")
		assertEquals("Highest Bid", p.button(AhControl.SORT)!!.selection)
		assertEquals("Auctions Only", p.button(AhControl.BIN_FILTER)!!.selection)
		assertEquals(1 to 38, p.page to p.pages)
		assertTrue(p.cards.size >= 20, "cards: ${p.cards.size}")
		assertNotNull(AhModel.parse(ah("ah-browser-2").first, ah("ah-browser-2").second))
	}

	@Test
	fun create() {
		val (t, s) = ah("ah-create")
		val p = AhModel.parse(t, s); assertNotNull(p); p!!
		assertEquals(AhKind.CREATE, p.kind)
		assertEquals(29, p.button(AhControl.CREATE)!!.slot)
		assertEquals(31, p.button(AhControl.PRICE)!!.slot)
		assertEquals(33, p.button(AhControl.DURATION)!!.slot)
		assertEquals(49, p.button(AhControl.BACK)!!.slot)
	}

	@Test
	fun bidsAndView() {
		val (t, s) = ah("ah-bids")
		val b = AhModel.parse(t, s); assertNotNull(b); b!!
		assertEquals(AhKind.MANAGE, b.kind)
		assertEquals(listOf(10), b.cards.map { it.slot })
		assertEquals(22, b.button(AhControl.BACK)!!.slot)
		val (vt, vs) = ah("ah-view")
		val v = AhModel.parse(vt, vs); assertNotNull(v); v!!
		assertEquals(AhKind.VIEW, v.kind)
		assertEquals(13, v.hero!!.index)
		assertEquals(29, v.button(AhControl.BID)!!.slot)
		assertEquals(49, v.button(AhControl.BACK)!!.slot)
		assertEquals("26", AhTileCounts.parse(vs.first { it.index == 33 }.lore))
	}

	@Test
	fun auctionStats() {
		val d = RealDumpFixtures.load("ah-stats")
		val m = AhStatsParser.parse(d.title, d.slots); assertNotNull(m); m!!
		assertEquals(listOf(84L, 59L, 14L, 0L, 173_380_953L, 3_966_716L), listOf(m.created, m.withBids, m.withoutBids, m.highestHeld, m.earned, m.fees))
		assertEquals(listOf(204L, 265L, 26_000_000L, 498_835_013L), listOf(m.won, m.bids, m.highestBid, m.spent))
		assertEquals(mapOf("Common" to 9L, "Uncommon" to 4L, "Rare" to 6L, "Epic" to 25L, "Legendary" to 14L, "Mythic" to 0L, "Special" to 1L, "Ultimate" to 0L), m.sold)
		assertEquals(mapOf("Common" to 10L, "Uncommon" to 34L, "Rare" to 59L, "Epic" to 56L, "Legendary" to 39L, "Mythic" to 0L, "Special" to 6L, "Ultimate" to 0L), m.bought)
		assertEquals(31, m.backSlot)
		assertEquals(173_380_953L - 3_966_716L - 498_835_013L, m.netProfit)
		assertEquals(59.0 / 84, m.sellThrough!!, 1e-9)
		assertEquals(173_380_953.0 / 59, m.averageSale!!, 1e-6)
		assertEquals(204.0 / 265, m.winRate!!, 1e-9)
		assertEquals("70.2%", AhStatsParser.pct(m.sellThrough))
	}

	@Test
	fun statsTitlesGuardsAndFallback() {
		for (t in listOf("Auction Stats", "§0Auction Stats", "auction  stats", "Co-op Auction Stats")) assertTrue(AhStatsParser.TITLE.matches(dev.ashtonite365.apex.features.menus.support.MenuText.clean(t)), t)
		assertTrue(!AhStatsParser.TITLE.matches("Auction House"))
		assertNull(AhStatsParser.ratio(5, 0)); assertNull(AhStatsParser.ratio(null, 3)); assertNull(AhStatsParser.ratio(3, null))
		val empty = AhStatsParser.parse("Auction Stats", listOf(dev.ashtonite365.apex.features.menus.support.SlotText(11, "Seller Stats", listOf("Auctions created: 0"))))!!
		assertNull(empty.sellThrough); assertNull(empty.averageSale); assertNull(empty.winRate); assertNull(empty.netProfit)
		assertNull(AhStatsParser.parse("Auction Stats", listOf(dev.ashtonite365.apex.features.menus.support.SlotText(31, "Go Back"))))
	}
}
