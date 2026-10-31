package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonStatusLogic
import dev.ashtonite365.apex.features.dungeon.TerminalTracker
import dev.ashtonite365.apex.features.kuudra.KuudraLogic
import dev.ashtonite365.apex.features.kuudra.KuudraLogic.Supply

class KitPortLogicTest {
	@Test fun terminalSections() {
		val t = TerminalTracker()
		assertNull(t.onChat("Steve completed a terminal! (1/7)", 0))
		t.onChat("[BOSS] Goldor: Who dares trespass into my domain?", 1000)
		val c = t.onChat("Steve completed a terminal! (1/7)", 2000)!!
		assertEquals("Steve", c.name); assertEquals(1, t.terminals)
		t.onChat("Alex activated a lever! (2/7)", 2500); assertEquals(1, t.levers)
		t.onChat("Bob activated a device! (3/7)", 3000); assertTrue(t.device)
		t.onChat("Steve completed a terminal! (7/7)", 4000); assertEquals(1, t.section)
		t.onChat("The gate has been destroyed!", 5000)
		assertEquals(2, t.section); assertEquals(5, t.termsNeeded); assertEquals(listOf(4000L), t.sectionTimes)
		t.onChat("The Core entrance is opening!", 9000)
		assertEquals(listOf(4000L, 4000L), t.sectionTimes); assertTrue(!t.active)
	}

	@Test fun titleFormat() = assertEquals("§6Steve 1/7",
		TerminalTracker.formatTitle("&6{name} {current}/{total}", TerminalTracker.Completion("Steve", "completed", "terminal", 1, 7)))

	@Test fun blessingsAndRoman() {
		assertEquals(19, DungeonStatusLogic.romanToInt("XIX")); assertEquals(4, DungeonStatusLogic.romanToInt("IV"))
		val b = DungeonStatusLogic.parseBlessings("Dungeon Buffs\nBlessing of Power XIX\nBlessing of Time V")
		assertEquals(mapOf(DungeonStatusLogic.Blessing.POWER to 19, DungeonStatusLogic.Blessing.TIME to 5), b)
	}

	@Test fun breakerCharges() = assertEquals(17 to 20, DungeonStatusLogic.parseCharges(listOf("Some lore", "Charges: 17/20⸕")))

	@Test fun invincibility() {
		val bonzo = DungeonStatusLogic.Invincibility.match("Your ⚚ Bonzo's Mask saved your life!")!!
		assertEquals(DungeonStatusLogic.Invincibility.BONZO, bonzo)
		bonzo.proc(DungeonStatusLogic.loreCooldown(listOf("Cooldown: 120s")))
		repeat(20) { bonzo.tick() }
		assertEquals(40, bonzo.active); assertEquals(120 * 20 - 20, bonzo.cooldown)
		bonzo.reset()
	}

	@Test fun kuudra() {
		assertEquals("§a100.0K§7/§a100K§c❤", KuudraLogic.healthDisplay(100000f, 5))
		assertEquals("§496.00M§7/§a240M§c❤", KuudraLogic.healthDisplay(10000f, 5))
		assertEquals(Supply.Triangle, KuudraLogic.preSpotAt(-70.0, -120.0))
		assertEquals(Supply.None, KuudraLogic.preSpotAt(0.0, 0.0))
		assertEquals("No Triangle!", KuudraLogic.missingCall(Supply.Triangle, listOf(-81.0 to -143.0)))
		assertEquals("No Shop!", KuudraLogic.missingCall(Supply.Triangle, listOf(-67.0 to -122.0)))
		assertNull(KuudraLogic.missingCall(Supply.Triangle, listOf(-67.0 to -122.0, -81.0 to -143.0)))
		assertEquals("Go X Cannon", KuudraLogic.cratePriority(Supply.Shop, Supply.X, false))
		assertEquals("FRESH", KuudraLogic.partyFreshRegex.matchEntire("Party > [MVP+] Steve: FRESH")?.let { "FRESH" })
	}
}
