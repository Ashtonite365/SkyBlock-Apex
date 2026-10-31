package dev.ashtonite365.apex.test.features.auction
import dev.ashtonite365.apex.features.auction.AhHoverLink
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class AhHoverLinkTest {
	@Test fun rowHoverLightsItsCard() {
		assertTrue(AhHoverLink.cardHighlighted(12, false, 12))
		assertFalse(AhHoverLink.cardHighlighted(13, false, 12))
		assertTrue(AhHoverLink.cardHighlighted(13, true, null))
	}
	@Test fun apexCardHoverPicksRow() {
		assertEquals(2, AhHoverLink.rowFor(listOf(11, 12, 20), 20, null))
		assertEquals(0, AhHoverLink.rowFor(listOf(11, 12, 20), null, 11))
		assertEquals(-1, AhHoverLink.rowFor(listOf(11, 12), 99, null))
		assertEquals(-1, AhHoverLink.rowFor(listOf(11, 12), null, null))
	}
}

class AuctionPanelRowClickTest {
	private val P = dev.ashtonite365.apex.features.auction.AuctionBrowserPanel
	@Test fun rowClickSendsTheTileSlot() {
		val sent = mutableListOf<Int>()
		assertTrue(P.rowClick(14, "Lapis Pickaxe", "§9Lapis Pickaxe") { sent += it })
		assertEquals(listOf(14), sent)
	}
	@Test fun rowNotInWindowIsDisabled() {
		val sent = mutableListOf<Int>()
		assertFalse(P.rowClick(14, "Lapis Pickaxe", null) { sent += it })
		assertFalse(P.rowClick(14, "Lapis Pickaxe", "Ender Pearl") { sent += it })
		assertTrue(sent.isEmpty())
		assertFalse(P.rowEnabled("Lapis Pickaxe", null))
	}
}
