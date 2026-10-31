// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.auction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexmarket.FlipHistory
import dev.ashtonite365.apex.features.auction.AhSoldPrice
import dev.ashtonite365.apex.features.auction.AuctionListingParser
import dev.ashtonite365.apex.features.menus.ah.AhControl
import dev.ashtonite365.apex.features.menus.ah.AhKind
import dev.ashtonite365.apex.features.menus.ah.AhLane
import dev.ashtonite365.apex.features.menus.ah.AhModel
import dev.ashtonite365.apex.features.menus.ah.AhSlot
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class AhSoldPriceTest {
	private val chat = javaClass.getResource("/apex-fixtures/ah-sold-1427-chat.txt")!!.readText().lines().filter { it.isNotBlank() }
	private val SCEPTRE = "Legendary Spirit Sceptre ✪✪✪✪✪"
	private val AOTD = "Fabled Aspect of the Dragons ✪✪✪✪✪"

	private fun ah(name: String): Pair<String, List<AhSlot>> = RealDumpFixtures.load(name).let { d ->
		d.title to d.slots.map { AhSlot(it.slot, it.name.trim(), it.lore, it.name.isNotBlank(), it.name.isBlank()) }
	}
	private fun pairs(name: String) = RealDumpFixtures.load(name).let { d -> d.title to d.slots.map { it.name to it.lore } }

	@Test
	fun fixtureLinesAreTheRealOnes() {
		assertEquals("§6[Auction] §b_YaoNexxus §ebought §fLegendary Spirit Sceptre ✪✪✪✪✪ §efor §610,000,000 coins §lCLICK", chat[0])
		assertEquals("§6[Auction] §bkuyyf §ebought §fFabled Aspect of the Dragons ✪✪✪✪✪ §efor §62,000,000 coins §lCLICK", chat[1])
		assertEquals("You collected 1,980,000 coins from selling Fabled Aspect of the Dragons ✪✪✪✪✪ to [MVP+] kuyyf in an auction!", chat[3])
	}

	@Test
	fun soldChatWithColourCodesAndClick() {
		val a = AhSoldPrice.parseSoldChat(chat[0])!!
		assertEquals(SCEPTRE, a.item); assertEquals("_YaoNexxus", a.buyer); assertEquals(10_000_000L, a.gross)
		val b = AhSoldPrice.parseSoldChat(chat[1])!!
		assertEquals(AOTD, b.item); assertEquals("kuyyf", b.buyer); assertEquals(2_000_000L, b.gross)

		assertEquals(a, AhSoldPrice.parseSoldChat(AhSoldPrice.cleanLine(chat[0])))
		assertEquals(a, AhSoldPrice.parseSoldChat("[Auction] _YaoNexxus bought Legendary Spirit Sceptre ✪✪✪✪✪ for 10,000,000 coins CLICK"))
		assertNull(AhSoldPrice.parseSoldChat(chat[2]))
	}

	@Test
	fun collectedChatIsAfterTax() {
		val a = AhSoldPrice.parseCollectedChat(chat[2])!!
		assertEquals(SCEPTRE, a.item); assertEquals("_YaoNexxus", a.buyer); assertEquals(9_900_000L, a.net); assertNull(a.gross)
		val b = AhSoldPrice.parseCollectedChat(chat[3])!!
		assertEquals(AOTD, b.item); assertEquals("kuyyf", b.buyer); assertEquals(1_980_000L, b.net)
		assertNull(AhSoldPrice.parseCollectedChat("You collected 9,726 coins back from an auction which you didn't hold the top bid!"))
	}

	@Test
	fun bidWording() {

		val b = AhSoldPrice.parseBidChat("§6[Auction] §bkuyyf §ebid §61,500,000 coins §eon §fFabled Aspect of the Dragons ✪✪✪✪✪ §lCLICK")!!
		assertEquals(AOTD, b.item); assertEquals("kuyyf", b.bidder); assertEquals(1_500_000L, b.amount)
		assertNull(AhSoldPrice.parseSoldChat("§6[Auction] §bkuyyf §ebid §61,500,000 coins §eon §fFabled Aspect of the Dragons ✪✪✪✪✪ §lCLICK"))

		val s = AhSoldPrice.parseLore("Hyperion", listOf("Seller: Ashtonite365", "Buyer: [MVP+] kuyyf", "Top bid: 1,500,000 coins", "Status: Ended!"))!!
		assertEquals(1_500_000L, s.gross); assertEquals("kuyyf", s.buyer)

		assertNull(AhSoldPrice.parseLore("Hyperion", listOf("Seller: Ashtonite365", "Top bid: 1,500,000 coins", "Bidder: [MVP+] kuyyf", "Ends in: 11h")))
	}

	@Test
	fun trackerMergesLiveChat() {
		val t = AhSoldPrice.Tracker()
		chat.forEach { l -> AhSoldPrice.parseSoldChat(l)?.let(t::record) ?: AhSoldPrice.parseCollectedChat(l)?.let(t::collected) }
		val s = t.find(SCEPTRE)!!
		assertEquals(10_000_000L, s.gross); assertEquals(9_900_000L, s.net); assertEquals("_YaoNexxus", s.buyer)
		val a = t.find("§f$AOTD")!!
		assertEquals(2_000_000L, a.gross); assertEquals(1_980_000L, a.net); assertEquals("kuyyf", a.buyer)
		assertEquals(2, t.sales.size)
	}

	@Test
	fun nameNormalisation() {
		assertEquals("legendary spirit sceptre", AhSoldPrice.normaliseName("§fLegendary Spirit Sceptre ✪✪✪✪✪"))
		assertEquals("heroic ice spray wand", AhSoldPrice.normaliseName(" Heroic Ice Spray Wand ✪✪✪✪✪"))
		assertTrue(AhSoldPrice.sameItem("Spirit Sceptre ✪✪✪✪✪", SCEPTRE))
		assertTrue(AhSoldPrice.sameItem("Fabled Aspect of the Dragons ➋", AOTD))
		assertFalse(AhSoldPrice.sameItem("Spirit Sceptre", "Legendary Spirit Bow"))
		assertFalse(AhSoldPrice.sameItem("Sceptre", "Scepter"))
		assertFalse(AhSoldPrice.sameItem("ceptre", "Spirit Sceptre"))
	}

	@Test
	fun manageBeforeSaleHasNoSale() {
		val (t, s) = ah("ah-sold-1427-manage-before")
		val p = AhModel.parse(t, s)!!
		assertEquals(AhKind.MANAGE, p.kind)
		val sceptre = p.cards.single { it.name == SCEPTRE }
		assertEquals(AhLane.ACTIVE, sceptre.lane); assertEquals(10_000_000.0, sceptre.listing!!.price); assertNull(sceptre.listing.soldFor)
		assertEquals(2_000_000.0, p.cards.single { it.name == AOTD }.listing!!.price)
		val (pt, ps) = pairs("ah-sold-1427-manage-before")
		assertTrue(AhSoldPrice.readPage(pt, ps).isEmpty())
	}

	@Test
	fun manageAfterSaleReadsSoldFor() {
		val (t, s) = ah("ah-sold-1427-manage-sold")
		val p = AhModel.parse(t, s)!!
		val sceptre = p.cards.single { it.name == SCEPTRE }
		assertEquals(AhLane.ENDED, sceptre.lane)
		assertEquals(10_000_000.0, sceptre.listing!!.price)
		assertEquals(10_000_000.0, sceptre.listing.soldFor); assertEquals("_YaoNexxus", sceptre.listing.buyer)
		val aotd = p.cards.single { it.name == AOTD }
		assertEquals(AhLane.ENDED, aotd.lane); assertEquals(2_000_000.0, aotd.listing!!.soldFor); assertEquals("kuyyf", aotd.listing.buyer)

		val wand = p.cards.single { it.name == "\uE068 Heroic Ice Spray Wand ✪✪✪✪✪" }
		assertTrue(AhSoldPrice.sameItem(wand.name, "Heroic Ice Spray Wand ✪✪✪✪✪"))
		assertEquals(AhLane.ACTIVE, wand.lane); assertEquals(20_000_000.0, wand.listing!!.price); assertNull(wand.listing.soldFor)
		val (pt, ps) = pairs("ah-sold-1427-manage-sold")
		val sales = AhSoldPrice.readPage(pt, ps)
		assertEquals(listOf(SCEPTRE to 10_000_000L, AOTD to 2_000_000L), sales.map { it.item to it.gross })
		assertEquals(listOf("_YaoNexxus", "kuyyf"), sales.map { it.buyer })

		val (qt, qs) = pairs("ah-sold-1427-manage-sold2")
		assertEquals(listOf(AOTD to 2_000_000L), AhSoldPrice.readPage(qt, qs).map { it.item to it.gross })
	}

	@Test
	fun binAuctionViewReadsHeroAndCollect() {
		for ((fixture, item, buyer, price) in listOf(
			listOf("ah-sold-1427-binview-sceptre", SCEPTRE, "_YaoNexxus", 10_000_000L),
			listOf("ah-sold-1427-binview-aotd", AOTD, "kuyyf", 2_000_000L))) {
			val (t, s) = ah(fixture as String)
			val p = AhModel.parse(t, s)!!
			assertEquals(AhKind.VIEW, p.kind)
			assertEquals(31, p.button(AhControl.COLLECT)!!.slot)
			val sale = AhModel.soldOnView(p)!!
			assertEquals(item, sale.item); assertEquals(buyer, sale.buyer); assertEquals(price, sale.gross)

			val c = AhSoldPrice.parseLore(item as String, p.button(AhControl.COLLECT)!!.lore)!!
			assertEquals(price, c.gross); assertEquals(buyer, c.buyer)
			val (pt, ps) = pairs(fixture)
			assertEquals(listOf(item to price), AhSoldPrice.readPage(pt, ps).map { it.item to it.gross })
		}
	}

	@Test
	fun collectLoreWithoutHeroSoldFor() {
		val (t, s) = pairs("ah-sold-1427-binview-sceptre")

		val stripped = s.map { (n, l) -> n to l.filterNot { it.startsWith("Sold for") } }
		assertEquals(listOf(SCEPTRE to 10_000_000L), AhSoldPrice.readPage(t, stripped).map { it.item to it.gross })
	}

	@Test
	fun createAndConfirmBin() {
		val create = RealDumpFixtures.load("ah-sold-1427-create-bin")
		val submit = create.slots.single { it.slot == 29 }
		assertEquals(SCEPTRE to 10_000_000L, AhSoldPrice.parseListedPrice(submit.lore, submit.name))
		val priceBtn = create.slots.single { it.slot == 31 }
		assertEquals(10_000_000L, AhSoldPrice.parseListedPrice(priceBtn.lore, priceBtn.name)!!.second)
		assertFalse(AhSoldPrice.isSoldPage(create.title))
		val confirm = RealDumpFixtures.load("ah-sold-1427-confirm-bin")
		assertEquals("Confirm BIN Auction", confirm.title)
		assertTrue(AhSoldPrice.readPage(confirm.title, confirm.slots.map { it.name to it.lore }).isEmpty())

		assertNull(AhSoldPrice.parseLore("Confirm BIN Auction", confirm.slots.single { it.slot == 11 }.lore))
	}

	@Test
	fun listingParserKeepsCommaNumbers() {
		val l = AuctionListingParser.parse(SCEPTRE, listOf("Seller: Ashtonite365", "Buyer: [MVP+] _YaoNexxus", "", "Sold for: 10,000,000 coins", "", "Status: Sold!"))!!
		assertEquals(10_000_000.0, l.price); assertEquals(10_000_000.0, l.soldFor); assertEquals("_YaoNexxus", l.buyer); assertEquals("Ashtonite365", l.seller)
	}

	@Test
	fun flipHistoryMatchesReforgedNameAndCollected() {
		val d = FlipHistory.Data()
		FlipHistory.recordBuy(d, "Spirit Sceptre ✪✪✪✪✪", 1, 8_000_000.0)
		val sold = AhSoldPrice.parseSoldChat(chat[0])!!
		val f = FlipHistory.recordAhSale(d, sold.item, sold.gross!! * 0.98, 0, 10)!!
		assertEquals(8_000_000.0, f.buyPerUnit); assertEquals(9_800_000.0, f.sellPerUnit, 0.01)
		val got = AhSoldPrice.parseCollectedChat(chat[2])!!
		val g = FlipHistory.applyCollected(d, got.item, got.net!!.toDouble())!!
		assertEquals(9_900_000.0, g.sellPerUnit); assertEquals(1_900_000.0, g.profit, 0.01)
		assertEquals(g, d.flips.first())
		assertTrue(d.lots.isEmpty())

		assertNull(FlipHistory.recordAhSale(d, AOTD, 1_960_000.0, 0, 10))
	}
}
