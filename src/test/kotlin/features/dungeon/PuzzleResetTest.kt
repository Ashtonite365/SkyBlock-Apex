// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleFailMarks
import dev.ashtonite365.apex.features.dungeon.PuzzleFailMarks.Puzzle

class PuzzleResetTest {
	private val puzzleRooms = setOf(2 to 1, 4 to 3)
	private val isPuzzle: (Pair<Int, Int>) -> Boolean = { it in puzzleRooms }

	private fun failed(): PuzzleFailMarks.State = PuzzleFailMarks.State().apply {
		onChatFail(Puzzle.CREEPER_BEAMS, 2 to 1, now = 0)
		onChatFail(Puzzle.BOULDER, 4 to 3, now = 0)
	}

	@Test fun resetMessageIsRecognised() {
		assertEquals(Puzzle.CREEPER_BEAMS, PuzzleFailMarks.parseReset("You used Architect's First Draft to reset the Creeper Beams puzzle!")?.puzzle)
		val unnamed = PuzzleFailMarks.parseReset("PUZZLE RESET! The puzzle has been reset.")
		assertTrue(unnamed != null); assertNull(unnamed!!.puzzle)
		assertNull(PuzzleFailMarks.parseReset("PUZZLE FAIL! Ashton killed a Blaze in the wrong order! Yikes!"))
		assertNull(PuzzleFailMarks.parseReset("Architect's First Draft x2 added to your sacks"))
	}

	@Test fun failThenResetMessageClearsTheX() {
		val s = failed()
		val r = PuzzleFailMarks.parseReset("You used Architect's First Draft to reset the Creeper Beams puzzle!")!!
		assertTrue(s.onReset(r.puzzle, 4 to 3, isPuzzle))
		assertEquals(setOf(4 to 3), s.failedCells.toSet())

		s.onMapCrosses(listOf(2 to 1, 4 to 3))
		assertEquals(setOf(4 to 3), s.failedCells.toSet())

		s.onTabFailed(setOf(Puzzle.CREEPER_BEAMS, Puzzle.BOULDER), now = 1_000, isPuzzleRoom = isPuzzle)
		assertEquals(setOf(4 to 3), s.failedCells.toSet())
	}

	@Test fun unnamedResetClearsThePlayersPuzzleRoomOnly() {
		val s = failed()
		assertTrue(s.onReset(null, 4 to 3, isPuzzle))
		assertEquals(setOf(2 to 1), s.failedCells.toSet())
	}

	@Test fun resetInANonPuzzleRoomClearsNothing() {
		val s = failed()
		assertFalse(s.onReset(null, 0 to 0, isPuzzle))
		assertFalse(s.onReset(null, null, isPuzzle))
		assertEquals(setOf(2 to 1, 4 to 3), s.failedCells.toSet())
	}

	@Test fun tabFailedBackToUnsolvedClears() {
		val s = failed()
		s.onTabFailed(setOf(Puzzle.CREEPER_BEAMS, Puzzle.BOULDER), now = 0, isPuzzleRoom = isPuzzle)
		assertEquals(2, s.failedCells.size)
		s.onTabFailed(setOf(Puzzle.BOULDER), now = 20_000, isPuzzleRoom = isPuzzle)
		assertEquals(setOf(4 to 3), s.failedCells.toSet())

		s.onMapCrosses(emptyList())
		s.onMapCrosses(listOf(2 to 1))
		assertTrue(2 to 1 in s.failedCells)
	}
}
