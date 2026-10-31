// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.ah

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.ah.AhControl
import dev.ashtonite365.apex.features.menus.ah.AhKind
import dev.ashtonite365.apex.features.menus.ah.AhLane
import dev.ashtonite365.apex.features.menus.ah.AhModel
import dev.ashtonite365.apex.features.menus.ah.AhSlot

class AhModelTest {
	private fun s(i: Int, name: String, vararg lore: String) = AhSlot(i, name, lore.toList(), true)
	private val pane = { i: Int -> AhSlot(i, "", emptyList(), true, filler = true) }

	@Test
	fun titles() {
		assertEquals(AhKind.BROWSER, AhModel.kindOf("Auctions Browser"))
		assertEquals(AhKind.BROWSER, AhModel.kindOf("§8Auctions: \"hyperion\""))
		assertEquals(AhKind.BROWSER, AhModel.kindOf("Auctions: \"\""))
		assertEquals(AhKind.BROWSER, AhModel.kindOf("Auctions: \"a very long search te"))
		assertEquals(AhKind.BROWSER, AhModel.kindOf("Steve's Auctions"))
		assertEquals(AhKind.VIEW, AhModel.kindOf("BIN Auction View"))
		assertEquals(AhKind.VIEW, AhModel.kindOf("Auction View"))
		assertEquals(AhKind.CONFIRM, AhModel.kindOf("Confirm Purchase"))
		assertEquals(AhKind.CONFIRM, AhModel.kindOf("Confirm Bid"))
		assertEquals(AhKind.CREATE, AhModel.kindOf("Create BIN Auction"))
		assertEquals(AhKind.CREATE, AhModel.kindOf("Create Auction"))
		assertEquals(AhKind.MANAGE, AhModel.kindOf("Manage Auctions"))
		assertEquals(AhKind.MANAGE, AhModel.kindOf("Your Bids"))
		assertEquals(AhKind.MANAGE, AhModel.kindOf("View Bids"))
		assertNull(AhModel.kindOf("Auction House"))
		assertNull(AhModel.kindOf("Co-op Auction House"))
		assertNull(AhModel.kindOf("Bazaar ➜ Farming"))
		assertTrue(AhModel.ANY.matches("Create BIN Auction"))
		assertTrue(!AhModel.ANY.matches("Auction House"))
	}

	@Test
	fun browserPage() {
		val slots = listOf(
			s(0, "Weapons", "Currently browsing!"), s(9, "Armor"), pane(1),
			s(11, "Hyperion", "Seller: [MVP+] Steve", "Buy it now: 800,000,000 coins", "Ends in: 2h"),
			s(12, "Aspect of the End", "Seller: Alex", "Top bid: 120,000 coins", "Bids: 3 bids", "Ends in: 5m"),
			s(13, "Random Lore Item", "nothing useful"),
			s(48, "Search", "Find items by name"), s(50, "Sort", "  Highest Bid", "▶ Lowest Price"),
			s(52, "BIN Filter", "▶ BIN Only"), s(53, "Next Page", "Page 2"),
		)
		val p = AhModel.parse("Auctions Browser", slots)
		assertNotNull(p); p!!
		assertEquals(2, p.cards.size)
		assertEquals(true, p.cards[0].listing!!.isBin)
		assertEquals(3, p.cards[1].listing!!.bids)
		assertEquals(listOf("Weapons", "Armor"), p.categories.map { it.name })
		assertTrue(p.categories[0].selected)
		assertEquals("Lowest Price", p.button(AhControl.SORT)!!.selection)
		assertEquals(53, p.button(AhControl.NEXT)!!.slot)
		assertNull(p.button(AhControl.PREV))
	}

	@Test
	fun fallbackWhenUnrecognised() {

		assertNull(AhModel.parse("Auctions Browser", listOf(s(11, "Mystery", "??"))))
		assertNull(AhModel.parse("Auctions Browser", listOf(pane(0), pane(1))))
		assertNull(AhModel.parse("Some Other Menu", listOf(s(48, "Search"))))
		assertNull(AhModel.parse("Create BIN Auction", listOf(s(13, "Hyperion"))))
	}

	@Test
	fun manageKanban() {
		val p = AhModel.parse("Manage Auctions", listOf(
			s(10, "Hyperion", "Seller: Me", "Buy it now: 1,000 coins", "Status: Sold!", "Click to collect coins!"),
			s(11, "Juju", "Starting bid: 5 coins", "Ends in: 1d"),
			s(12, "Terminator", "Top bid: 9 coins", "You were outbid!"),
			s(13, "Claim All"), s(49, "Go Back"),
		))!!
		assertEquals(mapOf(AhLane.ENDED to 1, AhLane.ACTIVE to 1, AhLane.OUTBID to 1), p.cards.groupingBy { it.lane }.eachCount())
		assertEquals(13, p.button(AhControl.CLAIM_ALL)!!.slot)
		assertEquals(49, p.button(AhControl.BACK)!!.slot)
	}

	@Test
	fun wizardPages() {
		val create = AhModel.parse("Create BIN Auction", listOf(
			pane(0), s(13, "Hyperion", "Damage: +260"), s(31, "Item Price: 500,000 coins", "Click to edit"),
			s(33, "Duration: 48 Hours"), s(29, "Create BIN Auction", "Click to create"), s(49, "Go Back"),
		))!!
		assertEquals(13, create.hero!!.index)
		assertEquals(29, create.button(AhControl.CREATE)!!.slot)
		assertEquals(31, create.button(AhControl.PRICE)!!.slot)
		assertEquals(33, create.button(AhControl.DURATION)!!.slot)
		val view = AhModel.parse("BIN Auction View", listOf(s(13, "Hyperion"), s(31, "Buy Item Right Now", "Price: 800,000,000 coins"), s(49, "Close")))!!
		assertEquals(31, view.button(AhControl.BUY)!!.slot)
		val confirm = AhModel.parse("Confirm Purchase", listOf(s(11, "Confirm"), s(13, "Hyperion"), s(15, "Cancel")))!!
		assertEquals(11, confirm.button(AhControl.CONFIRM)!!.slot); assertEquals(15, confirm.button(AhControl.CANCEL)!!.slot)
		assertEquals(13, confirm.hero!!.index)
	}

	@Test
	fun gridAndFormat() {
		assertEquals(4, AhModel.columns(360, 84, 0))
		assertEquals(1, AhModel.columns(40, 84, 0))
		assertEquals(3, AhModel.columns(360, 84, 3))
		assertEquals("1.20M", AhModel.shortCoins(1_200_000.0))
		assertEquals("800.00M", AhModel.shortCoins(8e8))
		assertEquals("?", AhModel.shortCoins(null))
	}
}

class AhHubAndInventoryTest {
	private fun h(i: Int, n: String, m: String) = dev.ashtonite365.apex.features.menus.ah.HubSlot(i, n, m)

	@org.junit.jupiter.api.Test
	fun hubTitlesAndRoles() {
		val M = dev.ashtonite365.apex.features.menus.ah.AhHubModel
		val R = dev.ashtonite365.apex.features.menus.ah.HubRole.entries.associateBy { it.name }
		assertTrue(M.TITLE.matches("Auction House")); assertTrue(M.TITLE.matches("Co-op Auction House"))
		assertTrue(!M.TITLE.matches("Auctions Browser"))

		val page = M.parse("§0Auction House", listOf(h(11, "Auctions Browser", "gold_block"), h(13, "Manage Bids", "golden_carrot"),
			h(15, "Create Auction", "golden_axe"), h(31, "Close", "barrier"), h(32, "Auction Stats", "paper"), h(0, "", "black_stained_glass_pane")))!!
		assertEquals(listOf(11, 13, 15, 31, 32), listOf("BROWSE", "BIDS", "CREATE", "CLOSE", "EXTRA").map { page[R[it]]!!.index })

		val p2 = M.parse("Co-op Auction House", listOf(h(10, "Something New", "gold_block"), h(16, "Manage Auctions", "gold_ingot"), h(31, "X", "barrier")))!!
		assertEquals(10, p2[R["BROWSE"]]!!.index); assertEquals(16, p2[R["CREATE"]]!!.index); assertEquals(31, p2[R["CLOSE"]]!!.index)
		assertNull(M.parse("Auction House", listOf(h(31, "Close", "barrier"))))
		assertNull(M.parse("Auction House", emptyList()))
	}

	@org.junit.jupiter.api.Test
	fun createInventoryGridSlotMapping() {

		val ids = AhModel.playerInvSlotIds(54)
		assertEquals(36, ids.size)
		assertEquals(54, AhModel.gridToSlotId(0, ids))
		assertEquals(80, AhModel.gridToSlotId(26, ids))
		assertEquals(81, AhModel.gridToSlotId(27, ids))
		assertEquals(89, AhModel.gridToSlotId(35, ids))
		assertNull(AhModel.gridToSlotId(36, ids)); assertNull(AhModel.gridToSlotId(-1, ids))
		assertEquals(36, AhModel.gridToSlotId(0, AhModel.playerInvSlotIds(36)))
	}
}

class AuctionBrowserPanelLayoutTest {
	private val m: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }
	private val P = dev.ashtonite365.apex.features.auction.AuctionBrowserPanel

	@org.junit.jupiter.api.Test
	fun fullNamesWiderOrTwoLines() {
		val rows = listOf("§bBIN §fWithered Dark Claymore" to "§61.7B §c+1%", "§bBIN §fHeroic Hyperion" to "§62.0B")
		val wide = P.layout(rows, 400, m, "Sort: price asc")!!
		assertTrue(!wide.twoLine); assertTrue(wide.w >= m(rows[0].first) + m(rows[0].second))
		val narrow = P.layout(rows, 200, m)!!
		assertTrue(narrow.twoLine); assertTrue(narrow.w <= 200); assertTrue(narrow.w >= m(rows[0].first))
		assertNull(P.layout(rows, 80, m))
	}
}

class AhStateCacheTest {
	private fun s(i: Int, name: String, vararg lore: String) = AhSlot(i, name, lore.toList(), true)
	private val weapons = listOf(s(0, "Weapons", "Currently browsing!"), s(9, "Armor"),
		s(11, "Hyperion", "Buy it now: 1,000 coins"), s(48, "Search"), s(53, "Next Page"))

	@Test
	fun keepsTabsAndGridThroughCategoryReopen() {
		val c = dev.ashtonite365.apex.features.menus.ah.AhStateCache<String>()
		val first = c.update(AhModel.parse("Auctions Browser", weapons), "stacks-1", 0)
		assertEquals(false, first.loading); assertEquals(2, first.page!!.categories.size)

		val empty = c.update(AhModel.parse("Auctions Browser", emptyList()), "stacks-empty", 100, AhKind.BROWSER)
		assertTrue(empty.loading); assertEquals(2, empty.page!!.categories.size); assertEquals("stacks-1", empty.extra)
		assertTrue(c.covering(100))
		val half = c.update(AhModel.parse("Auctions Browser", listOf(s(48, "Search"))), "stacks-half", 200, AhKind.BROWSER)
		assertTrue(half.loading); assertEquals("Weapons", half.page!!.categories.first().name)

		val armor = c.update(AhModel.parse("Auctions Browser", listOf(s(0, "Weapons"), s(9, "Armor", "Currently browsing!"), s(48, "Search"))), "stacks-2", 300)
		assertEquals(false, armor.loading); assertTrue(armor.page!!.categories[1].selected); assertEquals("stacks-2", armor.extra)
		assertTrue(!c.covering(300))
	}

	@Test
	fun givesUpAfterHoldAndNeverCoversOtherPages() {
		val c = dev.ashtonite365.apex.features.menus.ah.AhStateCache<String>()
		c.update(AhModel.parse("Auctions Browser", weapons), "a", 0)
		assertTrue(c.update(null, "b", 10, AhKind.BROWSER).loading)
		val late = c.update(null, "b", 10 + dev.ashtonite365.apex.features.menus.ah.AhStateCache.HOLD_MS, AhKind.BROWSER)
		assertNull(late.page); assertTrue(!c.covering(2000))
		val c2 = dev.ashtonite365.apex.features.menus.ah.AhStateCache<String>()
		c2.update(AhModel.parse("Auctions Browser", weapons), "a", 0)
		assertNull(c2.update(null, "x", 5, AhKind.VIEW).page)
	}
}

class AhTileCountsTest {
	private val C = dev.ashtonite365.apex.features.menus.ah.AhTileCounts

	@Test
	fun parsesTooltipCounts() {
		assertEquals("3", C.parse(listOf("View and manage your bids.", "You have 3 active bids")))
		assertEquals("2/14", C.parse(listOf("Auctions: 2 of 14 slots used")))
		assertEquals("5", C.parse(listOf("Auctions: 5")))
		assertEquals("1200", C.parse(listOf("You own 1,200 items")))
		assertNull(C.parse(listOf("Starting bid: 1,000 coins", "Click to browse!")))
		assertNull(C.parse(emptyList()))
	}

	@Test
	fun perProfileCacheUpdatesOnRescan() {
		val s = dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Store()
		assertEquals("3" to true, C.update(s, "p1", "view bids", listOf("You have 3 active bids"), 1))
		assertEquals("3" to false, C.update(s, "p1", "view bids", null, 2))
		assertEquals(null to false, C.update(s, "p2", "view bids", null, 2))
		assertEquals("4" to true, C.update(s, "p1", "view bids", listOf("You have 4 active bids"), 3))
		assertEquals(3L, s.profiles["p1"]!!["view bids"]!!.at)
	}

	@Test
	fun persistsAsJson() {
		val s = dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Store()
		C.update(s, "p1", "view bids", listOf("You have 7 active bids"), 9)
		val json = kotlinx.serialization.json.Json.encodeToString(dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Store.serializer(), s)
		val back = kotlinx.serialization.json.Json.decodeFromString(dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Store.serializer(), json)
		assertEquals("7" to false, C.update(back, "p1", "view bids", null, 10))
	}
}

class AhTileCountsViewsTest {
	private val C = dev.ashtonite365.apex.features.menus.ah.AhTileCounts

	@Test
	fun viewTooltipsParse() {
		assertEquals("3", C.parse(listOf("Claim all your ended auctions.", "You have 3 items to claim")))
		assertEquals("2/14", C.parse(listOf("Set up a new auction.", "Auctions: 2/14")))
		assertEquals("5", C.parse(listOf("Seller: Ashton", "Bids: 5", "Starting bid: 1,000 coins")))
		assertNull(C.parse(listOf("Price: 1,500,000 coins", "Click to edit")))
		assertNull(C.parse(listOf("Duration: 6 Hours")))
	}

	@Test
	fun keysSeparatePageKindsSharedCache() {
		assertEquals("manage:create auction", C.key("MANAGE", " Create Auction "))
		val s = dev.ashtonite365.apex.features.menus.ah.AhTileCounts.Store()
		C.update(s, "p1", C.key("MANAGE", "Create Auction"), listOf("Auctions: 2/14"), 1)
		C.update(s, "p1", C.key("hub", "Create Auction"), listOf("You have 4 active auctions"), 1)
		assertEquals("2/14" to false, C.update(s, "p1", C.key("MANAGE", "Create Auction"), null, 2))
		assertEquals("4" to false, C.update(s, "p1", C.key("hub", "Create Auction"), null, 2))
	}
}
