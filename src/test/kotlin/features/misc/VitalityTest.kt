// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.ActionBarStats
import dev.ashtonite365.apex.features.misc.StatBars

class VitalityTest {
	private fun vit(s: String, v: Int?, max: Int?) {
		val p = ActionBarStats.parse(s)
		assertEquals(v, p.vitality, "vitality in <$s>"); assertEquals(max, p.maxVitality, "max vitality in <$s>")
	}

	@Test fun riftWithSymbolAndWord() {
		val s = "§c1234/1500❤     §a800/1000♨ Vitality     §b893/893✎ Mana"
		vit(s, 800, 1000)
		val p = ActionBarStats.parse(s)
		assertEquals(1234, p.health); assertEquals(1500, p.maxHealth)
		assertEquals(893, p.mana); assertEquals(893, p.maxMana)
		assertNull(p.defence)
	}
	@Test fun symbolOnly() = vit("§c1234/1500❤     §a800/1000♨     §b893/893✎ Mana", 800, 1000)
	@Test fun wordOnlyWithCommas() = vit("§c12,345/15,000❤     §a1,200 / 2,500 Vitality", 1200, 2500)
	@Test fun privateUseGlyphPlusWord() {
		val s = "§c1234/1500\uE000     §a800/1000\uE010 Vitality     §b893/893\uE002"
		vit(s, 800, 1000)
		val p = ActionBarStats.parse(s)
		assertEquals(1234, p.health); assertEquals(893, p.mana)
	}
	@Test fun vitalityOnlyLineCountsAsStats() {
		val p = ActionBarStats.parse("§21,000/1,000♨ Vitality")
		assertEquals(1000, p.vitality); assertNull(p.health); assertNull(p.mana); assertTrue(p.any)
	}
	@Test fun noVitalityOutsideRift() {
		vit("§c1234/1500❤     §a250§a❈ Defense     §b893/893✎ Mana", null, null)
		vit("§c12,345/15,000❤     §a1,250❈ Defense     §b1,893/2,000✎ Mana", null, null)
		vit("1234/1500\uE000     893/893\uE002", null, null)
		vit("§c1600/1500❤     §b893/893✎ §3120ʬ     §b-50 Mana (§6Instant Transmission§b)", null, null)
		vit("§6♨ Selling stuff for a limited time!", null, null)
	}
	@Test fun currentTotalVitality() {
		assertEquals(105 to 105, ActionBarStats.parseVitality("Total Vitality: 105/105"))
		assertEquals(1050 to 1050, ActionBarStats.parseVitality(" §7Total Vitality: §a♨ §a1,050§7/§a1,050"))
		assertEquals(105 to 105, ActionBarStats.parseVitality("§a105/105♨ Total Vitality"))
		assertNull(ActionBarStats.parseVitality("§c1234/1500❤     §b893/893✎ Mana"))
	}
	@Test fun renderValue() {
		assertEquals("800/1000\uE028", StatBars.vitalityLabel(800, 1000))
		assertFalse(StatBars.vitalityLabel(800, 1000).contains('♨'))
		assertEquals(0.8F, StatBars.vitalityFraction(800, 1000), 1e-6F)
		assertEquals(1F, StatBars.vitalityFraction(1200, 1000), 1e-6F)
	}
	@Test fun registeredInEditorList() {
		StatBars.TConfig.explicitDefaultLoad()
		assertEquals(listOf("health", "mana", "xp", "defence", "vitality"), StatBars.elementIds())
		val huds = StatBars.allHuds()
		assertEquals(5, huds.size)
		assertTrue(huds.any { it.hudId.path.contains("vitality") || it.label.string.contains("itality") }, "vitality hud in allHuds")
		assertTrue("apex.config.stat-bars.vitality-hud" in dev.ashtonite365.apex.gui.config.HudMeta.hiddenEditorLabels, "vitality label hidden like the other bars")
		assertTrue(huds.last().isEnabled)
	}

	@Test fun fallbackDarkRedPair() {
		assertEquals(105 to 105, ActionBarStats.parseVitality("§4105/105\uE001"))
		assertEquals(105 to 105, ActionBarStats.parseVitality("§c2116/2116❤     §4105/105\uE001     §b787/787✎ Mana"))
		assertNull(ActionBarStats.parseVitality("§c105/105\uE001"))
	}

	@Test fun fallbackSkipsHealthAndMana() {
		assertEquals(null, ActionBarStats.parseVitality("§c2116/2116❤     §b787/787✎ Mana"))
		assertEquals(null, ActionBarStats.parseVitality("§c2116/2116\uE002     §b787/787\uE003"))
		assertEquals(null, ActionBarStats.parseVitality("§4500/500\uE010"))
	}

	@Test fun fallbackOnFullActionBarLine() {
		val p = ActionBarStats.parse("§c2116/2116❤     §4105/105\uE001     §b787/787✎ Mana")
		assertEquals(105, p.vitality); assertEquals(105, p.maxVitality)
		assertEquals(2116, p.health); assertEquals(2116, p.maxHealth)
		assertEquals(787, p.mana); assertEquals(787, p.maxMana)
	}

	private val LIVE = "§r§62,491/2,116\uE010     §7\uE067 §eBazaar Alley     §b707/707\uE003     §4105/105\uE028"

	@Test fun liveActionBar263() {
		val p = ActionBarStats.parse(LIVE)
		assertEquals(105, p.vitality); assertEquals(105, p.maxVitality)
		assertEquals(2491, p.health); assertEquals(2116, p.maxHealth)
		assertEquals(707, p.mana); assertEquals(707, p.maxMana)
		assertEquals(105 to 105, ActionBarStats.parseVitality(LIVE))

		assertEquals(105 to 105, ActionBarStats.parseVitality("§b707/707\uE003     §c105/105\uE028"))
		assertEquals(105 to 105, ActionBarStats.parseVitality("§62,491/2,116\uE010     §4105/105"))
		assertNull(ActionBarStats.parseVitality("§r§62,491/2,116\uE010     §b707/707\uE003"))
	}

	@Test fun stripVitality() {
		val p = ActionBarStats.parse(LIVE)
		val out = ActionBarStats.strip(LIVE, p, health = false, mana = false, defence = false, vitality = true)
		assertTrue("105/105" !in out && "707/707" in out && "2,491/2,116" in out, out)
	}

	@Test fun profileLoreFixture() {
		val lines = javaClass.getResourceAsStream("/vitality/skyblock-profile-lore.TODO.txt")!!.bufferedReader().readLines()
			.filterNot { it.startsWith("#") }

		assertEquals(105.0, dev.ashtonite365.apex.features.misc.CombatStatsVitality.parseProfileMax(lines))
		assertEquals(1200.0, dev.ashtonite365.apex.features.misc.CombatStatsVitality.parseProfileMax(listOf("§7Total Vitality: §4♨1,050§7/§41,200")))
		assertNull(dev.ashtonite365.apex.features.misc.CombatStatsVitality.parseProfileMax(listOf(" ❤ Health 2,116", " ✎ Intelligence 607")))
	}
}
