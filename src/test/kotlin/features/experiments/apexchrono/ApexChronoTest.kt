package dev.ashtonite365.apex.test.features.experiments.apexchrono

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.experiments.apexchrono.ApexChronoModel
import dev.ashtonite365.apex.features.experiments.apexchrono.ApexChronoSolver
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoMark
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoMarkKind.CURRENT
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoMarkKind.NEXT
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoMarkKind
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoPhase
import dev.ashtonite365.apex.features.experiments.apexchrono.ChronoSlot
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ApexChronoTest {
	private val dump = RealDumpFixtures.load("chrono-transcendent-r1")

	private fun board(lit: Set<String> = emptySet(), status: Pair<String, String> = "Remember the pattern!" to "glowstone", round: Int = 1) =
		dump.slots.map { s ->
			when {
				s.slot == 4 -> ChronoSlot(4, "Round: $round", "paper")
				s.slot == 49 -> ChronoSlot(49, status.first, status.second)
				s.name.isBlank() -> ChronoSlot(s.slot, "", "black_stained_glass_pane")
				s.name.lowercase() in lit -> ChronoSlot(s.slot, s.name, s.name.lowercase() + "_terracotta")
				else -> ChronoSlot(s.slot, s.name, s.name.lowercase() + "_stained_glass")
			}
		}
	private val replay = "Timer: 10s" to "clock"

	private fun show(vararg notes: String): ApexChronoSolver = ApexChronoSolver().also { c ->
		c.update(board())
		for (n in notes) { c.update(board(setOf(n))); c.update(board()) }
		c.update(board(status = replay))
	}

	@Test
	fun realDumpTitleRoundPhaseAndGroups() {
		assertEquals("Transcendent", ApexChronoModel.tier(dump.title))
		for (t in listOf("High", "Grand", "Supreme", "Transcendent", "Metaphysical")) assertEquals(t, ApexChronoModel.tier("Chronomatron ($t)"))
		assertNull(ApexChronoModel.tier("Chronomatron ➜ Stakes"))
		val c = ApexChronoSolver(); c.update(board())
		assertEquals(ChronoPhase.SHOW, c.phase); assertEquals(1, c.round)
		val expect = mapOf("red" to setOf(11, 20), "blue" to setOf(12, 21), "lime" to setOf(13, 22), "yellow" to setOf(14, 23),
			"aqua" to setOf(30, 39), "pink" to setOf(31, 40), "green" to setOf(32, 41), "cyan" to setOf(33, 42))
		for ((g, s) in expect) assertEquals(s, c.slotsOf(g), g)
		assertNull(c.groupOf(49)); assertNull(c.groupOf(4)); assertNull(c.groupOf(0))
	}

	@Test
	fun recordsShowAndReplaysGreenYellowAdvance() {
		val c = show("red", "blue", "cyan")
		assertEquals(listOf("red", "blue", "cyan"), c.sequence)
		assertEquals(ChronoPhase.REPLAY, c.phase)
		assertEquals(ChronoMark(CURRENT, 1, 1), c.markFor(11)); assertEquals(ChronoMark(CURRENT, 1, 1), c.markFor(20))
		assertEquals(ChronoMark(NEXT, 2, 1), c.markFor(12)); assertNull(c.markFor(33))
		c.onClick(20)
		assertEquals(ChronoMark(CURRENT, 2, 1), c.markFor(21)); assertEquals(ChronoMark(NEXT, 3, 1), c.markFor(42)); assertNull(c.markFor(11))
		c.onClick(30)
		assertEquals(1, c.index)
		c.onClick(12); c.onClick(33)
		assertNull(c.markFor(33)); assertEquals(3, c.index)
	}

	@Test
	fun repeatsCountDown() {

		val c = show("cyan", "cyan", "red")
		assertEquals(listOf("cyan", "cyan", "red"), c.sequence)
		assertEquals(ChronoMark(CURRENT, 1, 2), c.markFor(42)); assertEquals(ChronoMark(NEXT, 3, 1), c.markFor(20))
		c.onClick(42)
		assertEquals(ChronoMark(CURRENT, 2, 1), c.markFor(33)); assertEquals(ChronoMark(NEXT, 3, 1), c.markFor(11))
		c.onClick(33)
		assertEquals(ChronoMark(CURRENT, 3, 1), c.markFor(20)); assertNull(c.markFor(42))
	}

	@Test
	fun glintAlsoCountsAsLitAndNewRoundResets() {
		val c = ApexChronoSolver()
		c.update(board())
		c.update(board().map { if (it.index == 31 || it.index == 40) it.copy(glint = true) else it })
		c.update(board())
		c.update(board(status = replay))
		assertEquals(listOf("pink"), c.sequence)
		c.onClick(40)

		c.update(board(setOf("pink"), round = 2))
		c.update(board(round = 2))
		c.update(board(setOf("pink"), round = 2)); c.update(board(round = 2))
		c.update(board(setOf("lime"), round = 2)); c.update(board(round = 2))
		c.update(board(status = replay, round = 2))
		assertEquals(listOf("pink", "lime"), c.sequence)
		assertEquals(2, c.round); assertEquals(0, c.index)
	}

	@Test
	fun runLength() {
		assertEquals(3, ApexChronoModel.runAt(listOf("a", "a", "a", "b"), 0))
		assertEquals(1, ApexChronoModel.runAt(listOf("a", "a", "a", "b"), 3))
		assertTrue(ApexChronoModel.runAt(emptyList(), 0) == 0)
	}
}

class ApexChronoFixTest {
	private val dump = RealDumpFixtures.load("chrono-transcendent-r1")
	private fun board(lit: Set<String> = emptySet(), timer: Boolean = false, round: Int = 1, terracottaBoard: Boolean = false) =
		dump.slots.map { s ->
			val c = s.name.lowercase()
			when {
				s.slot == 4 -> ChronoSlot(4, "Round: $round", "paper")
				s.slot == 49 -> if (timer) ChronoSlot(49, "Timer: 10s", "clock") else ChronoSlot(49, "Remember the pattern!", "glowstone")
				s.name.isBlank() -> ChronoSlot(s.slot, "", "black_stained_glass_pane")
				terracottaBoard -> ChronoSlot(s.slot, s.name, if (c in lit) "${c}_stained_glass" else "${c}_terracotta")
				else -> ChronoSlot(s.slot, s.name, if (c in lit) "${c}_terracotta" else "${c}_stained_glass_pane")
			}
		}

	private fun playRound(c: ApexChronoSolver, round: Int, notes: List<String>) {
		c.update(board(round = round))
		for (n in notes) { c.update(board(setOf(n), round = round)); c.update(board(round = round)) }
		c.update(board(timer = true, round = round))
		assertEquals(notes, c.sequence, "round $round")
		for ((i, n) in notes.withIndex()) {
			assertEquals(ChronoMarkKind.CURRENT, c.markFor(c.slotsOf(n).first())!!.kind)
			assertEquals(i + 1, c.markFor(c.slotsOf(n).last())!!.number)
			c.onClick(c.slotsOf(n).last())
			c.update(board(setOf(n), timer = true, round = round))
			c.update(board(timer = true, round = round))
		}
		assertEquals(notes.size, c.index)
	}

	@Test
	fun detectionOnRealDump() {
		val c = ApexChronoSolver()
		c.update(board())
		assertEquals(ChronoPhase.SHOW, c.phase)
		assertTrue(!c.isTerracottaBoard)
		assertEquals(16, (0 until 54).count { c.groupOf(it) != null })
		assertEquals(49, ApexChronoModel.status(board())!!.index)
	}

	@Test
	fun phaseSwitchingAndUnknownStatusKeepsPhase() {
		val c = ApexChronoSolver()
		assertEquals(ChronoPhase.UNKNOWN, c.phase)
		c.update(board()); assertEquals(ChronoPhase.SHOW, c.phase)
		c.update(board().map { if (it.index == 49) ChronoSlot(49, "", "air") else it }); assertEquals(ChronoPhase.SHOW, c.phase)
		c.update(board(timer = true)); assertEquals(ChronoPhase.REPLAY, c.phase)
		c.update(board(round = 2)); assertEquals(ChronoPhase.SHOW, c.phase); assertEquals(2, c.round)
	}

	@Test
	fun finalNoteFlashingAfterTimerFlipIsKept() {
		val c = ApexChronoSolver()
		c.update(board())
		c.update(board(setOf("red"))); c.update(board())
		c.update(board(setOf("blue"), timer = true))
		c.update(board(timer = true))
		assertEquals(listOf("red", "blue"), c.sequence)
		c.onClick(11)
		c.update(board(setOf("green"), timer = true))
		assertEquals(listOf("red", "blue"), c.sequence)
		assertEquals(ChronoMark(ChronoMarkKind.CURRENT, 2, 1), c.markFor(21))
	}

	@Test
	fun olderTerracottaBoardLightsAsGlass() {
		val c = ApexChronoSolver()
		c.update(board(terracottaBoard = true))
		assertTrue(c.isTerracottaBoard)
		c.update(board(setOf("lime"), terracottaBoard = true)); c.update(board(terracottaBoard = true))
		c.update(board(setOf("pink"), terracottaBoard = true)); c.update(board(terracottaBoard = true))
		c.update(board(timer = true, terracottaBoard = true))
		assertEquals(listOf("lime", "pink"), c.sequence)
	}

	@Test
	fun multiRoundSequencesGrow() {
		val c = ApexChronoSolver()
		playRound(c, 1, listOf("cyan"))
		playRound(c, 2, listOf("cyan", "cyan"))
		playRound(c, 3, listOf("cyan", "cyan", "red"))
		playRound(c, 4, listOf("cyan", "cyan", "red", "aqua"))
		assertEquals(4, c.completedRounds)
	}

	@Test
	fun overlappingFlashesAreSeparateNotes() {
		val c = ApexChronoSolver()
		c.update(board())
		c.update(board(setOf("red"))); c.update(board(setOf("red", "yellow"))); c.update(board(setOf("yellow")))
		c.update(board(setOf("yellow", "aqua"))); c.update(board())
		c.update(board(timer = true))
		assertEquals(listOf("red", "yellow", "aqua"), c.sequence)
	}
}

class ExperimentSilentAutoClickTest {
	private val dump = RealDumpFixtures.load("chrono-transcendent-r1")
	private fun board(lit: Set<String> = emptySet(), timer: Boolean = false, round: Int = 1) = dump.slots.map { s ->
		val c = s.name.lowercase()
		when {
			s.slot == 4 -> ChronoSlot(4, "Round: $round", "paper")
			s.slot == 49 -> if (timer) ChronoSlot(49, "Timer: 10s", "clock") else ChronoSlot(49, "Remember the pattern!", "glowstone")
			s.name.isBlank() -> ChronoSlot(s.slot, "", "black_stained_glass_pane")
			else -> ChronoSlot(s.slot, s.name, if (c in lit) "${c}_terracotta" else "${c}_stained_glass_pane")
		}
	}

	private fun run(autoClickObserved: Boolean): ApexChronoSolver {
		val c = ApexChronoSolver()
		c.update(board()); c.update(board(setOf("lime"))); c.update(board(timer = true))

		if (autoClickObserved) c.onClick(13)
		c.update(board(setOf("lime"), timer = true, round = 2))
		c.update(board(setOf("lime"), round = 2))
		c.update(board(round = 2))
		c.update(board(setOf("lime"), round = 2)); c.update(board(round = 2))
		c.update(board(setOf("lime"), round = 2)); c.update(board(round = 2))
		c.update(board(timer = true, round = 2))
		return c
	}

	@Test
	fun chronomatronRound2CountsTwoNotesNotThree() {
		assertEquals(listOf("lime", "lime", "lime"), run(autoClickObserved = false).sequence)
		val c = run(autoClickObserved = true)
		assertEquals(listOf("lime", "lime"), c.sequence)
		assertEquals(1, c.completedRounds)
	}
}
