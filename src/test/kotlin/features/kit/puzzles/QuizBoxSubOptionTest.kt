// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.puzzles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.gui.config.SubOptionLook
import dev.ashtonite365.apex.settings.SettingsLayout

class QuizBoxSubOptionTest {
	private val c = PuzzleSolvers.TConfig
	private val quiz = c.allOptions.getValue("quiz")
	private val box = c.allOptions.getValue("quiz-box")

	@Test
	fun defaultOnKeepsKeyAndGreyAreaLabel() {
		c.explicitDefaultLoad()
		assertEquals(true, box.default())
		assertTrue(c.quizBox)
		assertEquals("quiz-box", box.propertyName)
		assertEquals(RiskLevel.GREY_AREA, box.riskLevel)

		assertEquals(RiskLevel.NONE, box.shownRisk)
		assertFalse(box.labelText.string.startsWith("⚠"))

		assertEquals(false, quiz.default())
		assertFalse(c.quiz)
		assertFalse(QuizSolver.boxEnabled())
	}

	@Test
	fun hiddenWhenQuizOff() {
		assertFalse(QuizSolver.boxShown(quiz = false, quizBox = true))
		assertFalse(QuizSolver.boxShown(quiz = false, quizBox = false))
		assertFalse(QuizSolver.boxShown(quiz = true, quizBox = false))
		assertTrue(QuizSolver.boxShown(quiz = true, quizBox = true))
	}

	@Test
	fun subOptionOfQuizInTheSolverBlock() {
		c.explicitDefaultLoad()
		assertSame(quiz, box.parentToggle)
		assertFalse(box.parentActive, "greyed out while Quiz is off")
		assertFalse(SubOptionLook.editable(box))
		assertTrue(SubOptionLook.editable(quiz))
		val keys = SettingsLayout.PUZZLE_SOLVER_KEYS
		assertEquals(keys.indexOf("quiz") + 1, keys.indexOf("quiz-box"), "shown right under Quiz, inside the solvers group")
	}
}
