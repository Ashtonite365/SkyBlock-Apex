package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.skills.Skill
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillsMenu
import dev.ashtonite365.apex.features.menus.skills.SkillsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkillsMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun roman() {
		assertEquals(50, SkillLore.level("L")); assertEquals(46, SkillLore.level("XLVI")); assertEquals(29, SkillLore.level("XXIX"))
		assertEquals(7, SkillLore.level("7")); assertNull(SkillLore.level("Farming"))
	}

	@Test fun parsesSkills() {
		val d = RealDumpFixtures.load("skills-your-skills")
		val m = SkillsParser.parse(d.title, d.slots)!!
		assertEquals(Skill.entries.toSet(), m.skills.map { it.skill }.toSet())
		val combat = m.skills.first { it.skill == Skill.COMBAT }
		assertEquals(19, combat.slot); assertEquals(27, combat.level)
		assertEquals(225_738.2, combat.progress!!.current); assertEquals(1e6, combat.progress!!.needed)
		assertEquals(22.6, combat.progress!!.percent)
		assertEquals("Warrior XXVIII", combat.rewards.first())
		assertTrue("+20 SkyBlock XP" in combat.rewards)
		val rc = m.skills.first { it.skill == Skill.RUNECRAFTING }
		assertEquals(3, rc.level); assertEquals(0.0, rc.progress!!.fraction); assertEquals(listOf("Access to Level 4 Runes"), rc.rewards)
		assertEquals(1, m.skills.first { it.skill == Skill.HUNTING }.level)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
		assertEquals(24.3, m.average)
		assertEquals(listOf("Dungeoneering" to 34, "Show Skill Rankings" to 53), m.extras)
	}

	@Test fun laterRealDumps() {
		for (f in listOf("skills-your-skills-1034", "tasks-your-skills-1049", "skills-your-skills-1055")) {
			val d = RealDumpFixtures.load(f)
			val m = SkillsParser.parse(d.title, d.slots)!!
			assertEquals(Skill.entries.toSet(), m.skills.map { it.skill }.toSet(), f)
			assertEquals(24.3, m.average, f)

			for (e in m.skills) assertEquals(SkillLore.nextLevel(d.slots.first { it.slot == e.slot }.cleanLore)!! - 1, e.level, "$f ${e.skill}")
			assertEquals(48, m.nav["Go Back"], f)
			assertTrue(m.extras.any { it.first == "Dungeoneering" }, f)
		}
	}

	@Test fun classifyBySlots() {
		val d = RealDumpFixtures.load("skills-your-skills")

		assertNull(SkillsParser.parse(d.title, d.slots.filter { it.slot > 30 || it.slot < 19 }))
		assertNull(SkillsParser.parse("SkyBlock Menu", d.slots))
	}

	@Test fun registryAndToggle() {
		MenuRegistry.register(object : CustomMenu by SkillsMenu { override val enabled = false })
		assertEquals("skills", MenuRegistry.match("§aYour Skills")?.id)
		assertTrue(MenuRegistry.resolve("Your Skills") { null } is MenuRegistry.Resolution.Vanilla)
	}
}
