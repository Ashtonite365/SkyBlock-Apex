// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CollectionPageMatchTest {

	@Test fun taylorsCollectionNotTakenOver() {
		val d = RealDumpFixtures.load("taylors-collection")
		assertEquals("Taylor's Collection", d.title)
		assertFalse(CollectionsParser.PAGES.containsMatchIn(d.title))
		assertTrue(CollectionsParser.ITEM_TITLES.containsMatchIn(d.title))
		assertFalse(CollectionsParser.isItemPage(d.slots))
		assertFalse(CollectionsParser.matchesItemPage(d.title, d.slots))
		assertNull(CollectionsParser.parse(d.title, d.slots))
		val hop = RealDumpFixtures.load("hoppity-collection-4")
		assertFalse(CollectionsParser.matchesItemPage(hop.title, hop.slots)); assertNull(CollectionsParser.parse(hop.title, hop.slots))
	}

	@Test fun realItemPagesMatch() {
		for (n in listOf("leather-collection", "cactus-collection", "carrot-collection", "pumpkin-collection", "raw-porkchop-collection",
				"coal-collection", "gravel-collection", "glowstone-dust-collection")) {
			val d = RealDumpFixtures.load(n)
			assertTrue(CollectionsParser.matchesItemPage(d.title, d.slots), n)
			assertTrue(CollectionsParser.parse(d.title, d.slots) is CollPage.Item, n)
		}

		for (n in listOf("farming-collections", "mining-collections", "boss-collections", "rift-collections"))
			assertTrue(CollectionsParser.PAGES.containsMatchIn(RealDumpFixtures.load(n).title), n)
	}

	@Test fun structureSignals() {
		val back = listOf(dev.ashtonite365.apex.features.menus.support.SlotText(48, "Go Back", listOf("To Boss Collections")))
		assertTrue(CollectionsParser.isItemPage(back))
		val head = listOf(dev.ashtonite365.apex.features.menus.support.SlotText(4, "Wheat V", listOf("View all your Wheat Collection", "progress and rewards!")))
		assertTrue(CollectionsParser.isItemPage(head))
		val other = listOf(dev.ashtonite365.apex.features.menus.support.SlotText(48, "Go Back", listOf("To Taylor's Cosmetics")))
		assertFalse(CollectionsParser.isItemPage(other))
	}
}
