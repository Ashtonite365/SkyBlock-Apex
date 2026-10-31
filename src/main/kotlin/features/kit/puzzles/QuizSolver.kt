/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: clean-room Apex implementation of the dungeon Quiz (Oruo trivia) helper.
 * Display only, it never clicks: the right option is shown as an alert, optionally boxed on its answer pad, plus a
 * countdown HUD for the question windows (220 / 100 server ticks). The answer table is the bundled
 * assets/apex/kit/puzzles/quiz-answers.json (read-only resource, no network).
 * The pad box is pinned to the Quiz room the question was asked in (its frame and cells) and is only drawn while the
 * player stands in that room; it is forgotten on leaving the room, a wrong or final answer, a world change, or
 * [AnswerBox.TIMEOUT_MS] after the last question.
 */
package dev.ashtonite365.apex.features.kit.puzzles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.kit.ServerTickEvent

object QuizSolver {
	private const val ORUO = "[STATUE] Oruo the Omniscient: "
	private const val YEAR_QUESTION = "What SkyBlock year is it?"
	private const val OPTION_MARKS = "ⓐⓑⓒ"

	private const val CALENDAR_EPOCH = 1560276000L
	private const val YEAR_SECONDS = 446400L

	const val START_DURATION = 220
	const val QUESTION_DURATION = 100

	const val QUIZ_ROOM = "Quiz"

	val LOCAL_PADS = listOf(BlockPos(20, 70, 6), BlockPos(15, 70, 9), BlockPos(10, 70, 6))

	val answers: Map<String, List<String>> by lazy {
		val stream = QuizSolver::class.java.getResourceAsStream("/assets/apex/kit/puzzles/quiz-answers.json")
		if (stream == null) emptyMap() else parse(stream.reader().readText())
	}

	fun parse(text: String): Map<String, List<String>> {
		val root = Json.parseToJsonElement(text).jsonObject
		val out = LinkedHashMap<String, List<String>>()
		for ((question, list) in root) out[question] = list.jsonArray.map { it.jsonPrimitive.content }
		return out
	}

	fun skyblockYear(nowMillis: Long): Int = ((nowMillis / 1000 - CALENDAR_EPOCH) / YEAR_SECONDS).toInt() + 1

	fun answersFor(line: String, table: Map<String, List<String>>, nowMillis: Long): List<String>? {
		val question = line.trim()
		if (question == YEAR_QUESTION) return listOf("Year " + skyblockYear(nowMillis))
		for ((key, accepted) in table) if (question.contains(key)) return accepted
		return null
	}

	fun correctOption(line: String, answers: List<String>): Int? {
		val option = line.trim()
		if (option.isEmpty()) return null
		val slot = OPTION_MARKS.indexOf(option[0])
		if (slot == -1) return null
		return if (answers.any(option::endsWith)) slot else null
	}

	fun timerFor(line: String): Pair<Int, Int>? {
		val said = line.trim()
		if (!said.startsWith(ORUO)) return null
		return when (said.removePrefix(ORUO)) {
			"I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know." -> Pair(START_DURATION, 1)
			"2 questions left... Then you will have proven your worth to me!" -> Pair(QUESTION_DURATION, 2)
			"One more question!" -> Pair(QUESTION_DURATION, 3)
			else -> null
		}
	}

	fun timerText(ticks: Int, stage: Int): String {
		val secs = "%.1f".format(ticks / 20.0)
		val head = if (stage == 1) "Quiz starts" else "Question $stage"
		return "§e$head: §a${secs}s"
	}

	fun boxShown(quiz: Boolean, quizBox: Boolean): Boolean = if (!quiz) false else quizBox
	fun boxEnabled(): Boolean = boxShown(PuzzleSolvers.TConfig.quiz, PuzzleSolvers.TConfig.quizBox)

	fun isFailLine(line: String): Boolean = line.startsWith(ORUO) && "chose the wrong" in line

	private fun isCorrectLine(line: String) = line.startsWith(ORUO) && line.endsWith("correctly!")
	private fun isFinalLine(line: String) = line.startsWith(ORUO) && "answered the final question" in line

	data class RoomNow(val cells: Set<Pair<Int, Int>>?, val name: String?)

	class AnswerBox {
		var option: Int? = null
			private set
		var frame: DungeonRooms.RoomFrame? = null
			private set
		var cells: Set<Pair<Int, Int>>? = null
			private set

		private var stamp = 0L

		private var unknownSince: Long? = null

		val active: Boolean get() = option != null

		fun question(frame: DungeonRooms.RoomFrame?, cells: Set<Pair<Int, Int>>?, now: Long) {
			this.frame = frame
			this.cells = cells
			option = null
			unknownSince = null
			stamp = now
		}

		fun answer(idx: Int, now: Long) {
			if (frame != null && cells != null) {
				option = idx
				stamp = now
			}
		}

		fun clear() {
			option = null
			frame = null
			cells = null
			unknownSince = null
		}

		fun inQuizRoom(room: RoomNow): Boolean {
			val pinned = cells ?: return false
			val named = room.name
			if (named != null && named != QUIZ_ROOM) return false
			return room.cells?.equals(pinned) ?: false
		}

		fun update(room: RoomNow, now: Long) {
			if (option == null && cells == null) return
			when {
				now - stamp >= TIMEOUT_MS -> clear()
				inQuizRoom(room) -> unknownSince = null
				room.name != null || room.cells != null -> clear()
				else -> {
					val first = unknownSince ?: now
					unknownSince = first
					if (now - first >= UNKNOWN_GRACE_MS) clear()
				}
			}
		}

		fun boxPos(room: RoomNow, now: Long): BlockPos? {
			update(room, now)
			val pick = option ?: return null
			val pinnedFrame = frame ?: return null
			return if (inQuizRoom(room)) pinnedFrame.toReal(LOCAL_PADS[pick]).below() else null
		}

		companion object {
			const val TIMEOUT_MS = 60_000L
			const val UNKNOWN_GRACE_MS = 2_000L
		}
	}

	val box = AnswerBox()

	private var asked: List<String>? = null
	private var countdown = 0
	private var stage = 0

	private fun roomNow(): RoomNow {
		val identified = DungeonRoomTracker.currentRoom()
			?: return RoomNow(DungeonRoomTracker.currentCells()?.toSet(), null)
		return RoomNow(identified.cells.toSet(), identified.info.name)
	}

	private fun pinTarget(): Pair<DungeonRooms.RoomFrame, Set<Pair<Int, Int>>>? {
		val identified = DungeonRoomTracker.currentRoom()
		if (identified != null) {
			if (identified.info.name != QUIZ_ROOM) return null
			return DungeonRooms.frameOf(identified) to identified.cells.toSet()
		}
		val cells = DungeonRoomTracker.currentCells()?.toSet() ?: return null
		val fallback = DungeonRooms.roomFrameFor("Quiz box") ?: return null
		return fallback to cells
	}

	private fun forget() {
		asked = null
		box.clear()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		forget()
		countdown = 0
		stage = 0
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!box.active) return
		if (DungeonUtil.isInDungeonIsland) box.update(roomNow(), System.currentTimeMillis()) else box.clear()
	}

	@Subscribe
	fun onServerTick(event: ServerTickEvent) {
		if (countdown > 0) countdown -= 1
	}

	@Subscribe
	fun onTimerChat(event: ProcessChatEvent) {
		val cfg = PuzzleSolvers.TConfig
		if (!cfg.quizTimer && !boxEnabled()) return
		if (!DungeonUtil.isInDungeonIsland) return
		val line = event.bareText
		val started = timerFor(line)
		if (started != null) {
			countdown = started.first
			stage = started.second
		}
		if (!isCorrectLine(line)) return
		box.clear()
		if ("answered the final question" in line) {
			countdown = 0
			stage = 0
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {

		if (!PuzzleSolvers.TConfig.quiz || !DungeonUtil.isInDungeonIsland) return
		val line = event.bareText
		if (isFinalLine(line) || isFailLine(line)) return forget()
		val pending = asked
		val pick = pending?.let { correctOption(line, it) }
		if (pick != null) {
			box.answer(pick, System.currentTimeMillis())
			Alerts.show(Component.literal("§aQuiz: §e" + line.trim()))
			return
		}
		val accepted = answersFor(line, answers, System.currentTimeMillis()) ?: return
		asked = accepted
		val pin = pinTarget()
		box.question(pin?.first, pin?.second, System.currentTimeMillis())
	}

	@Subscribe
	fun onHudTimer(event: HudRenderEvent) {
		val cfg = PuzzleSolvers.TConfig
		if (!cfg.quizTimer || countdown <= 0 || !DungeonUtil.isInDungeonIsland) return
		event.drawHudLines(cfg.quizTimerPosition, listOf(Component.literal(timerText(countdown, stage))))
	}

	@Subscribe
	fun onWorldBox(event: WorldRenderLastEvent) {
		if (!box.active || !boxEnabled() || !DungeonUtil.isInDungeonIsland) return
		val at = box.boxPos(roomNow(), System.currentTimeMillis()) ?: return
		val rgb = PuzzleSolvers.TConfig.quizBoxColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) { box(AABB(at), rgb) }
	}
}
