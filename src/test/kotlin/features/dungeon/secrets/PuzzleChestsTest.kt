// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.dungeon.secrets

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PuzzleChestsTest {
	@Test fun puzzleRewardChestsAreNeverSecrets() {
		for (n in listOf("Three Weirdos", "Water Board", "Boulder", "Ice Path", "Creeper Beams", "Ice Fill", "Quiz", "Teleport Maze"))
			assertFalse(PuzzleChests.liveChestsAreSecrets("PUZZLE", n), n)
		assertFalse(PuzzleChests.liveChestsAreSecrets("PUZZLE", null))
	}

	@Test fun cataloguedPuzzleChestsAndNormalRoomsKeepTheirChests() {
		assertTrue(PuzzleChests.liveChestsAreSecrets("PUZZLE", "Tic Tac Toe"))
		assertTrue(PuzzleChests.liveChestsAreSecrets("PUZZLE", "Higher Blaze"))
		assertTrue(PuzzleChests.liveChestsAreSecrets("NORMAL", "Deathmite"))
		assertTrue(PuzzleChests.liveChestsAreSecrets(null, null))
	}

	@Test fun everyPuzzleInTheRoomListIsCovered() {
		val json = javaClass.getResource("/assets/apex/dungeon-rooms/secret-rooms.json")!!.readText()
		val puzzles = Regex("\"name\": \"([^\"]+)\",\\s*\"type\": \"PUZZLE\"").findAll(json).map { it.groupValues[1] }.toList()
		assertTrue(puzzles.size >= 11, "$puzzles")
		for (n in puzzles) assertTrue((n in PuzzleChests.CATALOGUED_CHEST_ROOMS) == PuzzleChests.liveChestsAreSecrets("PUZZLE", n))
	}
}
