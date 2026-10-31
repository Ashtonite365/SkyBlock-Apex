// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.puzzles

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms.RoomFrame
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver.AnswerBox
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver.RoomNow

class QuizAnswerBoxTest {
	private val quizCells = setOf(2 to 3)
	private val quizFrame = RoomFrame(BlockPos(-136, 0, -104), 90)
	private val inQuiz = RoomNow(quizCells, "Quiz")
	private val iceFrame = RoomFrame(BlockPos(-40, 0, -40), 0)
	private val inIcePath = RoomNow(setOf(5 to 5), "Ice Path")

	private fun answered(t: Long = 0): AnswerBox = AnswerBox().apply { question(quizFrame, quizCells, t); answer(0, t + 100) }

	@Test
	fun drawnInQuizRoomAtSavedQuizFrame() {
		val b = answered()
		assertEquals(quizFrame.toReal(QuizSolver.LOCAL_PADS[0]).below(), b.boxPos(inQuiz, 1_000))
	}

	@Test
	fun positionUsesSavedQuizFrameNotAnotherRoomsFrame() {
		val b = answered()
		val pos = b.boxPos(inQuiz, 500)!!
		assertNotEquals(iceFrame.toReal(QuizSolver.LOCAL_PADS[0]).below(), pos)
		assertEquals(quizFrame, b.frame)

		assertEquals(pos, answered().boxPos(RoomNow(quizCells, null), 500))
	}

	@Test
	fun notDrawnInAnotherRoomAndClearedOnLeave() {
		val b = answered()
		assertNull(b.boxPos(inIcePath, 3_000), "never drawn in Ice Path")
		assertFalse(b.active, "leaving the Quiz room without answering forgets the answer")
		assertNull(b.boxPos(inQuiz, 3_500), "coming back does not resurrect it")
	}

	@Test
	fun leaveWithoutAnsweringViaTickClears() {

		val b = answered()
		b.update(inIcePath, 3_000)
		assertFalse(b.active)
		assertNull(b.boxPos(inIcePath, 4_000))
		assertNull(b.boxPos(RoomNow(setOf(7 to 1), null), 5_000))
	}

	@Test
	fun sameCellsButOtherNameIsNotQuiz() {
		val b = answered()
		assertFalse(b.inQuizRoom(RoomNow(quizCells, "Ice Path")))
		assertNull(b.boxPos(RoomNow(quizCells, "Ice Path"), 100))
	}

	@Test
	fun unknownRoomHidesThenClearsAfterGrace() {
		val b = answered()
		val unknown = RoomNow(null, null)
		assertNull(b.boxPos(unknown, 1_000))
		assertTrue(b.active, "brief unreadable room keeps the answer")
		assertTrue(b.boxPos(inQuiz, 1_500) != null)
		assertNull(b.boxPos(unknown, 2_000))
		assertNull(b.boxPos(unknown, 2_000 + AnswerBox.UNKNOWN_GRACE_MS))
		assertFalse(b.active)
	}

	@Test
	fun clearedOnTimeout() {
		val b = answered()
		assertTrue(b.boxPos(inQuiz, AnswerBox.TIMEOUT_MS - 1) != null)
		val c = answered()
		assertNull(c.boxPos(inQuiz, 100 + AnswerBox.TIMEOUT_MS))
		assertFalse(c.active)
	}

	@Test
	fun clearedOnFailLine() {
		assertTrue(QuizSolver.isFailLine("[STATUE] Oruo the Omniscient: Ashtonite365 chose the wrong answer! I shall never forget this moment of misrememberance."))
		assertFalse(QuizSolver.isFailLine("[STATUE] Oruo the Omniscient: Ashtonite365 answered Question #1 correctly!"))
		val b = answered()
		b.clear()
		assertNull(b.boxPos(inQuiz, 200))
	}

	@Test
	fun answerIgnoredWhenQuestionNotPinnedToQuizRoom() {
		val b = AnswerBox()
		b.question(null, null, 0)
		b.answer(1, 10)
		assertFalse(b.active)
		assertNull(b.boxPos(inQuiz, 20))
	}

	@Test
	fun newQuestionDropsOldAnswer() {
		val b = answered()
		b.question(quizFrame, quizCells, 5_000)
		assertFalse(b.active)
		b.answer(2, 5_100)
		assertEquals(quizFrame.toReal(QuizSolver.LOCAL_PADS[2]).below(), b.boxPos(inQuiz, 5_200))
	}
}
