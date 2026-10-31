package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skills.DungeoneeringParser
import dev.ashtonite365.apex.features.menus.skills.LevelState
import dev.ashtonite365.apex.features.menus.skills.SkillLevel
import dev.ashtonite365.apex.features.menus.skills.SkillLevelTips
import dev.ashtonite365.apex.features.menus.skills.SkillLore
import dev.ashtonite365.apex.features.menus.skills.SkillPageParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkillLevelTipsTest {
	private val repo: (String, Int) -> List<String>? = { _, l -> listOf("§7repo reward for $l") }

	@Test fun loreWinsOverRepo() {
		val lvl = SkillLevel(5, 10, LevelState.UNLOCKED, null, emptyList(), "§aMining Level V", listOf("§7Rewards:", "§a+4 Mining Fortune"))
		assertEquals(listOf("§aMining Level V", "§7Rewards:", "§a+4 Mining Fortune"), SkillLevelTips.levelLines("Mining", 5, lvl, repo))
	}

	@Test fun repoFallbackThenNotLoaded() {
		val note = SkillLevelTips.REPO_NOTE
		assertEquals(listOf("§fMining Level 7", "§7repo reward for 7", note), SkillLevelTips.levelLines("Mining", 7, null, repo))
		assertEquals(listOf("§fMining Level 7", "§7repo reward for 7", note),
			SkillLevelTips.levelLines("Mining", 7, SkillLevel(7, 10, LevelState.LOCKED, null, emptyList()), repo))
		assertEquals(listOf("§fMining Level 7", SkillLevelTips.NOT_LOADED), SkillLevelTips.levelLines("Mining", 7, null) { _, _ -> null })
		assertEquals(listOf("§fMining Level 7", SkillLevelTips.NOT_LOADED), SkillLevelTips.levelLines("Mining", 7, null) { _, _ -> emptyList() })

		assertEquals(listOf("§fMining Level 7", SkillLevelTips.NOT_LOADED), SkillLevelTips.levelLines("Mining", 7, null))
	}

	@Test fun levelToLoreAcrossHypixelPages() {

		for (n in listOf("skill-page-mining-1107-38", "skill-page-mining-1107-40", "skill-page-mining-1107-36", "skill-page-mining-1107-34")) {
			val d = RealDumpFixtures.load(n); val m = SkillPageParser.parse(d.title, d.slots)!!
			for (l in m.levels) {
				val src = d.slots.first { it.slot == l.slot }
				val lines = SkillLevelTips.levelLines(m.label, l.level, l)
				assertEquals(listOf(src.name) + src.lore, lines, "$n level ${l.level}")
				assertEquals(l.level, Regex("Level ([IVXLCDM]+)").find(src.cleanName)!!.groupValues[1].let { SkillLore.level(it) })
			}
		}

		for (n in listOf("skill-page-alchemy-60", "skill-page-carpentry-62", "skill-page-combat-22", "skill-page-enchanting-51",
				"skill-page-farming-24", "skill-page-fishing-26", "skill-page-foraging-44", "skill-page-hunting-70",
				"skill-page-runecrafting-64", "skill-page-social-68", "skill-page-taming-66")) {
			val d = RealDumpFixtures.load(n); val m = SkillPageParser.parse(d.title, d.slots)!!
			assertTrue(m.levels.isNotEmpty(), n)
			for (l in m.levels) assertEquals(d.slots.first { it.slot == l.slot }.lore, SkillLevelTips.levelLines(m.label, l.level, l).drop(1), "$n ${l.level}")
		}
	}

	@Test fun wrapsWithoutTruncating() {
		val long = "§7Grants §a+4 ☘ Mining Fortune§7, which increases your chance for multiple ore drops and much more text here"
		val w: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }
		val out = SkillLevelTips.wrap(listOf(long, ""), 60, w)
		assertTrue(out.size > 2)
		assertTrue(out.all { w(it) <= 60 })
		assertEquals(long.replace(Regex("§."), "").replace(" ", ""), out.joinToString("") { it.replace(Regex("§."), "") }.replace(" ", ""))
		assertEquals("", out.last())
	}

	@Test fun dungeoneeringTilesCarryHypixelLore() {
		val d = RealDumpFixtures.load("skills-dungeoneering")
		val dm = DungeoneeringParser.parse(d.title, d.slots)!!
		for (t in dm.dungeons + dm.classes) {
			val src = d.slots.first { it.slot == t.slot }
			val tip = dm.tips[t.slot]!!
			assertEquals(listOf(src.name) + src.lore, SkillLevelTips.itemLines(tip.first, tip.second))
		}
		val essence = dm.extras.first().second
		assertEquals(d.slots.first { it.slot == essence }.name, dm.tips[essence]!!.first)
		assertEquals(listOf("§fX", SkillLevelTips.NOT_LOADED), SkillLevelTips.itemLines("§fX", null))
	}
}
