// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.puzzles

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.puzzles.IceFillFloors
import dev.ashtonite365.apex.features.kit.puzzles.PuzzleHud
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver
import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs

class KitPuzzlesTest {
	private fun res(name: String) = javaClass.getResourceAsStream("/assets/apex/kit/puzzles/$name")!!.reader().readText()

	@Test
	fun quizTableLoadsAndMatches() {
		val table = QuizSolver.parse(res("quiz-answers.json"))
		assertTrue(table.size > 30)
		val ans = QuizSolver.answersFor("What is the status of Bonzo?", table, 0)
		assertEquals(listOf("New Necromancer"), ans)
		assertEquals(1, QuizSolver.correctOption("     ⓑ New Necromancer", ans!!))
		assertNull(QuizSolver.correctOption("     ⓐ Elle", ans))
		assertNull(QuizSolver.answersFor("hello", table, 0))
	}

	@Test
	fun skyblockYear() {
		assertEquals(1, QuizSolver.skyblockYear(1560276000_000L))
		assertEquals(listOf("Year 2"), QuizSolver.answersFor("What SkyBlock year is it?", emptyMap(), (1560276000L + 446400) * 1000))
	}

	@Test
	fun iceFillFloorsParseAndSolve() {
		val data = IceFillFloors.parse(res("ice-fill-floors.json"))
		assertEquals(3, data.identifier.size)
		assertEquals(data.identifier.map { it.size }, data.easy.map { it.size })
		assertEquals(data.identifier.map { it.size }, data.hard.map { it.size })

		val air = data.identifier.map { it[0][0] }.toSet()
		val path = IceFillFloors.solve(data, BlockPos.ZERO, false) { it in air }
		assertNotNull(path)
		assertEquals(data.easy.sumOf { it[0].size }, path!!.size)
		assertNull(IceFillFloors.solve(data, BlockPos.ZERO, false) { false })
	}

	@Test
	fun puzzleTabParse() {
		val s = PuzzleHud.parse(listOf("Party (3)", "Puzzles: (3)", " Ice Fill: [✔] (Ashton)", " Quiz: [✖]", " ???: [✦]", "Stats"))!!
		assertEquals(3, s.count)
		assertEquals(PuzzleHud.Entry("Ice Fill", PuzzleHud.Status.DONE, "Ashton"), s.entries[0])
		assertEquals(PuzzleHud.Status.FAILED, s.entries[1].status)
		assertEquals("???", s.entries[2].name)
		assertTrue(PuzzleHud.render(s, true)[1].contains("Ashton"))
		assertFalse(PuzzleHud.render(s, false)[1].contains("Ashton"))
		assertNull(PuzzleHud.parse(listOf("nothing")))
	}

	@Test
	fun starredMatching() {
		assertTrue(StarredMobs.isStarred("✯ Zombie 1,200❤", true))
		assertFalse(StarredMobs.isStarred("Zombie 1,200❤", true))
		assertTrue(StarredMobs.isStarred("✯ Foo", false))
		assertFalse(StarredMobs.isStarred("✯ Foo", true))
		assertEquals(12, StarredMobs.boxEdges(AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)).size)
	}
}
