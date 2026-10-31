package dev.ashtonite365.apex.test.features.plus.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.combat.PlusCombatLogic

class PlusCombatLogicTest {
	@Test
	fun bossTags() {
		val t = PlusCombatLogic.parseBossTag("﴾ Arachne 10M/20M❤ ﴿")!!
		assertEquals("Arachne", t.name); assertEquals(1e7, t.health); assertEquals(50.0, t.percent)
		assertEquals(1.5e9, PlusCombatLogic.parseBossTag("☠ Voidgloom Seraph 1.5B❤")!!.health)
		assertNull(PlusCombatLogic.parseBossTag("[Lv7] Graveyard Zombie 120/120❤"))
	}

	@Test
	fun chat() {
		assertEquals(5L to "Enchanted Obsidian", PlusCombatLogic.parseEnderNode("ENDER NODE! You found 5x Enchanted Obsidian!"))
		assertEquals(1L to "Ender Pearl", PlusCombatLogic.parseEnderNode("ENDER NODE! You found Ender Pearl!"))
		assertEquals("10⁑", PlusCombatLogic.armorStack("§c100/100❤  10⁑  §a50 Defense"))
		assertTrue(PlusCombatLogic.isArachneDown("        ARACHNE DOWN!"))
		assertEquals("Imminent", PlusCombatLogic.broodmotherStage(listOf(" Broodmother: Imminent")))
		assertTrue(PlusCombatLogic.isSummonSpawn("You have spawned your Zombie soul! (100 Mana)"))
	}
}
