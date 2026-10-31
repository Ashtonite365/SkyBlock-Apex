// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.dungeon.LeapMenuOptions
import dev.ashtonite365.apex.features.kit.puzzles.PuzzleHud
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver

class KitH6bTest {
	@Test
	fun scoreboardClasses() {
		val m = LeapMenuOptions.classesFromScoreboard(listOf("[B] 151_Dragon 2,062❤", " [A] Lennart0312 17,165", "[M] [MVP] Bonzi DEAD", "Time Elapsed: 1m"))
		assertEquals(mapOf("151_dragon" to 'B', "lennart0312" to 'A', "bonzi" to 'M'), m)
	}

	@Test
	fun headNamesAndClassIndex() {
		assertEquals("Bonzi", LeapMenuOptions.headName("[MVP+] Bonzi"))
		assertEquals("Bonzi", LeapMenuOptions.headName("Bonzi"))
		val names = listOf("Odtheking", "Bonzi", null)
		assertEquals(1, LeapMenuOptions.indexOfClass(names, mapOf("bonzi" to 'M'), 'M'))
		assertEquals(-1, LeapMenuOptions.indexOfClass(names, mapOf("bonzi" to 'M'), 'T'))
	}

	@Test
	fun quadrants() {
		assertEquals(0, LeapMenuOptions.quadrantOf(10.0, 10.0, 100, 100))
		assertEquals(1, LeapMenuOptions.quadrantOf(60.0, 10.0, 100, 100))
		assertEquals(2, LeapMenuOptions.quadrantOf(10.0, 60.0, 100, 100))
		assertEquals(3, LeapMenuOptions.quadrantOf(50.0, 50.0, 100, 100))
	}

	@Test
	fun quizTimer() {
		assertEquals(220 to 1, QuizSolver.timerFor("[STATUE] Oruo the Omniscient: I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know."))
		assertEquals(100 to 3, QuizSolver.timerFor("[STATUE] Oruo the Omniscient: One more question!"))
		assertNull(QuizSolver.timerFor("hello"))
		assertEquals("§eQuestion 2: §a5.0s", QuizSolver.timerText(100, 2))
		assertEquals(3, QuizSolver.LOCAL_PADS.size)
	}

	@Test
	fun bossLines() {
		assertTrue(PuzzleHud.isBossLine("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable."))
		assertFalse(PuzzleHud.isBossLine("[BOSS] The Watcher: Congratulations, you made it through the Entrance."))
		assertFalse(PuzzleHud.isBossLine("Puzzles: (3)"))
	}

	@Test
	fun leapClicksSendContainerButtonZero() {
		assertEquals(0, LeapMenuOptions.CONTAINER_LEFT_CLICK)
		var sent: List<Any>? = null
		LeapMenuOptions.clickLeapSlot(7, 13) { c, i, b, t -> sent = listOf(c, i, b, t) }
		assertEquals(listOf(7, 13, 0, net.minecraft.world.inventory.ContainerInput.PICKUP), sent)
	}
}
