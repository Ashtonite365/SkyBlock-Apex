package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.quests.ChapterMenu
import dev.ashtonite365.apex.features.menus.quests.ChapterParser
import dev.ashtonite365.apex.features.menus.quests.QuestMenu
import dev.ashtonite365.apex.features.menus.quests.QuestParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class QuestChapterTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun questLog() {
		val d = RealDumpFixtures.load("quest-log")
		val p = QuestParser.parse(d.title, d.slots)!!
		val g = QuestParser.groups(p).associate { it.name to it.tiles }
		assertEquals(7, g.getValue(QuestParser.ACTIVE).size)
		assertEquals(listOf("Find all Fairy Souls"), g.getValue(QuestParser.ONGOING).map { it.label })
		assertEquals(setOf("Dungeon Journals", "Completed Quests"), g.getValue(QuestParser.MORE).map { it.label }.toSet())
		assertEquals("Quest Log", p.header?.cleanName)
		val gearing = g.getValue(QuestParser.ACTIVE).first { it.label == "Gearing Up" }
		assertEquals(listOf("§70/1 steps", "§eReach Garden Level IV. (3/4)"), gearing.info)
	}

	@Test fun questLogModified() {
		val d = RealDumpFixtures.load("quest-log")
		val slots = d.slots.map { if (it.cleanName == "Safety First") it.copy(slot = 40, name = "Brand New Quest") else it } +
			SlotText(30, "Totally Invented", listOf("", " ✔ Step one.", " ✔ Step two.")) +
			SlotText(31, "Another One", listOf("", " ✖ Do the thing. (1/9)"))
		val g = QuestParser.groups(QuestParser.parse(d.title, slots)!!).associate { it.name to it.tiles }
		assertTrue(g.getValue(QuestParser.ACTIVE).any { it.label == "Brand New Quest" && it.slot == 40 })
		assertTrue(g.getValue(QuestParser.ACTIVE).none { it.label == "Safety First" })
		assertEquals(listOf("Totally Invented"), g.getValue(QuestParser.DONE).map { it.label })
		assertTrue(g.getValue(QuestParser.ACTIVE).any { it.label == "Another One" })
	}

	@Test fun chapters() {
		val d = RealDumpFixtures.load("chapters")
		val g = ChapterParser.groups(ChapterParser.parse(d.title, d.slots)!!).associate { it.name to it.tiles }
		assertEquals(listOf("Moonglade Marsh"), g.getValue(ChapterParser.IN_PROGRESS).map { it.label })
		assertEquals("§a2.9% §82/69", g.getValue(ChapterParser.IN_PROGRESS)[0].info[0])
		assertEquals(3, g.getValue(ChapterParser.NOT_STARTED).size)
	}

	@Test fun chaptersModified() {
		val d = RealDumpFixtures.load("chapters")
		val slots = d.slots.filter { it.cleanName != "Lotus Atoll" } +
			SlotText(30, "Imaginary Isle", listOf("/chapters imaginary", "", "Total Progress: 100%", "9/9")) +
			SlotText(31, "Unknown Reef", listOf("Some text", "Talk to Someone!"))
		val g = ChapterParser.groups(ChapterParser.parse(d.title, slots)!!).associate { it.name to it.tiles }
		assertEquals(listOf("Imaginary Isle"), g.getValue(ChapterParser.COMPLETE).map { it.label })
		assertTrue(g.getValue(ChapterParser.NOT_STARTED).any { it.label == "Unknown Reef" })
		assertTrue(g.values.flatten().none { it.label == "Lotus Atoll" })
	}

	@Test fun areaPageGeneric() {
		val d = RealDumpFixtures.load("chapter-area")
		val p = dev.ashtonite365.apex.features.menus.support.DynamicParse.parse(d.title, d.slots, Regex(".+"), 54)!!
		val g = ChapterParser.groups(p).associate { it.name to it.tiles }
		assertEquals(listOf("Chapter I"), g.getValue(ChapterParser.IN_PROGRESS).map { it.label })
		assertEquals(6, g.getValue(ChapterParser.LOCKED).size)
	}

	@Test fun titles() {
		MenuRegistry.clearForTests(); MenuRegistry.register(QuestMenu); MenuRegistry.register(ChapterMenu)
		assertEquals("quests", MenuRegistry.match("Quest Log")?.id)
		assertEquals("chapters", MenuRegistry.match("Chapters")?.id)
		assertNull(MenuRegistry.match("Quests & Chapters"))
		M2.reg(QuestMenu, false); assertTrue(M2.vanilla("Quest Log"))
	}

	@Test fun areaPageMatchedBySlots() {
		val d = RealDumpFixtures.load("chapter-area")
		MenuRegistry.clearForTests(); MenuRegistry.register(QuestMenu); MenuRegistry.register(ChapterMenu)
		assertNull(MenuRegistry.match(d.title))
		assertEquals("chapters", MenuRegistry.match(d.title, d.slots)?.id)
		assertNull(MenuRegistry.match("Some Shop", listOf(dev.ashtonite365.apex.features.menus.support.SlotText(48, "Go Back", listOf("To Hub")))))
		assertEquals("quests", MenuRegistry.match("Quest Log", d.slots)?.id)
		val p = ChapterParser.parse(d.title, d.slots)!!
		assertEquals(listOf("Chapter I"), ChapterParser.groups(p).single { it.name == ChapterParser.IN_PROGRESS }.tiles.map { it.label })
		assertNull(ChapterParser.parse(d.title, d.slots.filterNot { it.cleanName == "Go Back" }))
	}
}
