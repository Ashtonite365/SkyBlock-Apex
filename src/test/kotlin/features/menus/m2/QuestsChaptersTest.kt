package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSections
import dev.ashtonite365.apex.features.menus.quests.ChapterMenu
import dev.ashtonite365.apex.features.menus.quests.ChapterParser
import dev.ashtonite365.apex.features.menus.quests.QcButton
import dev.ashtonite365.apex.features.menus.quests.QuestMenu
import dev.ashtonite365.apex.features.menus.quests.QuestParser
import dev.ashtonite365.apex.features.menus.quests.QuestsChaptersLayout
import dev.ashtonite365.apex.features.menus.quests.QuestsChaptersMenu
import dev.ashtonite365.apex.features.menus.quests.QuestsChaptersParser
import dev.ashtonite365.apex.features.menus.quests.QuestsChaptersSettings
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class QuestsChaptersTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	private val real = RealDumpFixtures.load("quests-chapters-2117")
	private fun model() = QuestsChaptersParser.parse(real.title, real.slots)!!

	@Test fun layoutFromRealDump() {
		val m = model()
		assertEquals("Quests & Chapters", m.heading)
		assertEquals(4, m.headerSlot)

		assertEquals(real.slots.single { it.slot == 4 }.cleanLore, m.headerLore)
		assertEquals(listOf(20, 24), m.entries.map { it.slot })
		assertEquals(listOf("Chapters", "Quest Log"), m.entries.map { it.name })
		assertEquals(listOf("/chapters", "/quests"), m.entries.map { it.command })
		assertEquals(listOf("View your progression through the", "Chapters of each island!"), m.entries[0].description)
		assertTrue(m.entries.none { e -> e.description.any { it.startsWith("Click", true) } })

		val old = RealDumpFixtures.load("quests-chapters")
		assertEquals(m.buttons, QuestsChaptersParser.parse(old.title, old.slots)!!.buttons)
	}

	@Test fun buttonsForwardToTheirRealSlots() {
		val m = model()
		assertEquals(listOf(QcButton("Chapters", 20), QcButton("Quest Log", 24), QcButton("Back (SkyBlock Menu)", 48), QcButton("Close", 49)), m.buttons)

		for (b in m.buttons) {
			val s = real.slots.single { it.slot == b.slot }
			assertTrue(b.label == s.cleanName || b.label.startsWith("Back") && s.cleanName == "Go Back", b.label)
		}

		assertEquals(0, QuestsChaptersParser.forwardButton(0))
		assertNull(QuestsChaptersParser.forwardButton(1))
		assertNull(QuestsChaptersParser.forwardButton(2))
	}

	@Test fun dynamicNotFixedSlots() {
		val moved = real.slots.map { when (it.slot) { 20 -> it.copy(slot = 21); 24 -> it.copy(slot = 23); else -> it } }
		assertEquals(listOf(21, 23), QuestsChaptersParser.parse(real.title, moved)!!.entries.map { it.slot })

		assertNull(QuestsChaptersParser.parse(real.title, real.slots.filter { it.slot == 4 || it.slot >= 48 }))
		assertNull(QuestsChaptersParser.parse("Chapters", real.slots))
	}

	@Test fun headerWrappedNotCut() {
		val measure = { s: String -> s.length * 6 }
		val width = 338
		val lines = QuestsChaptersLayout.wrapHeader(model().headerLore, width, measure)
		assertTrue(lines.all { measure(it) <= width && !it.contains('…') })
		val words = { l: List<String> -> l.joinToString(" ").split(' ').filter { it.isNotBlank() } }
		assertEquals(words(model().headerLore), words(lines))
		assertEquals(2, lines.count { it.isEmpty() })

		val narrow = QuestsChaptersLayout.wrapHeader(model().headerLore, 120, measure)
		assertTrue(narrow.size > lines.size); assertEquals(words(model().headerLore), words(narrow))

		val cards = QuestsChaptersLayout.cards(2, 6, 100, 368, 80)
		assertTrue(cards[0][0] + cards[0][2] <= cards[1][0])
		assertTrue(cards[1][0] + cards[1][2] <= 6 + 368)
		assertTrue(QuestsChaptersLayout.navTop(0, 240) >= 100 + 80)
	}

	@Test fun matchersDoNotStealEitherWay() {
		for (order in listOf(listOf(QuestMenu, ChapterMenu, QuestsChaptersMenu), listOf(QuestsChaptersMenu, ChapterMenu, QuestMenu))) {
			MenuRegistry.clearForTests(); order.forEach(MenuRegistry::register)
			assertEquals("quests-chapters", MenuRegistry.match("Quests & Chapters")?.id)
			assertEquals("quests-chapters", MenuRegistry.match(real.title, real.slots)?.id)
			assertEquals("chapters", MenuRegistry.match("Chapters")?.id)
			assertEquals("chapters", MenuRegistry.match("Chapters (2/3)")?.id)
			assertEquals("chapters", MenuRegistry.match("Chapters ➜ Moonglade Marsh")?.id)
			assertEquals("quests", MenuRegistry.match("Quest Log")?.id)
			assertEquals("quests", MenuRegistry.match("Quest Log (1/2)")?.id)
			val area = RealDumpFixtures.load("chapter-area")
			assertEquals("chapters", MenuRegistry.match(area.title, area.slots)?.id)
			val ql = RealDumpFixtures.load("quest-log")
			assertEquals("quests", MenuRegistry.match(ql.title, ql.slots)?.id)
		}

		assertNull(QuestParser.parse(real.title, real.slots))
		assertNull(ChapterParser.parse(real.title, real.slots))
		assertFalse(ChapterParser.matchesBySlots(real.title, real.slots))
		for (t in listOf("Chapters", "Quest Log", "Quests", "Chapters ➜ Quests & Chapters", "Quests & Chapters ➜ X", "My Quests & Chapters"))
			assertFalse(QuestsChaptersParser.TITLE.containsMatchIn(t), t)
		assertFalse(QuestMenu.title.containsMatchIn("Quests & Chapters"))
		assertFalse(ChapterMenu.title.containsMatchIn("Quests & Chapters"))
		assertFalse(MenuRegistry.denied("Quests & Chapters"))

		val dir = java.io.File(javaClass.getResource("/apex-fixtures/quests-chapters-2117.txt")!!.toURI()).parentFile
		for (f in dir.listFiles()!!.filter { it.extension == "txt" }) {
			val title = runCatching { RealDumpFixtures.load(f.nameWithoutExtension).title }.getOrNull() ?: continue
			assertEquals(title.trim().equals("Quests & Chapters", true), QuestsChaptersParser.TITLE.containsMatchIn(MenuRegistry.strip(title)), f.name)
		}
	}

	@Test fun registeredInQuestsSection() {
		assertEquals("custom-menu-quests-chapters", QuestsChaptersSettings.name)
		assertEquals("quests", MenuSections.sectionOf(QuestsChaptersSettings.name))
		assertTrue(MenuSections.SECTIONS.any { it.group == "quests" && it.switchLabel == "Quests Apex Menus" })
		assertTrue(dev.ashtonite365.apex.settings.H1Sections.HOLDERS.containsKey("custom-menu-quests-chapters"))
		val en = generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.map { java.io.File(it, "translations/en_us.json") }.first { it.isFile }
		val text = en.readText()
		assertTrue(text.contains("\"apex.config.custom-menu-sections.quests\": \"Quests Apex Menus\""))
		assertTrue(text.contains("\"apex.config.custom-menu-quests-chapters\": \"Custom Menu: Quests & Chapters\""))
	}
}
