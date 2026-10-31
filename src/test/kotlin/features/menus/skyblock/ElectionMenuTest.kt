// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.ElectionParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ElectionMenuTest {
	private fun load(n: String) = RealDumpFixtures.load(n).let { ElectionParser.parse(it.title, it.slots)!! }

	@Test fun beforeVotingEveryBarIsUnknown() {
		val m = load("election-main")
		assertFalse(m.voted)
		assertEquals(listOf("Marina", "Paul", "Foxy", "Cole", "Jerry"), m.candidates.map { it.name })
		assertEquals(listOf(9, 11, 13, 15, 17), m.candidates.map { it.slot })
		assertTrue(m.candidates.all { it.percent == null && it.votes == null && !it.votedFor })
		assertTrue(m.candidates.single { it.name == "Jerry" }.special)
		assertEquals("521", m.year)
		assertEquals(listOf("Close" to 31, "Upcoming Special Mayor" to 32), m.nav)

		val marina = m.candidates.first()
		assertTrue(marina.info.contains("✯ Fishing XP Buff")); assertTrue(marina.info.contains("Gain +50☯ Fishing Wisdom on public"))
		assertTrue(marina.info.none { it.startsWith("Click to vote") })
		assertTrue(m.candidates.single { it.name == "Foxy" }.info.contains("Chivalrous Carnival"))
	}

	@Test fun afterVotingBarsFillByPercent() {
		for (n in listOf("election-year-521", "election-year-521-b")) {
			val m = load(n)
			assertTrue(m.voted, n)

			assertEquals(listOf("Marina" to 9, "Paul" to 11, "Foxy" to 13, "Cole" to 15, "Jerry" to 17), m.candidates.map { it.name to it.slot }, n)
			assertEquals(listOf(1.4, 1.5, 5.0, 3.3, 88.7), m.candidates.map { it.percent }, n)
			assertEquals(listOf("9.5k", "9.9k", "32.8k", "21.7k", "587.2k"), m.candidates.map { it.votes }, n)
			val jerry = m.candidates.last()
			assertTrue(jerry.votedFor && jerry.leading, n)
			assertTrue(m.candidates.dropLast(1).none { it.votedFor || it.leading }, n)
			assertTrue(m.candidates.all { c -> c.info.none { ElectionParser.VOTES.containsMatchIn(it) } }, n)
			assertEquals(listOf("Upcoming Special Mayor" to 4), m.nav, n)
		}
	}

	@Test fun fillIsSmoothNotQuarterSteps() {
		val inner = 98
		assertEquals(0, ElectionParser.fillPx(null, inner))
		assertEquals(1, ElectionParser.fillPx(1.4, inner))
		assertEquals(4, ElectionParser.fillPx(5.0, inner))
		assertEquals(86, ElectionParser.fillPx(88.7, inner))
		assertEquals(inner, ElectionParser.fillPx(100.0, inner)); assertEquals(inner, ElectionParser.fillPx(140.0, inner))

		assertNotEquals(ElectionParser.fillPx(30.0, inner), ElectionParser.fillPx(40.0, inner))
	}

	@Test fun anyYearAndNonElectionTitles() {
		val slots = listOf(SlotText(10, "Aatrox", listOf("Year 1000 Candidate", "", "Votes: 1.2M (51%)", "Slashed Pricing", "Click to vote for Aatrox!")))
		val m = ElectionParser.parse("§6Election, Year 1000", slots)!!
		assertEquals("1000", m.year); assertTrue(m.voted); assertEquals(51.0, m.candidates.single().percent)
		assertNull(ElectionParser.parse("Calendar and Events", slots))
		assertNull(ElectionParser.parse("Election", listOf(SlotText(31, "Close", listOf()))))
	}
}
