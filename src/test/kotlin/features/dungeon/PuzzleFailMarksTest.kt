package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonMap
import dev.ashtonite365.apex.features.dungeon.PuzzleFailMarks
import dev.ashtonite365.apex.features.dungeon.PuzzleFailMarks.Puzzle

class PuzzleFailMarksTest {

	private val tab = listOf(
		"Party (4)", "Puzzles: (5)",
		" Higher Or Lower: [✖]",
		" Three Weirdos: [✔] (Ashton)",
		" Water Board: [✦]",
		" Creeper Beams: [✖]",
		" ???: [✦]",
		"Stats",
	)

	@Test
	fun tabListFailedEntries() {
		assertEquals(setOf(Puzzle.BLAZE, Puzzle.CREEPER_BEAMS), PuzzleFailMarks.failedFromTab(tab))
		assertEquals(emptySet<Puzzle>(), PuzzleFailMarks.failedFromTab(listOf("Puzzles: (2)", " Boulder: [✦]", " Ice Path: [✔]")))
		assertEquals(emptySet<Puzzle>(), PuzzleFailMarks.failedFromTab(listOf("no section")))
		assertEquals(setOf(Puzzle.ICE_PATH), PuzzleFailMarks.failedFromTab(listOf("Puzzles: (1)", " Ice Path: [✖]")))
	}

	@Test
	fun nameVariants() {
		assertEquals(Puzzle.BLAZE, PuzzleFailMarks.canonical("Higher Or Lower"))
		assertEquals(Puzzle.BLAZE, PuzzleFailMarks.canonical("higher or lower"))
		assertEquals(Puzzle.BLAZE, PuzzleFailMarks.canonical("Blaze"))
		assertEquals(Puzzle.THREE_WEIRDOS, PuzzleFailMarks.canonical("Three Weirdos"))
		assertEquals(Puzzle.WATER_BOARD, PuzzleFailMarks.canonical("Water Board"))
		assertEquals(Puzzle.WATER_BOARD, PuzzleFailMarks.canonical("Waterboard"))
		assertEquals(Puzzle.ICE_PATH, PuzzleFailMarks.canonical("Ice Path"))
		assertEquals(Puzzle.ICE_FILL, PuzzleFailMarks.canonical("Ice Fill"))
		assertEquals(Puzzle.CREEPER_BEAMS, PuzzleFailMarks.canonical("Creeper Beams"))
		assertEquals(Puzzle.TIC_TAC_TOE, PuzzleFailMarks.canonical("Tic Tac Toe"))
		assertNull(PuzzleFailMarks.canonical("???"))
		assertNull(PuzzleFailMarks.canonical("Trinity"))
	}

	@Test
	fun chatParsing() {

		assertEquals(PuzzleFailMarks.ChatFail("Steve", Puzzle.BLAZE), PuzzleFailMarks.parseChat("PUZZLE FAIL! Steve killed a Blaze in the wrong order! Yikes!"))
		assertEquals(PuzzleFailMarks.ChatFail("Steve", Puzzle.THREE_WEIRDOS), PuzzleFailMarks.parseChat("PUZZLE FAIL! Steve was fooled by Baxter! Yikes!"))
		assertEquals(PuzzleFailMarks.ChatFail("Steve", Puzzle.TIC_TAC_TOE), PuzzleFailMarks.parseChat("PUZZLE FAIL! Steve lost Tic Tac Toe! Yikes!"))
		assertEquals(PuzzleFailMarks.ChatFail("Steve", Puzzle.QUIZ),
			PuzzleFailMarks.parseChat("[STATUE] Oruo the Omniscient: Steve chose the wrong answer! I shall never forget this moment of misrememberance."))
		assertEquals(PuzzleFailMarks.ChatFail("Steve", null), PuzzleFailMarks.parseChat("PUZZLE FAIL! Steve did something odd! Yikes!"))

		assertNull(PuzzleFailMarks.parseChat("DUNGEON BUFF! Majorsceptic found a Blessing of Life II!"))
		assertNull(PuzzleFailMarks.parseChat("[BOSS] The Watcher: So you made it this far... interesting."))
	}

	@Test
	fun tabNameBindsToChatFailCell() {
		val s = PuzzleFailMarks.State()
		s.onChatFail(null, 2 to 1, now = 1_000)
		assertEquals(setOf(2 to 1), s.failedCells)
		s.onTabFailed(setOf(Puzzle.CREEPER_BEAMS), now = 3_000)
		assertEquals(2 to 1, s.byPuzzle[Puzzle.CREEPER_BEAMS])
		assertTrue(s.pending.isEmpty())

		s.onTabFailed(setOf(Puzzle.CREEPER_BEAMS, Puzzle.BOULDER), now = 3_000 + PuzzleFailMarks.CHAT_WINDOW_MS + 1)
		assertEquals(setOf(Puzzle.BOULDER), s.pending)
		assertEquals(setOf(2 to 1), s.failedCells)
	}

	@Test
	fun mapCrossPlacesPendingName() {
		val s = PuzzleFailMarks.State()
		s.onTabFailed(setOf(Puzzle.BLAZE), now = 0)
		assertEquals(setOf(Puzzle.BLAZE), s.pending)
		s.onMapCrosses(listOf(4 to 3))
		assertEquals(4 to 3, s.byPuzzle[Puzzle.BLAZE])
		assertEquals(setOf(4 to 3), s.failedCells)

		s.onChatFail(Puzzle.THREE_WEIRDOS, 0 to 2, now = 10)
		s.onMapCrosses(emptyList())
		assertEquals(setOf(4 to 3, 0 to 2), s.failedCells)
		assertEquals(0 to 2, s.byPuzzle[Puzzle.THREE_WEIRDOS])
	}

	@Test
	fun resetOnRunBoundary() {
		val s = PuzzleFailMarks.State()
		s.onMapCrosses(listOf(1 to 1)); s.onTabFailed(setOf(Puzzle.QUIZ), now = 0)
		val v = s.version

		assertTrue(PuzzleFailMarks.isRunBoundary("[NPC] Mort: Here, I found this map when I first entered the dungeon."))
		assertTrue(PuzzleFailMarks.isRunBoundary("                             > EXTRA STATS <"))
		assertFalse(PuzzleFailMarks.isRunBoundary("The BLOOD DOOR has been opened!"))
		s.reset()
		assertTrue(s.failedCells.isEmpty() && s.pending.isEmpty() && s.byPuzzle.isEmpty())
		assertTrue(s.version > v)

		s.onTabFailed(setOf(Puzzle.QUIZ), now = 0)
		assertEquals(setOf(Puzzle.QUIZ), s.pending)
	}

	private val size = DungeonMap.SIZE
	private fun room(c: ByteArray, gx: Int, gy: Int, len: Int, colour: Int, centre: Int? = null) {
		for (y in 0 until len) for (x in 0 until len) c[gx + x + (gy + y) * size] = colour.toByte()
		if (centre != null) for (y in -1..1) for (x in -1..1) c[gx + len / 2 + x + (gy + len / 2 + y) * size] = centre.toByte()
	}

	@Test
	fun failedStateReusesTickRendering() {
		val c = ByteArray(size * size)
		room(c, 10, 10, 16, 30)
		room(c, 30, 10, 16, PuzzleFailMarks.PUZZLE_ROOM, centre = 18)
		room(c, 50, 10, 16, PuzzleFailMarks.PUZZLE_ROOM)
		room(c, 70, 10, 16, 18)
		room(c, 90, 10, 16, 18, centre = 34)
		val grid = DungeonMap.detectGrid(c)!!
		val ticks = DungeonMap.roomTicks(c, grid)
		assertEquals(DungeonMap.Tick.FAILED, ticks[1 to 0])
		assertNull(ticks[3 to 0])
		assertEquals(DungeonMap.Tick.WHITE, ticks[4 to 0])
		assertTrue(PuzzleFailMarks.isPuzzleCell(c, grid, 2 to 0))
		assertFalse(PuzzleFailMarks.isPuzzleCell(c, grid, 3 to 0))

		val red = 0xFFAA0000.toInt()
		fun count(px: IntArray, argb: Int, gx: Int) = (0 until 16).sumOf { y -> (0 until 16).count { x -> px[gx + x + (10 + y) * size] == argb } }
		val px = DungeonMap.layoutPixels(c, grid, ticks = true, failed = setOf(2 to 0), showFailed = true, failArgb = red)
		val crossPixels = 13
		assertEquals(crossPixels, count(px, red, 30))
		assertEquals(crossPixels, count(px, red, 50))
		assertEquals(0, count(px, red, 70))
		assertTrue(count(px, DungeonMap.Tick.WHITE.argb, 90) > 0)

		assertEquals(crossPixels, count(DungeonMap.layoutPixels(c, grid, ticks = false, failed = setOf(2 to 0), failArgb = red), red, 50))

		val off = DungeonMap.layoutPixels(c, grid, ticks = true, failed = setOf(2 to 0), showFailed = false, failArgb = red)
		assertEquals(0, count(off, red, 30) + count(off, red, 50))
		assertEquals(DungeonMap.Tick.FAILED, DungeonMap.displayTick(c, grid, 2 to 0, setOf(2 to 0), true))
		assertNull(DungeonMap.displayTick(c, grid, 1 to 0, emptySet(), false))
	}
}
