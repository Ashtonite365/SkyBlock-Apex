package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.skills.LevelState
import dev.ashtonite365.apex.features.menus.skills.Skill
import dev.ashtonite365.apex.features.menus.skills.SkillPageMenu
import dev.ashtonite365.apex.features.menus.skills.SkillPageParser
import dev.ashtonite365.apex.features.menus.skills.SkillsParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkillPageMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val pages = listOf("combat-22", "farming-24", "fishing-26", "mining-28", "mining-30", "foraging-44", "enchanting-51", "enchanting-58",
		"alchemy-60", "carpentry-62", "runecrafting-64", "taming-66", "social-68", "hunting-70", "hunting-72")
	private fun load(n: String) = RealDumpFixtures.load("skill-page-$n").let { SkillPageParser.parse(it.title, it.slots) }

	@Test fun titles() {
		MenuRegistry.register(SkillPageMenu)
		for (s in Skill.entries) assertEquals("skill-page", MenuRegistry.match("${s.label} Skill")?.id)
		assertNull(MenuRegistry.match("Farming Collections"))
		assertNull(MenuRegistry.match("Your Skills"))
	}

	@Test fun everyRealPageAgreesWithYourSkills() {
		val ys = RealDumpFixtures.load("skills-your-skills-1034").let { SkillsParser.parse(it.title, it.slots)!! }
		for (n in pages) {
			val m = load(n); assertNotNull(m, n); m!!
			assertEquals(n.substringBefore('-'), m.skill!!.label.lowercase(), n)
			assertTrue(m.levels.size >= 15, "$n levels=${m.levels.size}")
			val expected = ys.skills.first { it.skill == m.skill }.level
			m.level?.let { assertEquals(expected, it, n) }

			m.current?.let { c -> assertEquals(expected!! + 1, c.level, n); assertTrue(m.levels.none { it.level > c.level && it.state != LevelState.LOCKED }, n) }
			assertFalse(m.maxed, n)
			assertEquals(48, m.nav["Go Back"], n); assertEquals(49, m.nav["Close"], n)
		}
	}

	@Test fun combatDetails() {
		val m = load("combat-22")!!
		assertEquals(27, m.level)
		assertEquals(28, m.current!!.level); assertEquals(13, m.current!!.slot)
		assertEquals(225_738.2, m.current!!.progress!!.current); assertEquals(1e6, m.current!!.progress!!.needed)
		assertEquals("Warrior XXVIII", m.current!!.rewards.first())
		assertEquals(LevelState.UNLOCKED, m.levels.first { it.level == 17 }.state)
		assertEquals(LevelState.LOCKED, m.levels.first { it.level == 40 }.state)
		assertEquals(listOf("Bestiary" to 39, "Slayer" to 41), m.extras)
		assertEquals(45, m.nav["Scroll Left"]); assertEquals(53, m.nav["Scroll Right"])
	}

	@Test fun summaryWindowWithoutProgressTile() {
		val m = load("mining-30")!!
		assertNotNull(m.summary); assertEquals(49, m.summary!!.level); assertEquals(50, m.summary!!.next)
		assertEquals(2_203_700.3, m.summary!!.progress.current)
		assertNull(m.current)
		assertTrue(m.levels.all { it.state == LevelState.UNLOCKED })
		assertEquals(50, m.nav["Re-Center"]); assertNull(m.nav["Scroll Left"]); assertEquals(53, m.nav["Scroll Right"])
		assertFalse(m.extras.any { it.first == "Mining Skill" })
	}

	@Test fun scrolledWindowAllLocked() {
		val m = load("fishing-26")!!
		assertNull(m.current); assertNull(m.summary)
		assertTrue(m.levels.all { it.state == LevelState.LOCKED })
		assertEquals(50, m.levels.last().level)
	}

	@Test fun farmingGardenItemIsExtraNotLevel() {
		val m = load("farming-24")!!
		assertTrue(m.levels.none { it.slot == 40 })
		assertTrue(m.extras.any { it.first == "Garden Level III" })
	}

	@Test fun huntingLinksToBoxAndAttributes() {
		val m = load("hunting-70")!!
		assertEquals(1, m.level)
		assertEquals(listOf("Hunting Box", "Attribute Menu"), m.extras.map { it.first })
	}

	@Test fun maxedSkillDerived() {
		val d = RealDumpFixtures.load("skill-page-mining-28")
		val slots = d.slots.map { s -> if (s.name.startsWith("Mining Level")) SlotText(s.slot, s.name, s.lore.filter { !it.contains("Progress") && !it.contains("/") } + "UNLOCKED") else s } +
			SlotText(27, "Mining Skill", listOf("Dive into deep caves", "", "MAX SKILL", ""))
		val m = SkillPageParser.parse(d.title, slots)!!
		assertTrue(m.maxed); assertNull(m.current)
		assertTrue(m.levels.all { it.state == LevelState.UNLOCKED })
	}

	@Test fun classifyBySlots() {
		val d = RealDumpFixtures.load("skill-page-combat-22")
		assertNull(SkillPageParser.parse("Farming Skill", d.slots))
		MenuRegistry.register(object : CustomMenu by SkillPageMenu { override val enabled = false })
		assertTrue(MenuRegistry.resolve("Mining Skill") { null } is MenuRegistry.Resolution.Vanilla)
	}
}
