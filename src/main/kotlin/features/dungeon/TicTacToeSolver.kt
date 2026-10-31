
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

enum class TttMark { X, O, EMPTY;
	val other get() = when (this) { X -> O; O -> X; EMPTY -> EMPTY }
}

object TicTacToeLogic {
	private val lines = listOf(
		intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
		intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
		intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
	)

	fun winner(b: List<TttMark>): TttMark? = lines.firstOrNull { (a, c, d) ->
		b[a] != TttMark.EMPTY && b[a] == b[c] && b[a] == b[d]
	}?.let { b[it[0]] }

	fun isFinished(b: List<TttMark>) = winner(b) != null || b.none { it == TttMark.EMPTY }

	fun toMove(b: List<TttMark>): TttMark {
		val x = b.count { it == TttMark.X }; val o = b.count { it == TttMark.O }
		return if (o < x) TttMark.O else TttMark.X
	}

	private fun minimax(b: MutableList<TttMark>, turn: TttMark, me: TttMark, depth: Int): Int {
		winner(b)?.let { return if (it == me) 10 - depth else depth - 10 }
		if (b.none { it == TttMark.EMPTY }) return 0
		var best = if (turn == me) Int.MIN_VALUE else Int.MAX_VALUE
		for (i in 0..8) {
			if (b[i] != TttMark.EMPTY) continue
			b[i] = turn
			val s = minimax(b, turn.other, me, depth + 1)
			b[i] = TttMark.EMPTY
			best = if (turn == me) maxOf(best, s) else minOf(best, s)
		}
		return best
	}

	private fun moveScore(b: MutableList<TttMark>, i: Int, player: TttMark): Int {
		b[i] = player
		val s = minimax(b, player.other, player, 1)
		b[i] = TttMark.EMPTY
		return s
	}

	fun bestMove(board: List<TttMark>, player: TttMark = toMove(board)): Int? {
		require(board.size == 9)
		if (isFinished(board)) return null
		val b = board.toMutableList()
		return listOf(4, 0, 2, 6, 8, 1, 3, 5, 7).filter { b[it] == TttMark.EMPTY }.maxByOrNull { i -> moveScore(b, i, player) }
	}

	const val OPENING_ROW = 1
	const val OPENING_COL = 2
	const val OPENING_CELL = OPENING_ROW * 3 + OPENING_COL

	private fun winsNow(b: List<TttMark>, who: TttMark) = (0..8).any { i ->
		b[i] == TttMark.EMPTY && winner(b.toMutableList().also { it[i] = who }) == who
	}

	fun openingMove(board: List<TttMark>, player: TttMark = toMove(board)): Int? {
		require(board.size == 9)
		if (isFinished(board)) return null
		if (board.any { it == player } || board[OPENING_CELL] != TttMark.EMPTY) return null
		if (winsNow(board, player) || winsNow(board, player.other)) return null
		val b = board.toMutableList()
		val best = (0..8).filter { b[it] == TttMark.EMPTY }.maxOf { moveScore(b, it, player) }
		return if (moveScore(b, OPENING_CELL, player) >= best) OPENING_CELL else null
	}

	fun suggestedMove(board: List<TttMark>, player: TttMark = toMove(board)): Int? =
		openingMove(board, player) ?: bestMove(board, player)

	fun markFromMapColors(colors: ByteArray): TttMark {
		if (colors.size < 128 * 128) return TttMark.EMPTY
		when (colors[64 * 128 + 64].toInt() and 0xFF) { 114 -> return TttMark.X; 33 -> return TttMark.O }
		var red = 0; var other = 0
		for (c in colors) {
			val v = c.toInt() and 0xFF
			if (v < 4) continue
			if (v / 4 == 28) red++ else if (v / 4 == 8) other++
		}
		return when {
			red > 200 && red >= other -> TttMark.X
			other > 200 -> TttMark.O
			else -> TttMark.EMPTY
		}
	}

	data class Cell<T>(val horizontal: Int, val y: Int, val mark: TttMark, val payload: T)

	fun <T> arrange(cells: List<Cell<T>>): Pair<List<TttMark>, List<T?>>? {
		if (cells.isEmpty() || cells.size > 9) return null
		val minH = cells.minOf { it.horizontal }; val maxY = cells.maxOf { it.y }
		val spanH = cells.maxOf { it.horizontal } - minH; val spanY = maxY - cells.minOf { it.y }
		if (spanH > 2 || spanY > 2) return null

		if (spanH != 2 || spanY != 2) return null
		val marks = MutableList(9) { TttMark.EMPTY }
		val payloads = MutableList<T?>(9) { null }
		for (c in cells) {
			val i = (maxY - c.y) * 3 + (c.horizontal - minH)
			if (payloads[i] != null) return null
			marks[i] = c.mark; payloads[i] = c.payload
		}
		return marks to payloads
	}

	fun horizontalOf(pos: BlockPos, facing: Direction): Int = when (facing) {
		Direction.NORTH -> -pos.x
		Direction.SOUTH -> pos.x
		Direction.EAST -> -pos.z
		Direction.WEST -> pos.z
		else -> pos.x
	}

	fun cellPos(topLeft: BlockPos, facing: Direction, index: Int): BlockPos =
		topLeft.below(index / 3).relative(facing.counterClockWise, index % 3)

	fun cellIndex(topLeft: BlockPos, facing: Direction, pos: BlockPos): Int? {
		val row = topLeft.y - pos.y
		val col = horizontalOf(pos, facing) - horizontalOf(topLeft, facing)
		return if (row in 0..2 && col in 0..2) row * 3 + col else null
	}

	fun boardAt(topLeft: BlockPos, facing: Direction, frames: List<Pair<BlockPos, TttMark>>): List<TttMark>? {
		val marks = MutableList(9) { TttMark.EMPTY }
		val seen = BooleanArray(9)
		for ((pos, mark) in frames) {
			val i = cellIndex(topLeft, facing, pos) ?: return null
			if (seen[i]) return null
			seen[i] = true; marks[i] = mark
		}
		return marks
	}

	fun targetFor(topLeft: BlockPos, facing: Direction, frames: List<Pair<BlockPos, TttMark>>): BlockPos? {
		val board = boardAt(topLeft, facing, frames) ?: return null
		return suggestedMove(board)?.let { cellPos(topLeft, facing, it) }
	}

	enum class Turn { PLAYER, BOARD, OVER, INVALID }

	fun turn(b: List<TttMark>): Turn {
		require(b.size == 9)
		if (isFinished(b)) return Turn.OVER
		val x = b.count { it == TttMark.X }; val o = b.count { it == TttMark.O }
		return when (x - o) {
			0 -> Turn.BOARD
			1, -1 -> Turn.PLAYER
			else -> Turn.INVALID
		}
	}

	fun playerSide(b: List<TttMark>): TttMark? =
		if (turn(b) == Turn.PLAYER) toMove(b) else null

	fun hintMove(board: List<TttMark?>): Int? {
		require(board.size == 9)
		if (board.any { it == null }) return null
		val b = board.map { it!! }
		val me = playerSide(b) ?: return null
		return suggestedMove(b, me)
	}

	fun hintTarget(topLeft: BlockPos, facing: Direction, frames: List<Pair<BlockPos, TttMark?>>): BlockPos? {
		val marks = MutableList<TttMark?>(9) { TttMark.EMPTY }
		val seen = BooleanArray(9)
		for ((pos, mark) in frames) {
			val i = cellIndex(topLeft, facing, pos) ?: return null
			if (seen[i]) return null
			seen[i] = true; marks[i] = mark
		}
		return hintMove(marks)?.let { cellPos(topLeft, facing, it) }
	}

	fun topLeftFromFrames(frames: List<BlockPos>, facing: Direction): BlockPos? {
		if (frames.isEmpty()) return null
		val hs = frames.map { horizontalOf(it, facing) }; val ys = frames.map { it.y }
		if (hs.max() - hs.min() != 2 || ys.max() - ys.min() != 2) return null
		val ref = frames[0]
		return ref.above(ys.max() - ref.y).relative(facing.clockWise, horizontalOf(ref, facing) - hs.min())
	}
}

object TicTacToeSolver {
	private val cfg get() = PuzzleSolvers.TConfig
	private var target: BlockPos? = null
	private var hidden = false
	private var keyWasDown = false

	private fun ironRun(level: net.minecraft.world.level.Level, from: BlockPos, dir: Direction): Int {
		var n = 0
		while (n < 3 && level.getBlockState(from.relative(dir, n + 1)).`is`(Blocks.IRON_BLOCK)) n++
		return n
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val down = cfg.ticTacToeKey.isPressed()
		if (down && !keyWasDown && Client.screen == null) hidden = !hidden
		keyWasDown = down
		if (event.tickCount % 5 != 0) return
		target = null
		if (!cfg.ticTacToe || hidden || !DungeonUtil.isInDungeonIsland) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		val frames = level.getEntitiesOfClass(ItemFrame::class.java, player.boundingBox.inflate(16.0)) {
			it.item.`is`(Items.FILLED_MAP) && it.direction.axis.isHorizontal
		}

		val groups = frames.groupBy { it.direction to (if (it.direction.axis == Direction.Axis.X) it.blockPosition().x else it.blockPosition().z) }
		for ((key, group) in groups) {
			val facing = key.first
			val frameMarks = group.map { f ->

				val colors = MapItem.getSavedData(f.item, level)?.colors
				f.blockPosition() to colors?.let(TicTacToeLogic::markFromMapColors)
			}
			val topLeft = locateTopLeft(level, facing, frameMarks.map { it.first }) ?: continue

			target = TicTacToeLogic.hintTarget(topLeft, facing, frameMarks)
			return
		}
	}

	private fun locateTopLeft(level: net.minecraft.world.level.Level, facing: Direction, frames: List<BlockPos>): BlockPos? {
		if (frames.isEmpty()) return null

		TicTacToeLogic.topLeftFromFrames(frames, facing)?.let { return it }
		val right = facing.counterClockWise
		val hs = frames.map { TicTacToeLogic.horizontalOf(it, facing) }
		val ys = frames.map { it.y }
		if (hs.max() - hs.min() > 2 || ys.max() - ys.min() > 2) return null
		val ref = frames[0]
		val backing = ref.relative(facing.opposite)
		val ironOk = level.getBlockState(backing).`is`(Blocks.IRON_BLOCK)
		val leftSteps = when {
			hs.max() - hs.min() == 2 -> TicTacToeLogic.horizontalOf(ref, facing) - hs.min()
			ironOk && ironRun(level, backing, right.opposite) + ironRun(level, backing, right) == 2 -> ironRun(level, backing, right.opposite)
			else -> return null
		}
		val upSteps = when {
			ys.max() - ys.min() == 2 -> ys.max() - ref.y
			ironOk && ironRun(level, backing, Direction.UP) + ironRun(level, backing, Direction.DOWN) == 2 -> ironRun(level, backing, Direction.UP)
			else -> return null
		}
		return ref.above(upSteps).relative(right.opposite, leftSteps)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		val t = target ?: return
		WorldDraw.drawInWorld(event) { block(t, cfg.ticTacToeColour.getEffectiveColourRGB()) }
	}
}
