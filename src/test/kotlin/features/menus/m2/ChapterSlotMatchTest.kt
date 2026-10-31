package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.quests.ChapterMenu
import dev.ashtonite365.apex.features.menus.quests.ChapterParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ChapterSlotMatchTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun reg() { MenuRegistry.clearForTests(); MenuRegistry.register(ChapterMenu) }
	private fun rename(slots: List<SlotText>, from: String, to: String) = slots.map { s ->
		s.copy(name = s.name.replace(from, to), lore = s.lore.map { it.replace(from, to) }) }

	@Test fun areaPageMatchesAndRenders() {
		val d = RealDumpFixtures.load("chapter-area-1022")
		reg()
		assertNull(MenuRegistry.match(d.title))
		assertEquals("chapters", MenuRegistry.match(d.title, d.slots)?.id)
		val p = ChapterParser.parse(d.title, d.slots)!!
		assertEquals("Moonglade Marsh", p.header?.cleanName)
		val g = ChapterParser.groups(p).associate { it.name to it.tiles }
		assertEquals(listOf("Chapter I"), g.getValue(ChapterParser.IN_PROGRESS).map { it.label })
		assertEquals("§a33.3% §81/3", g.getValue(ChapterParser.IN_PROGRESS)[0].info[0])
		assertEquals(6, g.getValue(ChapterParser.LOCKED).size)
		assertEquals(48, p.nav.back); assertEquals(49, p.nav.close)
	}

	@Test fun detailPageMatchesAndRenders() {
		val d = RealDumpFixtures.load("chapter-detail")
		assertEquals("Moonglade Marsh ➜ Chapter I", d.title)
		assertFalse(ChapterParser.isAreaPage(d.slots))
		assertTrue(ChapterParser.isDetailPage(d.title, d.slots))
		reg(); assertEquals("chapters", MenuRegistry.match(d.title, d.slots)?.id)
		val p = ChapterParser.parse(d.title, d.slots)!!
		assertEquals("Chapter I", p.header?.cleanName)
		val g = ChapterParser.groups(p).associate { it.name to it.tiles }
		assertEquals(listOf("Talk to Swoop"), g.getValue(ChapterParser.COMPLETE).map { it.label })
		assertEquals(setOf("Use the Fusion Machine", "Talk to David and his Hunters"), g.getValue(ChapterParser.TODO).map { it.label }.toSet())
		assertEquals(listOf("Completion Rewards"), g.getValue(ChapterParser.IN_PROGRESS).map { it.label })
		assertEquals("§b+100 Hunting Experience", g.getValue(ChapterParser.TODO)[0].info[0])
	}

	@Test fun renamedAreaStillWorks() {
		val a = RealDumpFixtures.load("chapter-area-1022")
		val area = rename(a.slots, "Moonglade Marsh", "Glimmer Fen") + SlotText(26, "Chapter VIII", listOf("Glimmer Fen", "", "Chapter VIII Progress: 50%", "1/2"))
		reg(); assertEquals("chapters", MenuRegistry.match("Glimmer Fen", area)?.id)
		val g = ChapterParser.groups(ChapterParser.parse("Glimmer Fen", area)!!).associate { it.name to it.tiles }
		assertEquals(listOf("Chapter I", "Chapter VIII"), g.getValue(ChapterParser.IN_PROGRESS).map { it.label })

		val d = RealDumpFixtures.load("chapter-detail")
		val det = rename(rename(d.slots, "Moonglade Marsh", "Glimmer Fen"), "Chapter I", "Prologue")
		assertTrue(ChapterParser.isDetailPage("Glimmer Fen ➜ Prologue", det))
		assertEquals("chapters", MenuRegistry.match("Glimmer Fen ➜ Prologue", det)?.id)
		assertEquals("Prologue", ChapterParser.parse("Glimmer Fen ➜ Prologue", det)!!.header?.cleanName)

		assertFalse(ChapterParser.isDetailPage("Other Place ➜ Prologue", det))
	}

	@Test fun noFalseMatches() {
		reg()
		fun page(back: String, vararg extra: SlotText) = listOf(SlotText(48, "Go Back", listOf(back)), SlotText(49, "Close")) + extra

		for ((title, back) in listOf("Bank Deposit" to "To Personal Bank Account", "Collections" to "To SkyBlock Menu",
				"Mining Collections" to "To Collections", "Coal Collection" to "To Mining Collections", "Slayer Recipes" to "To Recipe Book",
				"Your Bids" to "To Auction House", "Chapters" to "To Quests & Chapters"))
			assertFalse(ChapterParser.matchesBySlots(title, page(back)), title)

		assertFalse(ChapterParser.matchesBySlots("Museum ➜ Combat", page("To Museum", SlotText(10, "Combat", listOf("Items donated: 5/20")))))

		assertFalse(ChapterParser.matchesBySlots("Foo", listOf(SlotText(48, "Go Back", listOf("To Foo", "To Chapters")))))
		assertFalse(ChapterParser.matchesBySlots("Foo ➜ Bar", listOf(SlotText(10, "Bar", listOf("Bar Progress: 5%")))))

		assertFalse(ChapterParser.isAreaPage(listOf(SlotText(60, "Go Back", listOf("To Chapters")))))
		assertNull(MenuRegistry.match("Coal Collection", page("To Mining Collections")))
	}
}
