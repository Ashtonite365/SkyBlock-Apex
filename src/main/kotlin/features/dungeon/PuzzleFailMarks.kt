/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: remembers which dungeon puzzle rooms FAILED so DungeonMap can draw its FAILED room state (a red X)
 * on them until the run ends. Visual only: reads the map item, the tab list and chat; never clicks or sends anything.
 *
 * Signals (most reliable first):
 *  1. Hypixel's own map: a failed puzzle room carries a red (map colour 18) cross where cleared rooms carry a
 * white/green tick. DungeonMap.roomTick reads it as Tick.FAILED (the DungeonMap draws CROSS for that colour).
 *     It names the exact room, so it is what places the X. We latch it so the X stays until the run ends.
 *  2. Tab list "Puzzles: (N)" entries " Name: [✖]" (parsed by PuzzleHud.parse). Names the puzzle, not the room:
 *     it is bound to a room through the cell where the failing player stood (from a matching chat line) or the
 *     one new map cross that appeared.
 *  3. Chat "PUZZLE FAIL! <player> ..." / Oruo's quiz fail (PlusDungeonLogic.puzzleFailer): gives the player, and for
 *     a few messages the puzzle. The player's map marker cell (if it is a puzzle-coloured room) is the room.
 * Cleared per room (hotfix3) when the failed puzzle is reset with Architect's First Draft: the chat line saying so
 * ([parseReset], tolerant: exact wording not in any log yet) or the tab entry going from [✖] back to unsolved.
 * Only puzzle rooms are cleared; a reset elsewhere does nothing.
 * Reset on the run start line (Mort), the end-of-run "> EXTRA STATS <" line and on world change.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapDecoration
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.kit.dungeon.RoomClear
import dev.ashtonite365.apex.features.kit.puzzles.PuzzleHud
import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonLogic
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

private fun norm(s: String) = s.lowercase().filter { it.isLetter() }

object PuzzleFailMarks {

	enum class Puzzle(vararg aliases: String) {
		BLAZE("Higher Or Lower", "Blaze", "Higher Blaze", "Lower Blaze"),
		THREE_WEIRDOS("Three Weirdos", "Weirdos"),
		TIC_TAC_TOE("Tic Tac Toe", "TicTacToe"),
		WATER_BOARD("Water Board", "Waterboard", "Water"),
		ICE_PATH("Ice Path", "Silverfish"),
		ICE_FILL("Ice Fill"),
		CREEPER_BEAMS("Creeper Beams", "Creeper"),
		BOULDER("Boulder"),
		TELEPORT_MAZE("Teleport Maze", "Teleport"),
		QUIZ("Quiz", "Trivia"),
		BOMB_DEFUSE("Bomb Defuse"),
		;

		val keys = aliases.map { norm(it) }.toSet()
	}

	fun canonical(name: String): Puzzle? {
		val n = norm(name)
		if (n.isEmpty()) return null
		return Puzzle.entries.firstOrNull { n in it.keys }
	}

	fun failedFromTab(lines: List<String>): Set<Puzzle> =
		PuzzleHud.parse(lines)?.entries.orEmpty()
			.filter { it.status == PuzzleHud.Status.FAILED }
			.mapNotNull { canonical(it.name) }.toSet()

	data class ChatFail(val player: String, val puzzle: Puzzle?)

	private val chatPuzzles = listOf(
		Regex("^PUZZLE FAIL! \\S+ killed a Blaze in the wrong order!.*") to Puzzle.BLAZE,
		Regex("^PUZZLE FAIL! \\S+ was fooled by .*") to Puzzle.THREE_WEIRDOS,
		Regex("^PUZZLE FAIL! \\S+ lost Tic Tac Toe!.*") to Puzzle.TIC_TAC_TOE,
		Regex("^\\[STATUE] Oruo the Omniscient: \\S+ chose the wrong .*") to Puzzle.QUIZ,
	)

	fun parseChat(line: String): ChatFail? {
		val who = PlusDungeonLogic.puzzleFailer(line) ?: return null
		return ChatFail(who, chatPuzzles.firstOrNull { it.first.matches(line) }?.second)
	}

	data class ChatReset(val puzzle: Puzzle?)

	fun parseReset(line: String): ChatReset? {
		val l = line.lowercase()
		if (l.contains("puzzle fail")) return null
		val draft = l.contains("architect's first draft") || l.contains("architects first draft")
		val hit = (draft && (l.contains("reset") || l.contains("used"))) || l.contains("puzzle reset") ||
			Regex("puzzle\\b.*\\breset").containsMatchIn(l)
		if (!hit) return null
		val named = Puzzle.entries.firstOrNull { p -> p.keys.any { k -> k.length >= 5 && norm(line).contains(k) } }
		return ChatReset(named)
	}

	fun isRunBoundary(line: String): Boolean =
		line.startsWith("[NPC] Mort: Here, I found this map") || line.contains("> EXTRA STATS <")

	const val PUZZLE_ROOM = 66

	fun isPuzzleCell(colors: ByteArray, grid: DungeonMap.Grid, cell: Pair<Int, Int>): Boolean {
		val x = grid.x + cell.first * grid.step + 1; val y = grid.y + cell.second * grid.step + 1
		if (x !in 0 until DungeonMap.SIZE || y !in 0 until DungeonMap.SIZE) return false
		return (colors[x + y * DungeonMap.SIZE].toInt() and 0xFF) == PUZZLE_ROOM
	}

	class State {

		val failedCells = linkedSetOf<Pair<Int, Int>>()
		val byPuzzle = mutableMapOf<Puzzle, Pair<Int, Int>>()

		val pending = linkedSetOf<Puzzle>()
		private val seenTab = mutableSetOf<Puzzle>()
		private var chatCell: Pair<Int, Int>? = null
		private var chatPuzzle: Puzzle? = null
		private var chatAt = Long.MIN_VALUE

		private val suppressed = mutableSetOf<Pair<Int, Int>>()

		private var lastTabFailed = emptySet<Puzzle>()

		var version = 0
			private set

		fun reset() {
			if (failedCells.isEmpty() && pending.isEmpty() && byPuzzle.isEmpty() && seenTab.isEmpty() && chatCell == null) return
			failedCells.clear(); byPuzzle.clear(); pending.clear(); seenTab.clear(); chatCell = null; chatPuzzle = null
			suppressed.clear(); lastTabFailed = emptySet()
			chatAt = Long.MIN_VALUE; version++
		}

		private fun place(puzzle: Puzzle?, cell: Pair<Int, Int>) {
			val added = failedCells.add(cell)
			var bound = false
			if (puzzle != null && byPuzzle[puzzle] == null && cell !in byPuzzle.values) { byPuzzle[puzzle] = cell; pending.remove(puzzle); bound = true }
			if (added || bound) version++
		}

		fun onMapCrosses(cells: Collection<Pair<Int, Int>>) {
			suppressed.retainAll(cells.toSet())
			val live = cells.filter { it !in suppressed }
			val fresh = live.filter { it !in failedCells }
			for (c in live) place(null, c)
			val unbound = failedCells.filter { it !in byPuzzle.values }
			if (pending.size == 1 && (fresh.size == 1 || unbound.size == 1)) place(pending.first(), fresh.singleOrNull() ?: unbound.single())
		}

		fun onChatFail(puzzle: Puzzle?, cell: Pair<Int, Int>?, now: Long) {
			chatAt = now; chatCell = cell; chatPuzzle = puzzle
			if (cell != null) place(puzzle, cell)
			else if (puzzle != null && byPuzzle[puzzle] == null && pending.add(puzzle)) version++
		}

		fun onTabFailed(failed: Set<Puzzle>, now: Long, isPuzzleRoom: (Pair<Int, Int>) -> Boolean = { true }) {

			val back = lastTabFailed - failed
			lastTabFailed = failed
			for (p in back) onReset(p, null, isPuzzleRoom)
			for (p in failed - seenTab) {
				seenTab += p
				if (byPuzzle[p] != null) continue
				val cell = chatCell
				if (cell != null && now - chatAt <= CHAT_WINDOW_MS && (chatPuzzle == null || chatPuzzle == p)) place(p, cell)
				else if (pending.add(p)) version++
			}
		}

		fun onReset(puzzle: Puzzle?, here: Pair<Int, Int>?, isPuzzleRoom: (Pair<Int, Int>) -> Boolean): Boolean {
			val cell = puzzle?.let { byPuzzle[it] } ?: here
			if (cell == null || !isPuzzleRoom(cell)) return false

			fun forget(p: Puzzle) { pending.remove(p); if (p !in lastTabFailed) seenTab.remove(p) }
			puzzle?.let(::forget)
			val removed = failedCells.remove(cell)
			byPuzzle.entries.filter { it.value == cell }.map { it.key }.forEach { forget(it); byPuzzle.remove(it) }
			if (chatCell == cell) chatCell = null
			suppressed += cell
			if (removed) version++
			return removed
		}
	}

	const val CHAT_WINDOW_MS = 10_000L

	val state = State()

	fun cells(): Set<Pair<Int, Int>> = if (DungeonMap.TConfig.puzzleFailX) state.failedCells else emptySet()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { state.reset() }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText
		if (isRunBoundary(line)) { state.reset(); return }
		if (!DungeonMap.TConfig.puzzleFailX || !DungeonUtil.isInDungeonIsland) return
		parseReset(line)?.let { r ->
			val read = mapData()
			state.onReset(r.puzzle, ownCell()) { c -> read != null && isPuzzleCell(read.first.colors, read.second, c) }
			return
		}
		val fail = parseChat(line) ?: return
		state.onChatFail(fail.puzzle, failerCell(fail.player), System.currentTimeMillis())
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!DungeonMap.TConfig.puzzleFailX || !DungeonUtil.isInActiveDungeon) return
		if (event.tickCount % 5 != 0) return
		val (data, grid) = mapData() ?: return
		state.onMapCrosses(DungeonMap.roomTicks(data.colors, grid).filterValues { it == DungeonMap.Tick.FAILED }.keys)
		if (event.tickCount % 20 == 0)
			state.onTabFailed(failedFromTab(TabListReader.currentSnapshot.body.map { it.bareText }), System.currentTimeMillis()) {
				isPuzzleCell(data.colors, grid, it)
			}
	}

	private class MapRead(val colors: ByteArray, val decorations: List<MapDecoration>)

	private fun mapData(): Pair<MapRead, DungeonMap.Grid>? {
		val player = Client.player ?: return null
		val level = Client.level ?: return null
		val stack = player.inventory.getItem(8)
		if (!stack.`is`(Items.FILLED_MAP)) return null
		val data = MapItem.getSavedData(stack, level) ?: return null
		val grid = DungeonMap.grid ?: DungeonMap.detectGrid(data.colors) ?: return null
		return MapRead(data.colors, data.decorations.toList()) to grid
	}

	private fun ownCell(): Pair<Int, Int>? {
		val (data, grid) = mapData() ?: return null
		val self = data.decorations.firstOrNull { it.type().value() == MapDecorationTypes.FRAME.value() } ?: return null
		return RoomClear.cellOf(self.x().toInt(), self.y().toInt(), grid)
	}

	private fun failerCell(name: String): Pair<Int, Int>? {
		val player = Client.player ?: return null
		val (data, grid) = mapData() ?: return null
		val self = data.decorations.firstOrNull { it.type().value() == MapDecorationTypes.FRAME.value() } ?: return null
		val marker = if (name == player.gameProfile.name) self
		else DungeonMap.matchHeads(player, self, data.decorations).entries.firstOrNull { it.value.gameProfile.name == name }?.key
		marker ?: return null
		val cell = RoomClear.cellOf(marker.x().toInt(), marker.y().toInt(), grid) ?: return null
		return cell.takeIf { isPuzzleCell(data.colors, grid, it) }
	}
}
