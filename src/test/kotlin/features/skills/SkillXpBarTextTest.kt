// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.skills.SkillOverlay
import dev.ashtonite365.apex.features.skills.SkillXpBar

class SkillXpBarTextTest {
	private val ladder = List(60) { if (it == 21) 400_000 else 1 + it }

	private fun stateOf(actionBar: String) = SkillOverlay.parseGain(actionBar)!!.let { (g, sk, p) -> SkillXpBar.parse(sk, p, ladder)!!.copy(gain = g) }

	@Test fun barTextFormat() {
		val st = stateOf("+5.8 Farming (292,208/400,000)")
		assertEquals("+5.8 Farming · 107.8k to next · 21", SkillXpBar.barText(st))

		assertEquals("+120 Mining · 10 to next", SkillXpBar.barText(SkillXpBar.State("Mining", 0.5f, 10.0, null, gain = 120.0)))
		assertEquals("+3 Combat", SkillXpBar.barText(SkillXpBar.State("Combat", 0.455f, null, null, gain = 3.0)))
		assertEquals("+1.5 Foraging · 1.2M to next · 40", SkillXpBar.barText(SkillXpBar.State("Foraging", 0f, 1_234_567.0, 40, gain = 1.5)))
	}

	@Test fun debugLogLine() {
		val st = stateOf("+5.8 Farming (292,208/400,000)")
		assertEquals("[ApexSkillXp] skill=Farming gain=5.8 current/needed=292,208/400,000 level=21", SkillXpBar.logLine(st))
	}
}

class SkillXpLevelBoundaryTest {
	private val ladder = List(60) { when (it) { 21 -> 400_000; 22 -> 500_000; else -> 1 + it } }

	@Test fun percentFormAtTheLevelBoundary() {
		val before = SkillOverlay.parseGain("§62,270/1,895     §3+6.3 Farming (381,616/400k)     §b331/331     §4115/115     ".replace(Regex("§."), ""))!!
		val b = SkillXpBar.parse(before.second, before.third, ladder)!!.copy(gain = before.first)
		assertEquals(21, b.level)
		assertEquals("+6.3 Farming · 18.4k to next · 21", SkillXpBar.barText(b))
		assertEquals(Triple("Farming", 21, 22), SkillXpBar.parseLevelUp("  §b§lSKILL LEVEL UP §3Farming §8XXI➜§3XXII"))
		val after = SkillOverlay.parseGain("§62,270/1,901     §3+6.3 Farming (2.33%)     §b331/331     §4115/115     ".replace(Regex("§."), ""))!!
		val a = SkillXpBar.parse(after.second, after.third, ladder, knownLevel = 22)!!.copy(gain = after.first, levelFrom = 21)
		assertEquals(22, a.level)
		assertEquals(true, Regex("^\\+6\\.3 Farming · 488\\.[34]k to next · 21 → 22$").matches(SkillXpBar.barText(a)), SkillXpBar.barText(a))

		val u = SkillXpBar.parse("Farming", "2.33%", null, knownLevel = null)!!.copy(gain = 6.3)
		assertEquals("+6.3 Farming", SkillXpBar.barText(u))
		assertEquals(null, SkillXpBar.parseLevelUp("  §b§lGARDEN MILESTONE §3Wheat §8X➜§3XI"))
	}
}
