// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumAppraisal
import dev.ashtonite365.apex.features.menus.museum.MuseumAppraisalData
import dev.ashtonite365.apex.features.menus.support.SlotText

class MuseumAppraisalTest {
	private val dump = RealDumpFixtures.load("museum-appraisal")

	@Test
	fun parsesRealLore() {
		val d = MuseumAppraisal.fromSlots(dump.slots)!!
		assertEquals("134,101,143", d.value)
		assertEquals("135,875", d.rank)
		assertTrue(d.lore.contains("Your Museum Value"))
	}

	@Test
	fun parsesRealLore2139ValueRankTopItems() {
		val d = MuseumAppraisal.fromSlots(RealDumpFixtures.load("museum-appraisal-2139").slots)!!
		assertEquals("134,101,143", d.value)
		assertEquals("135,878", d.rank)
		assertEquals(listOf(
			"Jaded Chestplate of Divan" to "62,536,879 Coins", "Jaded Leggings of Divan" to "62,536,879 Coins",
			"Jaded Helmet of Divan" to "54,536,879 Coins", "Jaded Boots of Divan" to "52,536,879 Coins",
			"Ancient Storm's Chestplate" to "38,805,508 Coins"), d.topItems)
		val t = MuseumAppraisal.tooltip(d)
		assertTrue(t.contains("§eAppraised value: §6134,101,143 coins"))
		assertTrue(t.contains("§eRank: §f#135,878"))
		assertTrue(t.contains("§eTop Items"))
		assertTrue(t.contains("§71. §fJaded Chestplate of Divan §8- §662,536,879 Coins"))
		assertTrue(t.contains("§75. §fAncient Storm's Chestplate §8- §638,805,508 Coins"))
	}

	@Test
	fun noValueMeansNoResult() {
		assertNull(MuseumAppraisal.fromLore(SlotText(51, "Museum Appraisal Service", listOf("Madame Goldsworth offers an", "Click to appraise!"))))
		assertNull(MuseumAppraisal.fromLore(SlotText(10, "Museum Search", listOf("Your Museum Value", "5 Coins"))))
	}

	@Test
	fun cachePerProfile() {
		val store = mutableMapOf<String, MuseumAppraisalData>()
		val a = MuseumAppraisalData("1,000", null); val b = MuseumAppraisalData("2,000", "5")
		assertTrue(MuseumAppraisal.update(store, "apple", a))
		assertTrue(MuseumAppraisal.update(store, "banana", b))
		assertFalse(MuseumAppraisal.update(store, "apple", a))
		assertFalse(MuseumAppraisal.update(store, "apple", null))
		assertEquals("1,000", store["apple"]!!.value); assertEquals("2,000", store["banana"]!!.value)
		assertTrue(MuseumAppraisal.update(store, "apple", MuseumAppraisalData("3,000")))
		assertEquals("3,000", store["apple"]!!.value)
	}

	@Test
	fun boxStatesAndTooltip() {
		assertEquals("not appraised yet", MuseumAppraisal.label(null))
		assertEquals("not appraised yet", MuseumAppraisal.label(MuseumAppraisalData()))
		assertTrue(MuseumAppraisal.tooltip(null).isEmpty())
		val d = MuseumAppraisal.fromSlots(dump.slots)!!
		assertEquals("Appraisal Service", MuseumAppraisal.label(d))
		val t = MuseumAppraisal.tooltip(d)
		assertTrue(t.contains("§7Madame Goldsworth offers an"))
		assertTrue(t.contains("§7134,101,143 Coins"))
		assertTrue(t.contains("§eAppraised value: §6134,101,143 coins"))
		assertTrue(t.contains("§eRank: §f#135,875"))
	}
}
