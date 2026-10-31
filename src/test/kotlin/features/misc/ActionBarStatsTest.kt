// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.misc.ActionBarStats
import dev.ashtonite365.apex.util.getLegacyFormatString

class ActionBarStatsTest {
	private fun check(s: String, hp: Int?, maxHp: Int?, mana: Int?, maxMana: Int?, def: Int?) {
		val p = ActionBarStats.parse(s)
		assertEquals(hp, p.health, "health in <$s>"); assertEquals(maxHp, p.maxHealth, "max health in <$s>")
		assertEquals(mana, p.mana, "mana in <$s>"); assertEquals(maxMana, p.maxMana, "max mana in <$s>")
		assertEquals(def, p.defence, "defence in <$s>")
	}

	@Test fun liveStringNoDefence() = check("§c1234/1500❤     §b893/893✎ Mana", 1234, 1500, 893, 893, null)
	@Test fun plainNoColours() = check("1234/1500❤ 893/893✎ Mana", 1234, 1500, 893, 893, null)
	@Test fun withDefence() = check("§c1234/1500❤     §a250§a❈ Defense     §b893/893✎ Mana", 1234, 1500, 893, 893, 250)
	@Test fun commas() = check("§c12,345/15,000❤     §a1,250❈ Defense     §b1,893/2,000✎ Mana", 12345, 15000, 1893, 2000, 1250)
	@Test fun irregularSpaces() = check("§c1234 / 1500 ❤\u00A0\u00A0§b893\u202F/\u2009893 ✎\u00A0Mana", 1234, 1500, 893, 893, null)
	@Test fun codesInsideSegment() = check("§c1234§c/§c1500§c❤     §b893§b/§b893§b✎ §bMana", 1234, 1500, 893, 893, null)
	@Test fun manaKeywordOnly() = check("§c1234/1500❤     §b893/893 Mana", 1234, 1500, 893, 893, null)
	@Test fun privateUseGlyphsByColour() = check("§c1234/1500\uE000     §a250\uE001     §b893/893\uE002", 1234, 1500, 893, 893, 250)
	@Test fun privateUseNoColourByOrder() = check("1234/1500\uE000     893/893\uE002", 1234, 1500, 893, 893, null)
	@Test fun withOverflowAndAbility() = check("§c1600/1500❤     §b893/893✎ §3120ʬ     §b-50 Mana (§6Instant Transmission§b)", 1600, 1500, 893, 893, null)
	@Test fun manaOnly() = check("§b893/893✎ Mana", null, null, 893, 893, null)
	@Test fun unrelated() { assertTrue(!ActionBarStats.parse("§eYou are now AFK").any) }

	@Test fun styledSiblings() {
		val c = Component.empty()
			.append(Component.literal("1234/1500❤").withStyle(ChatFormatting.RED))
			.append(Component.literal("     "))
			.append(Component.literal("893/893✎ Mana").withStyle(ChatFormatting.AQUA))
		check(c.getLegacyFormatString(), 1234, 1500, 893, 893, null)
		check(c.string, 1234, 1500, 893, 893, null)
	}

	@Test fun stripAll() {
		val s = "§c1234/1500❤     §a250❈ Defense     §b893/893✎ §3120ʬ     §bMining Speed"
		val out = ActionBarStats.strip(s, ActionBarStats.parse(s), health = true, mana = true, defence = true)
		assertEquals("Mining Speed", out.replace(Regex("§."), "").trim())
		assertTrue(out.contains("§bMining"), out)
	}

	@Test fun stripLiveToEmpty() {
		val s = "§c1234/1500❤     §b893/893✎ Mana"
		val out = ActionBarStats.strip(s, ActionBarStats.parse(s), health = true, mana = true, defence = true)
		assertTrue(!ActionBarStats.hasVisibleText(out), out)
	}

	@Test fun stripKeepsDefenceWhenOff() {
		val s = "§c1234/1500❤     §a250❈ Defense     §b893/893✎ Mana"
		val out = ActionBarStats.strip(s, ActionBarStats.parse(s), health = true, mana = true, defence = false)
		assertEquals("250❈ Defense", out.replace(Regex("§."), "").trim())
		assertNull(ActionBarStats.parse(out).health)
	}

	private fun glyphLine(line: String, h: Int, mh: Int, d: Int, m: Int, mm: Int, v: Int, mv: Int) {
		val p = dev.ashtonite365.apex.features.misc.ActionBarStats.parse(line)
		org.junit.jupiter.api.Assertions.assertEquals(listOf(h, mh, d, m, mm, v, mv),
			listOf(p.health, p.maxHealth, p.defence, p.mana, p.maxMana, p.vitality, p.maxVitality))
		org.junit.jupiter.api.Assertions.assertEquals(v to mv, dev.ashtonite365.apex.features.misc.ActionBarStats.parseVitality(line))
	}

	@org.junit.jupiter.api.Test
	fun hypixelPrivateUseGlyphLine() = glyphLine(
		"\u00A7r\u00A762,491/2,116\uE010     \u00A7a932\uE008     \u00A7b707/707\uE003     \u00A74105/105\uE028     \u00A7r",
		2491, 2116, 932, 707, 707, 105, 105)

	@org.junit.jupiter.api.Test
	fun hypixelPrivateUseGlyphLine2() = glyphLine(
		"\u00A7r\u00A76395/1,019\uE010     \u00A7a150\uE008     \u00A7b299/299\uE003     \u00A74101/101\uE028     \u00A7r",
		395, 1019, 150, 299, 299, 101, 101)
}
