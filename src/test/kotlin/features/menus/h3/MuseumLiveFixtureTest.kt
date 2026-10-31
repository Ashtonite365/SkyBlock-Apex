// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumStatus
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.SlotText

class MuseumLiveFixtureTest {

	private val eleven = listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting",
		"Special Items", "Museum Milestones", "Museum Search", "Museum Appraisal Services")
	private val slotOf = mapOf("Combat" to 19, "Farming" to 20, "Mining" to 21, "Fishing" to 22, "Foraging" to 23,
		"Dungeoneering" to 24, "Hunting" to 25, "Special Items" to 32 , "Museum Milestones" to 40,
		"Museum Search" to 50, "Museum Appraisal Services" to 51)

	private val statsItem = SlotText(4, "§aMuseum", listOf(
		"§7Here you can see your overall", "§7museum progress.", "",
		"§7Items Donated: §e212§6/§e1,309", "§7Museum Value: §61.2B Coins", "§7Milestone: §e12", "",
		"§eClick to view your stats!"))
	private val overview = listOf(statsItem) + slotOf.map { (n, s) -> SlotText(s, "§a$n", listOf("§7Click to view!", "§7Items Donated: §e3§6/§e9")) } + listOf(
		SlotText(49, "§cClose"), SlotText(0, "§7 "), SlotText(1, " "), SlotText(53, "-"))

	@Test
	fun mainPageTitleExactlyAsLogged() {
		assertTrue(MuseumParser.ANY_TITLE.containsMatchIn("Your Museum"))
		assertEquals(Triple(null, null, null), MuseumParser.parseTitle("Your Museum"))
	}

	@Test
	fun categoryTitleAnySeparator() {
		for (t in listOf("Museum ➜ Special Items", "Museum ? Special Items", "Museum → Special Items", "Museum » Special Items",
			"§7Museum §8➜ §aSpecial Items", "Museum➜Special Items", "Museum \uFFFD Special Items")) {
			assertEquals("Special Items", MuseumParser.parseTitle(t)?.first, t)
		}
		assertEquals(Triple("Combat", 2, 3), MuseumParser.parseTitle("Museum ➜ Combat (2/3)"))
	}

	@Test
	fun allElevenEntriesListedAtTheirSlotsAndStatsIsTheHeader() {
		val m = MuseumParser.parse("Your Museum", overview)
		assertNotNull(m)
		val shown = eleven - "Museum Appraisal Services"
		assertEquals(shown.map { slotOf[it] }.sortedBy { it }, m!!.categories.map { it.slot })
		for (n in shown) assertEquals(slotOf[n], m.categories.single { it.name == n }.slot, n)
		assertFalse(m.categories.any { it.name == "Museum" || it.slot == 4 })
		assertFalse(m.categories.any { it.name.equals("close", true) || it.name.isBlank() || it.name == "-" })
		val st = m.stats!!
		assertEquals(4, st.slot)
		assertEquals(listOf("Items Donated: 212/1309"), st.lines)
		assertEquals(212, st.donated); assertEquals(1309, st.total)
	}

	@Test
	fun statsHeaderOnCategoryPagesToo() {
		val m = MuseumParser.parse("Museum ➜ Combat", listOf(statsItem,
			SlotText(10, "§6Some Weapon", listOf("§a✔ Donated")), SlotText(48, "§aGo Back")))!!
		assertEquals(4, m.stats?.slot)
		assertFalse(m.entries.any { it.slot == 4 })
		assertEquals(null, MuseumParser.parse("Museum ➜ Combat", listOf(SlotText(10, "§6Some Weapon", listOf("§a✔ Donated")))).let { it!!.stats })
		for (n in listOf("Museum Milestones", "Museum Search", "Museum Appraisal Services")) assertFalse(MuseumParser.STATS_NAME.matches(n), n)
	}

	@Test
	fun statsLinesPackIntoRows() {
		val rows = MuseumParser.packStatsLines(listOf("aaaa", "bb", "cccccc"), 10) { it.length }
		assertEquals(listOf("aaaa · bb", "cccccc"), rows)
	}

	@Test
	fun carouselSpecialAndMoreFromSlotOrder() {
		val t = dev.ashtonite365.apex.features.menus.museum.MuseumTabs.of(MuseumParser.parse("Your Museum", overview)!!.categories)

		assertEquals(listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting", "Special Items"), t.main.map { it.name })
		assertEquals("Special Items", t.special?.name)
		assertEquals(32, t.special?.slot)
		assertEquals(listOf("Museum Milestones", "Museum Search"), t.more.map { it.name })
		assertEquals(2, t.pages)
		assertEquals(listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering"), t.carousel(0).map { it.name })
		assertEquals(listOf("Hunting", "Special Items"), t.carousel(1).map { it.name })
		assertEquals(t.carousel(0), t.carousel(2))
		assertEquals(t.carousel(1), t.carousel(-1))
		assertEquals(1, t.pageOf("Hunting"))

		for (c in t.main + t.more) assertEquals(slotOf[c.name], c.slot, c.name)

		val moved = MuseumParser.parse("Your Museum", overview.map { if (it.name == "§aMuseum Search") it.copy(slot = 26) else it })!!
		val t2 = dev.ashtonite365.apex.features.menus.museum.MuseumTabs.of(moved.categories)
		assertTrue(t2.main.any { it.name == "Museum Search" }); assertFalse(t2.more.any { it.name == "Museum Search" })

		val t3 = dev.ashtonite365.apex.features.menus.museum.MuseumTabs.of(moved.categories.filter { it.name != "Special Items" })
		assertEquals(null, t3.special); assertTrue(t3.more.isEmpty())
	}

	@Test
	fun unknownNewEntryStillShown() {
		val m = MuseumParser.parse("Your Museum", overview + SlotText(45, "§aMuseum Rewards Plus"))!!
		assertTrue(m.categories.any { it.slot == 45 && it.name == "Museum Rewards Plus" })
	}

	@Test
	fun specialItemsPageWithLoggedGoBack() {
		val m = MuseumParser.parse("Museum ➜ Special Items", listOf(
			SlotText(10, "§6Some Special Item", listOf("§a✔ Donated")),
			SlotText(11, "§cAnother Item", listOf("§cNot donated yet")),
			SlotText(48, "§aGo Back", listOf("§7To Your Museum")),
		))!!
		assertEquals("Special Items", m.category)
		assertEquals(48, m.backSlot)
		assertEquals(listOf(MuseumStatus.DONATED, MuseumStatus.MISSING), m.entries.map { it.status })
	}

	@Test
	fun fallbackWaitsForContents() {

		assertFalse(ApexViewBase.shouldFallBack(true, 3000, 2500, false))
		assertTrue(ApexViewBase.shouldFallBack(true, 5000, 2500, false))
		assertFalse(ApexViewBase.shouldFallBack(true, 5000, 2500, true))
		assertFalse(ApexViewBase.shouldFallBack(false, 9000, 0, false))
	}

	@Test
	fun carouselPageKShowsEntries6kMinus5To6kInOrder() {
		val cats = (1..14).map { dev.ashtonite365.apex.features.menus.museum.MuseumCategory("Option $it", 9 + it, null, null) }
		val t = dev.ashtonite365.apex.features.menus.museum.MuseumTabs.of(cats)
		assertEquals(6, dev.ashtonite365.apex.features.menus.museum.MuseumTabs.PER_PAGE)
		assertEquals(3, t.pages)
		for (k in 1..t.pages) {
			val want = ((6 * (k - 1) + 1)..minOf(6 * k, cats.size)).map { "Option $it" }
			assertEquals(want, t.carousel(k - 1).map { it.name }, "page $k")
		}
		assertEquals(cats.map { it.name }, (0 until t.pages).flatMap { t.carousel(it) }.map { it.name })
		assertEquals(1, t.pageOf("Option 7")); assertEquals(2, t.pageOf("Option 14"))
	}
}
