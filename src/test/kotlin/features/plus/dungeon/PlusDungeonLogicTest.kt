package dev.ashtonite365.apex.test.features.plus.dungeon

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonLogic

class PlusDungeonLogicTest {
	@Test
	fun bloodPrediction() {
		assertEquals(36.seconds, PlusDungeonLogic.bloodMovePrediction(32.seconds))
		assertEquals(24.seconds, PlusDungeonLogic.bloodMovePrediction(10.seconds))
		assertNull(PlusDungeonLogic.bloodMovePrediction(40.seconds))
	}

	@Test
	fun deaths() {
		assertTrue(PlusDungeonLogic.isDeath(" ☠ You were killed by Someone and became a ghost."))
		assertTrue(PlusDungeonLogic.isDeath(" ☠ Someone disconnected from the Dungeon and became a ghost."))
		assertTrue(PlusDungeonLogic.isDeath(" ☠ Someone burnt to death and became a ghost."))
		assertFalse(PlusDungeonLogic.isDeath("☠ Defeated Maxor"))
	}

	@Test
	fun bossMessages() {
		assertTrue(PlusDungeonLogic.isBossMessage("[BOSS] Bonzo: Gratz for making it this far"))
		assertFalse(PlusDungeonLogic.isBossMessage("[BOSS] The Watcher: You have proven yourself. You may pass."))
		assertEquals("Sadan", PlusDungeonLogic.floorBossOf("[BOSS] Sadan: ENOUGH!"))
		assertNull(PlusDungeonLogic.floorBossOf("[BOSS] The Watcher: Oh.. hello?"))
	}

	@Test
	fun copilotAndFilter() {
		assertEquals("Open Wither Door", PlusDungeonLogic.copilotStep("A Wither Key was picked up!", "me"))
		assertEquals("Open Blood Door", PlusDungeonLogic.copilotStep("Steve has obtained Blood Key!", "me"))
		assertEquals(PlusDungeonLogic.FilterType.NOT_POSSIBLE, PlusDungeonLogic.filterType("This chest has already been searched!"))
		assertEquals(PlusDungeonLogic.FilterType.BUFF, PlusDungeonLogic.filterType("DUNGEON BUFF! Steve found a Blessing of Power II!"))
		assertNull(PlusDungeonLogic.filterType("[BOSS] Bonzo: hi"))
	}

	@Test
	fun misc() {
		assertEquals("Steve", PlusDungeonLogic.puzzleFailer("PUZZLE FAIL! Steve killed a Blaze in the wrong order!"))
		assertEquals("Chissl", PlusDungeonLogic.enteredPlayer("[MVP+] Chissl entered MM The Catacombs, Floor I!"))
		assertTrue(PlusDungeonLogic.isMilestone("Healer Milestone ❷: You have healed 50,000 HP so far! 1m 2s"))
		assertEquals("M3", PlusDungeonLogic.floorOf(listOf("Late Spring", " ⏣ The Catacombs (M3)")))
		assertEquals(5, PlusDungeonLogic.puzzleCount(listOf("Puzzles: (5)")))
		assertTrue(PlusDungeonLogic.isChestsSpawned("                Master Mode The Catacombs - Floor III"))
		val low = PlusDungeonLogic.parseTeammate("§e[H] §bFlameeke §c8,998§c❤")!!
		assertEquals("Flameeke", low.name); assertTrue(low.isLow)
		assertFalse(PlusDungeonLogic.parseTeammate("§e[A] §6Lennart0312 §a17,165§c❤")!!.isLow)
	}
}
