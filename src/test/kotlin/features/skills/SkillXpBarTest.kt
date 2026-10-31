// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.skills.SkillOverlay
import dev.ashtonite365.apex.features.skills.SkillXpBar

class SkillXpBarTest {

	private val ladder = listOf(50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000,
		30000, 50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000,
		1200000, 1300000, 1400000, 1500000)

	@Test
	fun fromTheActionBar() {
		val (_, skill, progress) = SkillOverlay.parseGain("+12.5 Farming (150,000/200,000)")!!
		val s = SkillXpBar.parse(skill, progress, ladder)!!
		assertEquals("Farming", s.skill)
		assertEquals(0.75f, s.fraction)
		assertEquals(50000.0, s.needed)
		assertEquals(19, s.level)
		assertEquals(29, SkillXpBar.parse("Mining", "600k/1.2M", ladder)!!.level)
	}

	@Test
	fun percentMaxedAndUnknown() {
		val p = SkillXpBar.parse("Combat", "45.5%", ladder)!!
		assertEquals(0.455f, p.fraction, 1e-4f); assertNull(p.level); assertNull(p.needed)
		assertNull(SkillXpBar.parse("Farming", "1,234,567", ladder))
		assertNull(SkillXpBar.parse("Catacombs", "10/20", null)!!.level)
		assertNull(SkillXpBar.levelFor(123456.0, ladder))
	}
}
