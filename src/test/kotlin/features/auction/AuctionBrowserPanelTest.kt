package dev.ashtonite365.apex.test.features.auction

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.auction.AuctionBrowserPanel

class AuctionBrowserPanelTest {

	private fun fixture(): List<AuctionBrowserPanel.SlotItem> {
		val text = javaClass.getResourceAsStream("/ah-browser-1941.txt")!!.readBytes().decodeToString()
		val re = Regex("""slot=(\d+) name=(.*?) lore=\[(.*)]$""")
		return text.lines().mapNotNull { re.find(it) }.map { m ->
			AuctionBrowserPanel.SlotItem(m.groupValues[1].toInt(), m.groupValues[2].trim(), m.groupValues[3].split(", "),
				if (m.groupValues[2] == "God Potion") "GOD_POTION_2" else null, 1)
		}
	}

	@Test
	fun everyBidAuctionIsListedEvenWithBinOnly() {
		val items = fixture()
		val listings = items.count { it.lore.any { l -> l.startsWith("Seller:") } }
		Assertions.assertTrue(listings >= 20)
		for (binFirst in listOf(false, true)) {
			val rows = AuctionBrowserPanel.rowsFrom(items, AuctionBrowserPanel.Sort.PRICE_ASC, binFirst) { null }
			Assertions.assertEquals(listings, rows.size)
			Assertions.assertTrue(rows.all { it.listing.price != null })
		}
	}

	@Test
	fun bidsComparedWithReferenceAndNoDataShowsQuestionMark() {
		val rows = AuctionBrowserPanel.rowsFrom(fixture(), AuctionBrowserPanel.Sort.PRICE_ASC, false) { id -> if (id == "GOD_POTION_2") 100_000_000.0 else null }
		val giant = rows.filter { it.listing.itemName.contains("God Potion") }
		Assertions.assertTrue(giant.isNotEmpty() && giant.all { it.lbinDiffPct != null })
		Assertions.assertTrue(rows.any { it.lbinDiffPct == null })
		Assertions.assertEquals(" §7?", AuctionBrowserPanel.diffText(null))
		Assertions.assertEquals(" §c+20%", AuctionBrowserPanel.diffText(20.0))
		Assertions.assertEquals(" §a-5%", AuctionBrowserPanel.diffText(-5.0))
	}
}

class AuctionBrowserPanelScrollTest {
	private val P = dev.ashtonite365.apex.features.auction.AuctionBrowserPanel
	@org.junit.jupiter.api.Test fun clampAndEnsureVisible() {
		org.junit.jupiter.api.Assertions.assertEquals(14, P.clampScroll(99, 10, 24))
		org.junit.jupiter.api.Assertions.assertEquals(0, P.clampScroll(-3, 10, 24))
		org.junit.jupiter.api.Assertions.assertEquals(11, P.ensureVisible(0, 20, 10, 24))
		org.junit.jupiter.api.Assertions.assertEquals(3, P.ensureVisible(5, 3, 10, 24))
		org.junit.jupiter.api.Assertions.assertEquals(5, P.ensureVisible(5, 8, 10, 24))
	}
	@org.junit.jupiter.api.Test fun resetOnPageChange() {
		org.junit.jupiter.api.Assertions.assertEquals(7, P.scrollFor("a", "a", 7))
		org.junit.jupiter.api.Assertions.assertEquals(0, P.scrollFor("a", "b", 7))
	}
	@org.junit.jupiter.api.Test fun sourceAndLabel() {
		org.junit.jupiter.api.Assertions.assertEquals("1d avg", P.referenceSource(15_000.0, 200_000.0, null, null))
		org.junit.jupiter.api.Assertions.assertEquals("LBIN", P.referenceSource(190_000.0, 200_000.0, null, null))
		org.junit.jupiter.api.Assertions.assertNull(P.referenceSource(null, null, null, null))
		org.junit.jupiter.api.Assertions.assertEquals("LBIN 200k, 3m ago", P.refLabel("LBIN", 200_000.0, 1L, 180_001L) { "200k" })
		org.junit.jupiter.api.Assertions.assertEquals("no price data", P.refLabel(null, null, 1L, 2L) { "" })
	}
}
