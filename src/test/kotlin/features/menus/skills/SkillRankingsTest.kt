// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skills.Skill
import dev.ashtonite365.apex.features.menus.skills.SkillRank
import dev.ashtonite365.apex.features.menus.skills.SkillRankData
import dev.ashtonite365.apex.features.menus.skills.SkillRankings
import dev.ashtonite365.apex.features.menus.skills.SkillsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkillRankingsTest {
	private val on = RealDumpFixtures.load("skills-rankings-2108")
	private val off = RealDumpFixtures.load("skills-norankings-2108")

	@Test
	fun parsesLoreLines() {
		val r = SkillRankings.parse(listOf("+20 SkyBlock XP", "", "#993,272 out of 32,472,081 players",
			"You are within the top 3.06% of players!", "Rankings may take some time to refresh."))!!
		assertEquals(SkillRank(993_272, 32_472_081, 3.06), r)
		assertEquals(96.94, r.betterThan!!, 0.005)
		assertNull(SkillRankings.parse(listOf("Ranking requires skill level 15", "Rankings may take some time to refresh.")))
		assertNull(SkillRankings.parse(listOf("Fight mobs and special bosses to", "earn Combat EXP!", "", "Click to view!")))
	}

	@Test
	fun realDumpLive() {
		val live = SkillRankings.fromPage(on.title, on.slots)
		assertEquals(setOf(Skill.COMBAT, Skill.FARMING, Skill.MINING, Skill.FORAGING, Skill.ENCHANTING, Skill.CARPENTRY, Skill.TAMING), live.keys)
		assertEquals(SkillRank(993_272, 32_472_081, 3.06), live[Skill.COMBAT])
		assertEquals(SkillRank(376_358, 32_472_081, 1.16), live[Skill.MINING])
		val skills = SkillsParser.parse(on.title, on.slots)!!.skills.map { it.skill }
		val tip = SkillRankings.tooltip(skills, live, emptyMap())
		assertEquals(skills.size, tip.size, "one line per skill, nothing else")
		assertTrue("§fCombat: §e#993,272 §7of 32,472,081 §8· §abetter than 96.94%" in tip, tip.toString())
		assertTrue("§fMining: §e#376,358 §7of 32,472,081 §8· §abetter than 98.84%" in tip)
		assertTrue("§fFishing: §7unknown" in tip, "below Hypixel's level gate: no ranking")
	}

	@Test
	fun displayOffUsesLastSeenElseUnknown() {
		assertTrue(SkillRankings.fromPage(off.title, off.slots).isEmpty(), "rankings display off: nothing live")
		val skills = SkillsParser.parse(off.title, off.slots)!!.skills.map { it.skill }
		assertTrue(SkillRankings.tooltip(skills, emptyMap(), emptyMap()).all { it.endsWith("§7unknown") }, "no cache -> unknown")

		val d = SkillRankData()
		assertTrue(SkillRankings.update(d, SkillRankings.fromPage(on.title, on.slots)))
		assertFalse(SkillRankings.update(d, SkillRankings.fromPage(off.title, off.slots)), "an OFF page never clears the cache")
		val tip = SkillRankings.tooltip(skills, emptyMap(), d.ranks)
		assertTrue("§fCombat: §e#993,272 §7of 32,472,081 §8· §abetter than 96.94%" in tip)
		assertTrue("§fHunting: §7unknown" in tip)

		val newer = mapOf(Skill.COMBAT to SkillRank(900_000, 32_500_000, 2.77))
		assertEquals(newer[Skill.COMBAT], SkillRankings.best(Skill.COMBAT, newer, d.ranks))
	}

	@Test
	fun buttonNames() {
		assertTrue(SkillRankings.BUTTON.matches("Show Skill Rankings"))
		assertTrue(SkillRankings.BUTTON.matches("Hide Skill Rankings"))
		assertFalse(SkillRankings.BUTTON.matches("Show Collection Rankings"))
	}
}
