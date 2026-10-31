// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.CombatStatsVitality

class CombatStatsVitalityTest {
	@Test fun plainInteger() = assertEquals(105.0, CombatStatsVitality.parseLine("  Vitality 105"))
	@Test fun commasAndDecimal() = assertEquals(1234.5, CombatStatsVitality.parseLine("  Vitality 1,234.5"))
	@Test fun formattingCodesStripped() = assertEquals(1234.5, CombatStatsVitality.parseLine("§7  §4♨ §7Vitality §f1,234.5".replace("♨ ", "")))
	@Test fun bigCommas() = assertEquals(1_234_567.0, CombatStatsVitality.parseLine(" Vitality 1,234,567"))
	@Test fun rejectsOthers() {
		assertNull(CombatStatsVitality.parseLine("  Health 1,234"))
		assertNull(CombatStatsVitality.parseLine("  Vitality"))
		assertNull(CombatStatsVitality.parseLine("Total Vitality: abc"))
		assertNull(CombatStatsVitality.parseLine("  Vitality 1,23"))
	}
	@Test fun loreFirstMatch() = assertEquals(105.0, CombatStatsVitality.parseLore(listOf("Gives you stats", "  Health 500", "§7  Vitality §a105", "  Vitality 9")))
	@Test fun itemIdentity() {
		assertTrue(CombatStatsVitality.isCombatStats(14, "§aCombat Stats"))
		assertFalse(CombatStatsVitality.isCombatStats(13, "Combat Stats"))
		assertFalse(CombatStatsVitality.isCombatStats(14, "Mining Stats"))
	}
	@Test fun changeLogOnlyOnChange() {
		assertEquals("[apex stat-bars] vitality changed 100->80 (80/100)", CombatStatsVitality.changeLog(100, 100, 80, 100))
		assertEquals("[apex stat-bars] vitality changed ?->105 (105/105)", CombatStatsVitality.changeLog(null, null, 105, 105))
		assertNull(CombatStatsVitality.changeLog(80, 100, 80, 100))
	}
	@Test fun cachedIsOnlyMax() {
		val d = CombatStatsVitality.Data(1234.5)
		val os = CombatStatsVitality.store
		CombatStatsVitality.store = { d }
		try { assertEquals(1235, CombatStatsVitality.maxFallback()) } finally { CombatStatsVitality.store = os }
	}
	@Test fun recordPersistsOnlyOnChange() {
		val d = CombatStatsVitality.Data(); var saves = 0
		val os = CombatStatsVitality.store; val ov = CombatStatsVitality.save
		CombatStatsVitality.store = { d }; CombatStatsVitality.save = { saves++ }
		try {
			assertTrue(CombatStatsVitality.record(105.0)); assertFalse(CombatStatsVitality.record(105.0))
			assertEquals(105.0, CombatStatsVitality.cached()); assertEquals(1, saves)
		} finally { CombatStatsVitality.store = os; CombatStatsVitality.save = ov }
	}

	@Test fun iconGlyphPrefixFromRealDump() {
		assertEquals(105.0, CombatStatsVitality.parseLine("  \uE028 Vitality 105"))
		assertEquals(105.0, CombatStatsVitality.parseLore(listOf("  Health 2,116.5", "  \uE028 Vitality 105")))
		assertNull(CombatStatsVitality.parseLine("  Combat Vitality 105"))
	}
}
