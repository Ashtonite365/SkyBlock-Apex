package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skills.Skill
import dev.ashtonite365.apex.features.menus.skills.SkillPageParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class MiningPages1107Test {
	private fun load(id: Int) = RealDumpFixtures.load("skill-page-mining-1107-$id").let { SkillPageParser.parse(it.title, it.slots) }

	@Test fun windowsComeFromTheScreen() {
		val ranges = listOf(34, 36, 38, 40, 42).associateWith { id -> load(id)!!.also { assertEquals(Skill.MINING, it.skill) }.levels.map { it.level } }
		assertEquals(1..20, ranges[38]!!.min()..ranges[38]!!.max()); assertEquals(20, ranges[38]!!.size)
		assertEquals(21..41, ranges[40]!!.min()..ranges[40]!!.max())
		assertEquals(26..46, ranges[36]!!.min()..ranges[36]!!.max())
		assertEquals(37..60, ranges[34]!!.min()..ranges[34]!!.max())
		assertEquals(ranges[34], ranges[42])
		for (l in ranges.values) assertEquals(l.sorted(), l)
	}

	@Test fun hotmLogOpens() {
		for (id in listOf(49, 51, 53)) {
			val m = load(id)!!
			assertEquals(37..60, m.levels.first().level..m.levels.last().level, "$id")
			assertEquals(24, m.levels.size); assertNull(m.nav["Scroll Right"]); assertNotNull(m.nav["Scroll Left"])
		}
	}

	@Test fun scrollControlsFromScreen() {
		val first = load(38)!!; val mid = load(40)!!; val end = load(34)!!
		assertNull(first.nav["Scroll Left"]); assertNotNull(first.nav["Scroll Right"])
		assertNotNull(mid.nav["Scroll Left"]); assertNotNull(mid.nav["Scroll Right"]); assertNotNull(mid.nav["Re-Center"])
		assertNotNull(end.nav["Scroll Left"]); assertNull(end.nav["Scroll Right"])
		assertNotNull(first.summary)
		assertTrue(first.extras.none { it.first == "Mining Skill" })
	}

	@Test fun nothingCapsAt50or60() {
		val d = RealDumpFixtures.load("skill-page-mining-1107-34")
		val roman = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
		fun r(n: Int): String { var x = n; return buildString { for ((v, s) in roman) while (x >= v) { append(s); x -= v } } }
		val lvl = Regex("^Mining Level ([IVXLC]+)$")
		val shifted = d.slots.map { s -> lvl.matchEntire(s.name)?.let { SlotText(s.slot, "Mining Level " + r(dev.ashtonite365.apex.features.menus.skills.SkillLore.level(it.groupValues[1])!! + 40), s.lore) } ?: s }
		val m = SkillPageParser.parse(d.title, shifted)!!
		assertEquals(77..100, m.levels.first().level..m.levels.last().level)
		val real = SkillPageParser.parse(d.title, d.slots)!!
		assertEquals(60, real.levels.last().level)
		if (real.levels.any { it.state != dev.ashtonite365.apex.features.menus.skills.LevelState.UNLOCKED }) assertFalse(real.maxed)
	}
}
