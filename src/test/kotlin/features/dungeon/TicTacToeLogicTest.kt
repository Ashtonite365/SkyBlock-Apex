package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import dev.ashtonite365.apex.features.dungeon.TicTacToeLogic
import dev.ashtonite365.apex.features.dungeon.TttMark

class TicTacToeLogicTest {
	private fun b(s: String) = s.filter { it in "XO." }.map { when (it) { 'X' -> TttMark.X; 'O' -> TttMark.O; else -> TttMark.EMPTY } }

	@Test fun takesWin() = assertEquals(2, TicTacToeLogic.bestMove(b("XX. OO. ..."), TttMark.X))
	@Test fun takesWinOverBlock() = assertEquals(5, TicTacToeLogic.bestMove(b("XX. OO. X..")))
	@Test fun blocks() = assertEquals(2, TicTacToeLogic.bestMove(b("OO. X.. ..X"), TttMark.X))
	@Test fun emptyBoardCentre() = assertEquals(4, TicTacToeLogic.bestMove(b("... ... ...")))
	@Test fun answersCornerWithCentre() = assertEquals(4, TicTacToeLogic.bestMove(b("O.. ... ..."), TttMark.X))
	@Test fun finishedWonBoard() = assertNull(TicTacToeLogic.bestMove(b("XXX OO. ...")))
	@Test fun finishedFullBoard() = assertNull(TicTacToeLogic.bestMove(b("XOX XOO OXX")))
	@Test fun winnerDetection() {
		assertEquals(TttMark.O, TicTacToeLogic.winner(b("O.X .OX ..O")))
		assertNull(TicTacToeLogic.winner(b("XOX XOO OXX")))
	}
	@Test fun toMoveIsMinority() {
		assertEquals(TttMark.X, TicTacToeLogic.toMove(b("O.. ... ...")))
		assertEquals(TttMark.O, TicTacToeLogic.toMove(b("X.. ... ...")))
	}
	@Test fun perfectPlayNeverLoses() {

		fun play(board: MutableList<TttMark>, turn: TttMark) {
			TicTacToeLogic.winner(board)?.let { assert(it != TttMark.O) { "solver lost: $board" }; return }
			if (board.none { it == TttMark.EMPTY }) return
			if (turn == TttMark.X) {
				val m = TicTacToeLogic.bestMove(board, TttMark.X)!!
				board[m] = TttMark.X; play(board, TttMark.O); board[m] = TttMark.EMPTY
			} else for (i in 0..8) if (board[i] == TttMark.EMPTY) {
				board[i] = TttMark.O; play(board, TttMark.X); board[i] = TttMark.EMPTY
			}
		}
		play(MutableList(9) { TttMark.EMPTY }, TttMark.O)
		play(MutableList(9) { TttMark.EMPTY }, TttMark.X)
	}

	private fun map(centre: Int, fill: Int = 0, count: Int = 0) = ByteArray(128 * 128).also { a ->
		for (i in 0 until count) a[i] = fill.toByte()
		a[64 * 128 + 64] = centre.toByte()
	}
	@Test fun mapColours() {
		assertEquals(TttMark.X, TicTacToeLogic.markFromMapColors(map(114)))
		assertEquals(TttMark.O, TicTacToeLogic.markFromMapColors(map(33)))
		assertEquals(TttMark.EMPTY, TicTacToeLogic.markFromMapColors(map(0)))
		assertEquals(TttMark.X, TicTacToeLogic.markFromMapColors(map(0, 112, 500)))
		assertEquals(TttMark.O, TicTacToeLogic.markFromMapColors(map(0, 34, 500)))
		assertEquals(TttMark.EMPTY, TicTacToeLogic.markFromMapColors(ByteArray(10)))
	}

	@Test fun arrangeGrid() {
		val cells = listOf(
			TicTacToeLogic.Cell(10, 72, TttMark.X, "tl"),
			TicTacToeLogic.Cell(12, 72, TttMark.O, "tr"),
			TicTacToeLogic.Cell(11, 71, TttMark.X, "mid"),
			TicTacToeLogic.Cell(12, 70, TttMark.EMPTY, "br"),
		)
		val (board, payload) = TicTacToeLogic.arrange(cells)!!
		assertEquals(b("X.O .X. ..."), board)
		assertEquals("tl", payload[0]); assertEquals("mid", payload[4]); assertEquals("br", payload[8]); assertNull(payload[1])
	}
	@Test fun arrangeRejectsBadShapes() {
		assertNull(TicTacToeLogic.arrange(listOf(TicTacToeLogic.Cell(0, 0, TttMark.X, 1), TicTacToeLogic.Cell(5, 0, TttMark.X, 2))))
		assertNull(TicTacToeLogic.arrange(listOf(TicTacToeLogic.Cell(0, 0, TttMark.X, 1), TicTacToeLogic.Cell(0, 0, TttMark.O, 2), TicTacToeLogic.Cell(2, 2, TttMark.O, 3))))
		assertNull(TicTacToeLogic.arrange(emptyList<TicTacToeLogic.Cell<Int>>()))
		assertNotNull(TicTacToeLogic.arrange(listOf(TicTacToeLogic.Cell(0, 0, TttMark.X, 1), TicTacToeLogic.Cell(2, 2, TttMark.O, 3))))
	}

	@Test fun openingCellIsMiddleRow() {
		assertEquals(1, TicTacToeLogic.OPENING_ROW); assertEquals(2, TicTacToeLogic.OPENING_COL)
		assertEquals(5, TicTacToeLogic.OPENING_CELL)
	}
	@Test fun emptyBoardHighlightsOpeningCell() {
		assertEquals(TicTacToeLogic.OPENING_CELL, TicTacToeLogic.suggestedMove(b("... ... ...")))
		assertEquals(TicTacToeLogic.OPENING_CELL, TicTacToeLogic.openingMove(b("... ... ...")))

		assertEquals(4, TicTacToeLogic.bestMove(b("... ... ...")))
	}
	@Test fun openingCellTakenFallsBack() {
		val board = b("... ..X ...")
		assertNull(TicTacToeLogic.openingMove(board))
		assertEquals(TicTacToeLogic.bestMove(board), TicTacToeLogic.suggestedMove(board))
		val byO = b("... ..O ...")
		assertNull(TicTacToeLogic.openingMove(byO))
		assertEquals(TicTacToeLogic.bestMove(byO), TicTacToeLogic.suggestedMove(byO))
	}
	@Test fun openingNeverWorseThanSolver() {

		val centre = b("... .X. ...")
		assertNull(TicTacToeLogic.openingMove(centre))
		assertEquals(TicTacToeLogic.bestMove(centre), TicTacToeLogic.suggestedMove(centre))
	}
	@Test fun forcedMovesUnchanged() {
		for (s in listOf("XX. OO. ...", "XX. OO. X..", "OO. X.. ..X", "O.. ... ...", "X.O .X. ..O", "OX. .X. ...")) {
			val board = b(s)
			assertEquals(TicTacToeLogic.bestMove(board), TicTacToeLogic.suggestedMove(board), s)
			assertEquals(TicTacToeLogic.bestMove(board, TttMark.X), TicTacToeLogic.suggestedMove(board, TttMark.X), s)
		}
		assertEquals(2, TicTacToeLogic.suggestedMove(b("XX. OO. ..."), TttMark.X))
		assertEquals(2, TicTacToeLogic.suggestedMove(b("OO. X.. ..X"), TttMark.X))
	}
	@Test fun laterMovesUnchangedExhaustive() {

		val seen = HashSet<List<TttMark>>()
		fun walk(board: MutableList<TttMark>) {
			if (!seen.add(board.toList()) || TicTacToeLogic.isFinished(board)) return
			val me = TicTacToeLogic.toMove(board)
			if (board.any { it == me }) assertEquals(TicTacToeLogic.bestMove(board), TicTacToeLogic.suggestedMove(board), board.toString())
			else {
				val o = TicTacToeLogic.openingMove(board)
				if (o == null) assertEquals(TicTacToeLogic.bestMove(board), TicTacToeLogic.suggestedMove(board), board.toString())
				else assertEquals(TicTacToeLogic.OPENING_CELL, o)
			}
			for (i in 0..8) if (board[i] == TttMark.EMPTY) { board[i] = me; walk(board); board[i] = TttMark.EMPTY }
		}
		walk(MutableList(9) { TttMark.EMPTY })
	}
	@Test fun suggestedPlayNeverLoses() {
		fun play(board: MutableList<TttMark>, turn: TttMark, solver: TttMark) {
			TicTacToeLogic.winner(board)?.let { assert(it == solver) { "solver lost: $board" }; return }
			if (board.none { it == TttMark.EMPTY }) return
			if (turn == solver) {
				val m = TicTacToeLogic.suggestedMove(board, solver)!!
				board[m] = solver; play(board, turn.other, solver); board[m] = TttMark.EMPTY
			} else for (i in 0..8) if (board[i] == TttMark.EMPTY) {
				board[i] = turn; play(board, turn.other, solver); board[i] = TttMark.EMPTY
			}
		}
		play(MutableList(9) { TttMark.EMPTY }, TttMark.X, TttMark.X)
		play(MutableList(9) { TttMark.EMPTY }, TttMark.X, TttMark.O)
	}

	private val tl = BlockPos(10, 72, 5)
	@Test fun openingFacingSouth() =
		assertEquals(BlockPos(12, 71, 5), TicTacToeLogic.targetFor(tl, Direction.SOUTH, emptyList()))
	@Test fun openingFacingNorth() =
		assertEquals(BlockPos(8, 71, 5), TicTacToeLogic.targetFor(tl, Direction.NORTH, emptyList()))
	@Test fun openingFacingEast() =
		assertEquals(BlockPos(10, 71, 3), TicTacToeLogic.targetFor(tl, Direction.EAST, emptyList()))
	@Test fun openingFacingWest() =
		assertEquals(BlockPos(10, 71, 7), TicTacToeLogic.targetFor(tl, Direction.WEST, emptyList()))
	@Test fun frameMappingFollowsFacing() {

		assertEquals(5, TicTacToeLogic.cellIndex(tl, Direction.SOUTH, BlockPos(12, 71, 5)))
		assertEquals(5, TicTacToeLogic.cellIndex(tl, Direction.EAST, BlockPos(10, 71, 3)))
		assertEquals(3, TicTacToeLogic.cellIndex(tl, Direction.EAST, BlockPos(10, 71, 5)))
		assertNull(TicTacToeLogic.cellIndex(tl, Direction.SOUTH, BlockPos(13, 71, 5)))
		val taken = listOf(BlockPos(10, 71, 3) to TttMark.X)
		val board = TicTacToeLogic.boardAt(tl, Direction.EAST, taken)!!
		assertEquals(b("... ..X ..."), board)
		assertEquals(TicTacToeLogic.cellPos(tl, Direction.EAST, TicTacToeLogic.bestMove(board)!!),
			TicTacToeLogic.targetFor(tl, Direction.EAST, taken))
	}
	@Test fun locatedForcedMoveUnchanged() {

		val f = listOf(BlockPos(10, 72, 5) to TttMark.X, BlockPos(9, 72, 5) to TttMark.X,
			BlockPos(10, 71, 5) to TttMark.O, BlockPos(9, 71, 5) to TttMark.O)
		val board = TicTacToeLogic.boardAt(tl, Direction.NORTH, f)!!
		assertEquals(b("XX. OO. ..."), board)
		assertEquals(BlockPos(8, 72, 5), TicTacToeLogic.targetFor(tl, Direction.NORTH, f))
	}
	@Test fun finishedBoardNoTarget() {
		val f = listOf(BlockPos(10, 72, 5) to TttMark.X, BlockPos(11, 72, 5) to TttMark.X, BlockPos(12, 72, 5) to TttMark.X)
		assertNull(TicTacToeLogic.targetFor(tl, Direction.SOUTH, f))
	}
	@Test fun fullSpanLocateMatchesPreviousArrange() {

		val rnd = java.util.Random(7)
		for (facing in listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) repeat(300) {
			val board = MutableList(9) { TttMark.EMPTY }
			val placed = (0..8).shuffled(rnd).take(3 + rnd.nextInt(5))
			placed.forEachIndexed { k, i -> board[i] = if (k % 2 == 0) TttMark.X else TttMark.O }
			val frames = placed.map { TicTacToeLogic.cellPos(tl, facing, it) to board[it] }
			val cells = frames.map { (p, m) -> TicTacToeLogic.Cell(TicTacToeLogic.horizontalOf(p, facing), p.y, m, p) }
			val old = TicTacToeLogic.arrange(cells)
			val topLeft = TicTacToeLogic.topLeftFromFrames(frames.map { it.first }, facing)
			if (old == null) { assertNull(topLeft); return@repeat }
			assertEquals(tl, topLeft)
			val (oldBoard, oldPos) = old
			assertEquals(oldBoard, TicTacToeLogic.boardAt(tl, facing, frames))
			if (oldBoard.any { it == TicTacToeLogic.toMove(oldBoard) }) {
				val m = TicTacToeLogic.bestMove(oldBoard)
				val oldTarget = m?.let { oldPos[it] ?: TicTacToeLogic.cellPos(tl, facing, it) }
				assertEquals(oldTarget, TicTacToeLogic.targetFor(tl, facing, frames))
			}
		}
	}

	@Test fun turnFromMarks() {
		assertEquals(TicTacToeLogic.Turn.BOARD, TicTacToeLogic.turn(b("... ... ...")))
		assertEquals(TicTacToeLogic.Turn.PLAYER, TicTacToeLogic.turn(b("... .X. ...")))
		assertEquals(TicTacToeLogic.Turn.BOARD, TicTacToeLogic.turn(b("O.. .X. ...")))
		assertEquals(TicTacToeLogic.Turn.PLAYER, TicTacToeLogic.turn(b("O.. .X. ..X")))
		assertEquals(TicTacToeLogic.Turn.INVALID, TicTacToeLogic.turn(b("XX. ... ...")))
		assertEquals(TicTacToeLogic.Turn.OVER, TicTacToeLogic.turn(b("XXX OO. ...")))
	}
	@Test fun startOfPuzzleNoHintBeforeBoardOpens() {
		assertNull(TicTacToeLogic.hintMove(b("... ... ...")))
		assertNull(TicTacToeLogic.hintTarget(tl, Direction.SOUTH, emptyList()))
	}
	@Test fun startOfPuzzleHintAfterBoardOpens() {

		for (ai in listOf(TttMark.X, TttMark.O)) {
			val board = MutableList(9) { TttMark.EMPTY }.also { it[4] = ai }
			assertEquals(ai.other, TicTacToeLogic.playerSide(board))
			val m = TicTacToeLogic.hintMove(board)
			assertEquals(TicTacToeLogic.suggestedMove(board, ai.other), m)
			assert(m in listOf(0, 2, 6, 8))
		}

		assertEquals(4, TicTacToeLogic.hintMove(b("X.. ... ...")))
	}
	@Test fun hintHiddenAfterPlayerMoveUntilBoardResponds() {

		val afterPlayer = b("O.. .X. ...")
		assertNull(TicTacToeLogic.hintMove(afterPlayer))
		assertNull(TicTacToeLogic.hintTarget(tl, Direction.SOUTH,
			listOf(TicTacToeLogic.cellPos(tl, Direction.SOUTH, 0) to TttMark.O, TicTacToeLogic.cellPos(tl, Direction.SOUTH, 4) to TttMark.X)))

		assertNull(TicTacToeLogic.hintMove(b("X.. .O. ...")))
	}
	@Test fun hintShownAfterBoardMove() {
		val afterBoard = b("O.. .X. ..X")
		val m = TicTacToeLogic.hintMove(afterBoard)
		assertNotNull(m)
		assertEquals(TicTacToeLogic.suggestedMove(afterBoard, TttMark.O), m)
		val frames = listOf(0 to TttMark.O, 4 to TttMark.X, 8 to TttMark.X).map { (i, k) -> TicTacToeLogic.cellPos(tl, Direction.EAST, i) to k }
		assertEquals(TicTacToeLogic.cellPos(tl, Direction.EAST, m!!), TicTacToeLogic.hintTarget(tl, Direction.EAST, frames))
	}
	@Test fun hintHiddenWhileNewMarkStillLoading() {

		val frames = listOf(TicTacToeLogic.cellPos(tl, Direction.SOUTH, 4) to TttMark.X, TicTacToeLogic.cellPos(tl, Direction.SOUTH, 0) to null)
		assertNull(TicTacToeLogic.hintTarget(tl, Direction.SOUTH, frames))
		val read = MutableList<TttMark?>(9) { TttMark.EMPTY }.also { it[4] = TttMark.X }
		assertNotNull(TicTacToeLogic.hintMove(read))
		read[0] = null
		assertNull(TicTacToeLogic.hintMove(read))
	}
	@Test fun fullGameHintOnlyOnPlayerTurns() {

		for (opening in listOf(4, 0, 2, 6, 8)) {
			val board = MutableList(9) { TttMark.EMPTY }
			assertNull(TicTacToeLogic.hintMove(board))
			board[opening] = TttMark.X
			while (true) {
				val h = TicTacToeLogic.hintMove(board) ?: break
				board[h] = TttMark.O
				assertNull(TicTacToeLogic.hintMove(board), "hint shown while AI reply pending: $board")
				val ai = TicTacToeLogic.bestMove(board, TttMark.X) ?: break
				board[ai] = TttMark.X
			}
			assert(TicTacToeLogic.winner(board) != TttMark.X) { "player lost following hints: $board" }
			assertNull(TicTacToeLogic.hintMove(board))
		}
	}
	@Test fun noHintForEveryEqualCountPosition() {
		val seen = HashSet<List<TttMark>>()
		fun walk(board: MutableList<TttMark>, turn: TttMark) {
			if (!seen.add(board.toList())) return
			val h = TicTacToeLogic.hintMove(board)
			val x = board.count { it == TttMark.X }; val o = board.count { it == TttMark.O }
			if (x == o || TicTacToeLogic.isFinished(board)) assertNull(h, board.toString())
			else assertEquals(TicTacToeLogic.suggestedMove(board, TicTacToeLogic.toMove(board)), h, board.toString())
			if (TicTacToeLogic.isFinished(board)) return
			for (i in 0..8) if (board[i] == TttMark.EMPTY) { board[i] = turn; walk(board, turn.other); board[i] = TttMark.EMPTY }
		}
		walk(MutableList(9) { TttMark.EMPTY }, TttMark.X)
		walk(MutableList(9) { TttMark.EMPTY }, TttMark.O)
	}
}
