package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonScore

class DungeonScoreTest {
	private fun calc(
		floor: String = "F7", cleared: Double = 1.0, secrets: Double = 1.0, crypts: Int = 5, deaths: Int = 0,
		puzzles: Int = 0, seconds: Int = 600, mimic: Boolean = true, paul: Boolean = false, spirit: Boolean = true,
	) = DungeonScore.compute(floor, cleared, secrets, crypts, deaths, puzzles, seconds, mimic, paul, spirit)

	@Test fun perfectF7() = assertEquals(307, calc().total)

	@Test fun deaths() {
		assertEquals(99, calc(deaths = 1).skill)
		assertEquals(98, calc(deaths = 1, spirit = false).skill)
		assertEquals(95, calc(deaths = 3).skill)
	}

	@Test fun unstartedPuzzleIsPenalised() {

		assertEquals(2, DungeonScore.countIncompletePuzzles(listOf("Puzzles: (3)", "Ice Path: [✦]", "Boulder: [✖]", "Creeper Beams: [✔]")))
		assertEquals(90, calc(puzzles = 1).skill)
	}

	@Test fun exploreUsesFloorSecretRequirement() {
		assertEquals(100, calc(floor = "F5", secrets = 0.7).explore)
		assertEquals(60 + 20, calc(floor = "F5", secrets = 0.35).explore)
		assertEquals(60 + 20, calc(floor = "M7", secrets = 0.5).explore)
	}

	@Test fun speedUsesFloorTimeLimit() {
		assertEquals(100, calc(floor = "F7", seconds = 840).speed)
		assertEquals(100, calc(floor = "M5", seconds = 479).speed)
		assertEquals(99, calc(floor = "M5", seconds = 492).speed)
		assertEquals(90, calc(floor = "F1", seconds = 720).speed)
		assertEquals(80, calc(floor = "M1", seconds = 840).speed)
	}

	@Test fun bonus() {
		assertEquals(7, calc(floor = "F7").bonus)
		assertEquals(5, calc(floor = "M5").bonus)
		assertEquals(17, calc(floor = "M6", paul = true).bonus)
		assertEquals(3, calc(floor = "F7", crypts = 3, mimic = false).bonus)
	}

	@Test fun elapsed() = assertEquals(106, DungeonScore.parseElapsed(listOf("Cleared: 60% (105)", "Time Elapsed: 1m 46s")))
}
