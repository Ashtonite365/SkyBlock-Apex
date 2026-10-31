package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.skills.GardenLevelsMenu
import dev.ashtonite365.apex.features.menus.skills.GardenLevelsParser
import dev.ashtonite365.apex.features.menus.skills.LevelState
import dev.ashtonite365.apex.features.menus.skills.SkillPageMenu
import dev.ashtonite365.apex.features.menus.skills.SkillPageParser
import dev.ashtonite365.apex.features.menus.skills.SkillsMenu
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class FarmingGarden1106Test {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun load(n: String) = RealDumpFixtures.load(n)

	@Test fun farmingWindowsReadFromScreen() {
		val a = load("skill-page-farming-1106-30").let { SkillPageParser.parse(it.title, it.slots)!! }
		val b = load("skill-page-farming-1106-32").let { SkillPageParser.parse(it.title, it.slots)!! }
		assertEquals(7..30, a.levels.first().level..a.levels.last().level)
		assertEquals(1..20, b.levels.first().level..b.levels.last().level)
		assertNotNull(a.nav["Scroll Left"]); assertNotNull(a.nav["Scroll Right"])
		assertNull(b.nav["Scroll Left"]); assertNotNull(b.nav["Scroll Right"])

		assertTrue(a.extras.any { it.first.startsWith("Garden Level") }); assertTrue(b.extras.any { it.first.startsWith("Garden Level") })
	}

	@Test fun gardenLevels() {
		val d = load("garden-levels-1106-31")
		val m = GardenLevelsParser.parse(d.title, d.slots)!!
		assertEquals("Garden", m.label); assertNull(m.skill)
		assertEquals((1..15).toList(), m.levels.map { it.level })
		assertEquals(3, m.level); assertEquals(4, m.summary?.next)
		assertEquals(LevelState.IN_PROGRESS, m.levels.first { it.level == 4 }.state)
		assertEquals(LevelState.UNLOCKED, m.levels.first { it.level == 1 }.state)
		assertEquals(LevelState.LOCKED, m.levels.first { it.level == 15 }.state)
		assertTrue(m.levels.first { it.level == 1 }.rewards.any { it.contains("Visitors") })
		assertNotNull(m.nav["Go Back"])
		assertNull(SkillPageParser.parse(d.title, d.slots))

		val tip = dev.ashtonite365.apex.features.menus.skills.SkillLevelTips.levelLines(m.label, 1, m.levels.first { it.level == 1 })
		assertTrue(tip.size > 1 && tip.drop(1).any { it.contains("Visitors") }, tip.toString())
		assertTrue(m.tips[m.summary!!.slot]!!.second.isNotEmpty())
		assertNull(GardenLevelsParser.parse("Farming Skill", load("skill-page-farming-1106-32").slots))
	}

	@Test fun matching() {
		MenuRegistry.register(SkillPageMenu); MenuRegistry.register(GardenLevelsMenu); MenuRegistry.register(SkillsMenu)
		for (n in listOf("garden-levels-1106-31", "skill-page-farming-1106-30", "skill-page-farming-1106-32")) {
			val d = load(n)
			assertEquals(if (n.startsWith("garden")) "garden-levels" else "skill-page", MenuRegistry.match(d.title, d.slots)?.id, n)
		}
		assertNull(MenuRegistry.match("Garden", load("garden-levels-1106-31").slots))
	}
}
