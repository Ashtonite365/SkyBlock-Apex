// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.h6.CalendarMenu
import dev.ashtonite365.apex.features.menus.h6.CalendarMenuParser
import dev.ashtonite365.apex.features.menus.skyblock.ElectionMenu
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ElectionRouteTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	private val calendar = object : CustomMenu by CalendarMenu { override val enabled = true }
	private val election = object : CustomMenu by ElectionMenu { override val enabled = true }
	private val electionFixtures = listOf("election-main", "election-year-521", "election-year-521-b")
	private val calendarFixtures = listOf("cal-calendar-events", "cal-event-rewards", "cal-month-autumn", "cal-month-early-autumn", "cal-month-late-summer", "calendar-events")

	@Test fun electionTitlePatternAnyYear() {
		for (t in listOf("Election", "Election, Year 521", "Election, Year 522", "Election, Year 1000", "Election, Year 1"))
			assertTrue(MenuTitles.ELECTION.matches(t), t)

		MenuRegistry.register(calendar); MenuRegistry.register(election)
		for (t in listOf("§6Election", "§r§8Election, Year 522", "§lElection§r, Year §e1000", "  §aElection, Year 521 "))
			assertEquals("election", MenuRegistry.match(t)?.id, t)
		for (t in listOf("Election Results", "Mayor Election Results", "Elections", "Election, Year", "Election, Year x", "Early Spring, Year 521", "Calendar and Events"))
			assertFalse(MenuTitles.ELECTION.matches(MenuRegistry.strip(t)), t)
		assertTrue(MenuRegistry.VANILLA_OWNED.isEmpty())
	}

	@Test fun calendarMatcherNoLongerTakesAnyYearTitle() {
		for (t in listOf("Election, Year 521", "Election, Year 522", "Election, Year 1000", "Foo, Year 3"))
			assertFalse(CalendarMenuParser.TITLE.matches(t), t)
		for (s in listOf("Spring", "Summer", "Autumn", "Winter")) for (p in listOf("", "Early ", "Late "))
			assertTrue(CalendarMenuParser.TITLE.matches("$p$s, Year 521"), "$p$s")
	}

	@Test fun realElectionScreensRouteToElectionNotCalendar() {
		MenuRegistry.register(calendar); MenuRegistry.register(election)
		for (n in electionFixtures) {
			val d = RealDumpFixtures.load(n)
			assertTrue(d.title.startsWith("Election"), n)
			assertEquals("election", MenuRegistry.match(d.title)?.id, n)
			assertEquals("election", MenuRegistry.match(d.title, d.slots)?.id, n)
			val asked = mutableListOf<String>()
			MenuRegistry.resolve(d.title, d.slots) { asked += it.id; null }
			assertEquals(listOf("election"), asked, n)
		}
		for (y in listOf(521, 522, 1000)) assertEquals("election", MenuRegistry.match("§fElection, Year $y")?.id)

		MenuRegistry.clearForTests(); MenuRegistry.register(calendar)
		for (n in electionFixtures) assertNull(MenuRegistry.match(RealDumpFixtures.load(n).title), n)
	}

	@Test fun electionWinsOverAnySlotMatcher() {
		val anySlots = object : CustomMenu by CalendarMenu {
			override val id = "any-slots"; override val enabled = true
			override val title = Regex("(?!)")
			override val slotPredicate: ((String, List<SlotText>) -> Boolean) get() = { _, _ -> true }
		}
		MenuRegistry.register(anySlots); MenuRegistry.register(election)
		for (n in electionFixtures) RealDumpFixtures.load(n).let { assertEquals("election", MenuRegistry.match(it.title, it.slots)?.id, n) }
	}

	@Test fun realCalendarScreensStillRouteToCalendar() {
		MenuRegistry.register(election); MenuRegistry.register(calendar)
		for (n in calendarFixtures) {
			val d = RealDumpFixtures.load(n)
			assertNull(MenuRegistry.vanillaOwner(d.title), n)
			assertEquals("calendar", MenuRegistry.match(d.title)?.id, n)
			val r = MenuRegistry.resolve(d.title) { null }
			assertTrue(r is MenuRegistry.Resolution.Fallback && r.menu.id == "calendar", "$n: $r")
			assertNotNull(CalendarMenuParser.parse(d.title, d.slots.filter { it.cleanName.isNotEmpty() }), n)
		}
		assertEquals("calendar", MenuRegistry.match("§aEarly Spring, Year 522")?.id)
	}

	@Test fun calendarParserRejectsElectionDumps() {
		for (n in electionFixtures) RealDumpFixtures.load(n).let { assertNull(CalendarMenuParser.parse(it.title, it.slots), n) }
	}
}
