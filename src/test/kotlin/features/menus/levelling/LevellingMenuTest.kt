package dev.ashtonite365.apex.test.features.menus.levelling

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.levelling.LevellingMenu
import dev.ashtonite365.apex.features.menus.levelling.LevellingParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class LevellingMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun parses() {
		val d = RealDumpFixtures.load("skyblock-levelling")
		val m = LevellingParser.parse(d.title, d.slots)!!
		assertEquals(122, m.summary.level); assertEquals(12_258.0, m.summary.xp); assertEquals(21.1, m.summary.tasksPct)
		assertEquals("#517,312", m.summary.ranking); assertEquals("Classic Mode", m.summary.mode)
		assertEquals(listOf(122, 123, 124, 125, 126, 140), m.levels.map { it.level })
		val next = m.levels.first { it.level == 123 }
		assertEquals("Next Level", next.tag); assertEquals(58.0, next.progress!!.current); assertEquals(100.0, next.progress!!.needed)
		assertTrue(m.levels.first { it.level == 122 }.unlocked)

		assertEquals(listOf("+1 Strength", "+5 Health"), m.levels.first { it.level == 125 }.rewards.map { it.replace(Regex("\\s*\\p{Co}"), "") })
		assertEquals("1,742 XP (18 Levels)", m.levels.first { it.level == 140 }.xpLeft)
		assertEquals(listOf("Ways to Level Up", "⚑ SkyBlock XP Guide", "Leveling Rewards", "Prefix Emblems", "SkyBlock Levels in Chat"), m.explore.map { it.name })
		assertEquals(30.0, m.explore.first { it.name == "Leveling Rewards" }.progress!!.current)
		assertEquals("ENABLED", m.explore.last().state)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
	}

	@Test fun slotsClassify() {
		val d = RealDumpFixtures.load("skyblock-levelling")
		assertNull(LevellingParser.parse(d.title, d.slots.filter { !it.cleanName.startsWith("Level ") }))
		MenuRegistry.register(LevellingMenu)
		assertEquals("skyblock-levelling", MenuRegistry.match("SkyBlock Leveling")?.id)
		assertNull(MenuRegistry.match("Leveling Rewards"))
	}
}
