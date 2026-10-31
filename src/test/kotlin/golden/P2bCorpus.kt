// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.dungeon.rooms.DungeonRooms
import dev.ashtonite365.apex.features.kit.boss.BossTickTimers
import dev.ashtonite365.apex.features.kit.boss.KingRelicHelper
import dev.ashtonite365.apex.features.kit.boss.SpiritBearTimer
import dev.ashtonite365.apex.features.kit.boss.TerracottaRespawnTimer
import dev.ashtonite365.apex.features.kit.boss.WitherDragonHelper
import dev.ashtonite365.apex.features.kit.devices.KitDevices
import dev.ashtonite365.apex.features.kit.dungeon.DoorLogic
import dev.ashtonite365.apex.features.kit.dungeon.KeyHighlight
import dev.ashtonite365.apex.features.kit.dungeon.LeapMenuOptions
import dev.ashtonite365.apex.features.kit.kuudra.KuudraKitLogic
import dev.ashtonite365.apex.features.kit.kuudra.KuudraKitPorts
import dev.ashtonite365.apex.features.kit.puzzles.QuizSolver
import dev.ashtonite365.apex.features.kit.render.RenderHides
import dev.ashtonite365.apex.util.data.ManagedConfig
import java.util.Base64
import kotlin.random.Random
import net.minecraft.core.BlockPos

object P2bCorpus {
	private val r = Random(0x2B2B)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]
	private fun chars(pool: String, max: Int) = (0 until r.nextInt(0, max + 1)).map { pool[r.nextInt(pool.length)] }.joinToString("")

	private val quizJson = QuizSolver::class.java.getResourceAsStream("/assets/apex/kit/puzzles/quiz-answers.json")!!.reader().readText()
	private val jsonVariants = listOf(quizJson, "{}", "{\"a\":[]}", "{\"Q?\":[\"x\",\"y\"],\"R\":[\"z\"]}", "{\"a\":\"b\"}", "[]", "", "{", "{\"a\":[1,true,null]}",
		"{\"a\":[[1]]}", "{\"dup\":[\"1\"],\"dup\":[\"2\"]}", "{\"a\":[\"x\"],\"ab\":[\"y\"]}")
	private val table = QuizSolver.parse(quizJson)
	private val questions = table.keys.toList()
	private val allAnswers = table.values.flatten().distinct()
	private val millis = listOf(0L, -1L, 1560276000_000L, 1560276000_999L, 1560276001_000L - 1, (1560276000L + 446400) * 1000, (1560276000L + 446400) * 1000 - 1,
		1_700_000_000_000L, Long.MAX_VALUE, Long.MIN_VALUE, 1_000L, 1560276000L * 1000 - 446400_000L * 3) + List(80) { r.nextLong(0, 4_000_000_000_000L) }
	private val questionLines = List(400) {
		when (r.nextInt(7)) {
			0 -> questions.pick()
			1 -> "     " + questions.pick() + "  "
			2 -> "What SkyBlock year is it?"
			3 -> listOf(" What SkyBlock year is it? ", "What SkyBlock year is it", "what skyblock year is it?", "Q: What SkyBlock year is it?").pick()
			4 -> "[STATUE] Oruo the Omniscient: " + questions.pick()
			5 -> questions.pick().drop(r.nextInt(1, 6))
			else -> chars("abc ?Wh", 20)
		}
	}
	private val marks = listOf("ⓐ", "ⓑ", "ⓒ", "ⓓ", "a", "(a)", "", " ")
	private val optionLines = List(500) {
		val ans = if (r.nextInt(4) == 0) chars("xyz ", 8) else allAnswers.pick()
		when (r.nextInt(5)) {
			0 -> "     ${marks.pick()} $ans"
			1 -> "${marks.pick()}$ans"
			2 -> "${marks.pick()} $ans!"
			3 -> " ${marks.pick()} $ans   "
			else -> marks.pick()
		}
	}
	private val answerSets = List(60) { List(r.nextInt(0, 4)) { if (r.nextInt(5) == 0) "" else allAnswers.pick() } } + listOf(listOf(""), emptyList())
	private const val ORUO = "[STATUE] Oruo the Omniscient: "
	private val statueLines = listOf(
		ORUO + "I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know.",
		ORUO + "2 questions left... Then you will have proven your worth to me!",
		ORUO + "One more question!",
		"  " + ORUO + "One more question!  ",
		ORUO + "One more question",
		"[STATUE] Oruo the Omniscient:One more question!",
		ORUO + "Ashtonite365 answered Question #1 correctly!",
		ORUO + "Ashtonite365 answered the final question correctly!",
		ORUO + "Ashtonite365 chose the wrong answer! I shall never forget this moment of misrememberance.",
		" " + ORUO + "x chose the wrong answer!",
		"Ashtonite365 chose the wrong answer!",
		ORUO + "chose the wrong",
		ORUO,
		"",
		"hello",
		ORUO + "I am Oruo the Omniscient.",
	)

	private val frames = listOf(DungeonRooms.RoomFrame(BlockPos(-136, 0, -104), 90), DungeonRooms.RoomFrame(BlockPos(-40, 0, -40), 0),
		DungeonRooms.RoomFrame(BlockPos(8, 0, 72), 180), DungeonRooms.RoomFrame(BlockPos(-200, 0, 24), 270), null)
	private val cellSets = listOf(setOf(2 to 3), setOf(5 to 5), setOf(2 to 3, 2 to 4), emptySet(), null)
	private val roomNames = listOf("Quiz", "Ice Path", null, "", "quiz")
	private fun room(b: QuizSolver.AnswerBox) =
		if (r.nextInt(5) < 3) QuizSolver.RoomNow(b.cells ?: cellSets.pick(), listOf("Quiz", null, "Quiz", "Ice Path").pick())
		else QuizSolver.RoomNow(cellSets.pick(), roomNames.pick())
	private fun pinFrame() = if (r.nextInt(5) == 0) frames.pick() else frames[r.nextInt(4)]
	private fun pinCells() = if (r.nextInt(5) == 0) cellSets.pick() else cellSets[r.nextInt(3)]

	private fun boxRun(len: Int): String {
		val b = QuizSolver.AnswerBox()
		var t = r.nextLong(0, 10_000)
		val log = StringBuilder()
		repeat(len) {
			t += if (r.nextInt(3) == 0) listOf(1_999L, 2_000L, 2_001L, 30_000L, 59_999L, 60_000L, 61_000L).pick() else listOf(0L, 1L, 100L, 999L, 1_000L).pick()
			val now = t
			val step = when (r.nextInt(9)) {
				0, 1 -> { b.question(pinFrame(), pinCells(), now); "q" }
				2, 3 -> { val i = if (r.nextInt(6) == 0) r.nextInt(-1, 5) else r.nextInt(0, 3); b.answer(i, now); "a$i" }
				4 -> { b.clear(); "c" }
				5 -> { val rm = room(b); b.update(rm, now); "u$rm" }
				6 -> { val rm = room(b); "i$rm=" + b.inQuizRoom(rm) }
				else -> { val rm = room(b); "p$rm=" + dev.ashtonite365.apex.test.golden.Golden.run { b.boxPos(rm, now).toString() } }
			}
			log.append(step).append('>').append(b.option).append(',').append(b.frame).append(',').append(b.cells).append(',').append(b.active).append("; ")
		}
		return log.toString()
	}

	private val keyNames = listOf("Wither Key", "Blood Key", " Wither Key ", "Wither Key!", "wither key", "Blood key", "", "Key", "§8Wither Key", "Blood Key\n", "\tBlood Key")
	private val slots = List(120) { DoorLogic.Slot(DoorLogic.Cell(r.nextInt(-2, 8), r.nextInt(-2, 8)), r.nextBoolean()) }

	private val ranks = listOf("", "[VIP] ", "[MVP+] ", "[MVP++] ", "[YOUTUBE] ", "[ADMIN]", "[] ")
	private val letters = "ABHMTXa"
	private val playerNames = listOf("Bonzi", "151_Dragon", "Lennart0312", "a", "ab", "Ashtonite365", "x".repeat(17), "Name With Space", "DEAD", "_x_")
	private fun scoreLine() = when (r.nextInt(6)) {
		0 -> "[${letters.random(r)}] ${ranks.pick()}${playerNames.pick()} ${r.nextInt(0, 30000)}❤"
		1 -> " [${letters.random(r)}] ${playerNames.pick()} DEAD"
		2 -> "[${letters.random(r)}]${playerNames.pick()}"
		3 -> "Time Elapsed: ${r.nextInt(0, 9)}m"
		4 -> "  [${letters.random(r)}] ${ranks.pick()}${playerNames.pick()}  "
		else -> chars("[]ABM xyz_0", 14)
	}
	private val boards = List(200) { List(r.nextInt(0, 7)) { scoreLine() } }
	private val headDisplays = List(150) { listOf("${ranks.pick()}${playerNames.pick()}", playerNames.pick(), " ${playerNames.pick()} ", chars("[]+ ab_9", 12), "").pick() }
	private val quadrantInputs = List(300) {
		val w = listOf(0, 1, 2, 99, 100, 101, 1920, -4).pick(); val h = listOf(0, 1, 3, 100, 1080, -2).pick()
		val x = listOf(0.0, w / 2.0, w / 2.0 - 1e-9, w.toDouble(), -1.0, Double.NaN, r.nextDouble(-10.0, 2000.0)).pick()
		val y = listOf(0.0, h / 2.0, h / 2.0 - 1e-9, h.toDouble(), -1.0, Double.NaN, r.nextDouble(-10.0, 1200.0)).pick()
		listOf(x, y, w.toDouble(), h.toDouble())
	}

	private val supplyLines = List(300) {
		val n = listOf("Ashton", "a", "x".repeat(16), "x".repeat(17), "Name_1", "", "Two Words").pick()
		when (r.nextInt(6)) {
			0 -> "${ranks.pick()}$n recovered one of Elle's supplies! (${r.nextInt(0, 12)}/${r.nextInt(0, 12)})"
			1 -> "$n recovered one of Elle's supplies! (${r.nextInt(0, 10)}/6)"
			2 -> "  [MVP+] $n recovered one of Elle's supplies! (3/6) extra"
			3 -> "$n recovered one of Elle's supplies! (10/6)"
			4 -> "$n recovered one of Elle's supplies!"
			else -> chars("recovered Elle's ()/0123 ", 30)
		}
	}
	private val buildNames = List(200) {
		when (r.nextInt(4)) {
			0 -> "Building Progress ${r.nextInt(0, 140)}% (${r.nextInt(0, 6)} Players Helping)"
			1 -> "§aBuilding Progress ${r.nextInt(0, 100)}% (${r.nextInt(0, 6)} Players Helping) x"
			2 -> listOf("Building Progress % (1 Players Helping)", "Building Progress 5% (1 Player Helping)", "Building Progress 99999999999% (1 Players Helping)", "PROGRESS: 20%", "PROGRESS: %", "progress: 5%").pick()
			else -> chars("Building Progress%() 0123", 30)
		}
	}
	private val manaLines = listOf("Used Extreme Focus! (1200 Mana)", " Used Extreme Focus! (5 Mana) ", "Used Extreme Focus! (Mana)", "Used Extreme Focus! (99999999999 Mana)",
		"Used Extreme Focus! (12 Mana)!", "xUsed Extreme Focus! (1 Mana)", "", "Used Extreme Focus! (0 Mana)")
	private val perkNames = listOf("Steady Hands", "Steady Hands II", "Bomberman", "Mining Frenzy III", "Elle's Lava Rod", "Elle's Lava Rod Upgrade", "Elle's Pickaxe", "Auto Revive",
		"Auto Revive ", "Human Cannonball", "Human Cannonball II", "Ballista Mechanic", "", "steady hands", "Elle's Pickaxe I")
	private val yaws = listOf(0f, -130f, 50f, 90f, -90f, 180f, 360f, 1e6f, Float.NaN, -0.0f) + List(60) { r.nextFloat() * 720f - 360f }
	private val coords = listOf(0.0, -0.5, 0.5, -101.3, 100.9, -1e9, 3.7) + List(20) { r.nextDouble(-200.0, 200.0) }

	private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
	private val hashes = listOf(RenderHides.Logic.TENTACLE, RenderHides.Logic.HEALER_FAIRY, RenderHides.Logic.SOUL_WEAVER, "abc123", "ABC", "")
	private val skinValues = buildList {
		for (h in hashes) {
			add(b64("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$h\"}}}"))
			add(b64("{\"timestamp\":1,\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/$h?x\"}}}"))
			add(b64("{\"textures\":{\"SKIN\":{\"url\":[\"http://textures.minecraft.net/texture/$h\"]}}}"))
		}
		addAll(listOf("", "not base64!", b64(""), b64("{}"), b64("[]"), b64("{\"textures\":null}"), b64("{\"textures\":{}}"), b64("{\"textures\":{\"SKIN\":{}}}"),
			b64("{\"textures\":{\"SKIN\":{\"url\":5}}}"), b64("{\"textures\":{\"SKIN\":{\"url\":null}}}"), b64("{\"textures\":{\"SKIN\":{\"url\":{}}}}"),
			b64("{\"textures\":\"x\"}"), b64("{\"textures\":{\"SKIN\":\"x\"}}"), b64("textures.minecraft.net/texture/abc"), b64("{\"textures\":{\"SKIN\":{\"url\":\"textures.minecraft.net/texture/\"}}}"),
			b64("{textures:{SKIN:{url:'textures.minecraft.net/texture/beef'}}}"), "e30", "e30=", "e30==="))
	}

	private fun configShape(vararg cfgs: ManagedConfig): String {
		P2Opts.load(*cfgs)
		return cfgs.joinToString(" / ") { c -> c.name + ":" + c.sortedOptions.joinToString(",") { "${it.propertyName}=${it._actualValue}" } }
	}

	fun cases(): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>()
		fun add(k: String, f: () -> Any?) { out += k to Golden.run { f().toString() } }

		for ((i, j) in jsonVariants.withIndex()) add("qparse|$i") { QuizSolver.parse(j).entries.joinToString("\n") { "${it.key}=${it.value}" } }
		add("qanswers") { QuizSolver.answers == table }
		for ((i, m) in millis.withIndex()) add("year|$i") { QuizSolver.skyblockYear(m) }
		val tables = listOf(table, emptyMap(), mapOf("a" to listOf("x"), "ab" to listOf("y")), mapOf("" to listOf("empty")))
		for ((i, q) in questionLines.withIndex()) add("qfor|$i") { QuizSolver.answersFor(q, tables[i % tables.size], millis.pick()) }
		for (i in 0 until 400) {
			val set = table.values.toList().pick()
			val line = "${listOf("     ", "", " ", "\t").pick()}${marks.pick()} ${set.pick()}${listOf("", "", " ", "!", "s").pick()}"
			add("qhit|$i") { QuizSolver.correctOption(line, set) }
		}
		for ((i, o) in optionLines.withIndex()) {
			val set = if (i % 3 == 0) answerSets.pick() else table.values.toList().pick()
			add("qopt|$i") { QuizSolver.correctOption(o, set) }
		}
		for ((i, l) in (statueLines + questionLines.take(30)).withIndex()) add("qline|$i") { "${QuizSolver.timerFor(l)} ${QuizSolver.isFailLine(l)}" }
		for (t in listOf(-21, -1, 0, 1, 19, 20, 99, 100, 101, 219, 220, 2001, Int.MAX_VALUE, Int.MIN_VALUE)) for (s in listOf(0, 1, 2, 3, 7, -1)) add("qtext|$t|$s") { QuizSolver.timerText(t, s) }
		for (a in listOf(false, true)) for (b in listOf(false, true)) add("qshown|$a|$b") { QuizSolver.boxShown(a, b) }
		add("qconst") { listOf(QuizSolver.START_DURATION, QuizSolver.QUESTION_DURATION, QuizSolver.LOCAL_PADS, QuizSolver.QUIZ_ROOM,
			QuizSolver.AnswerBox.TIMEOUT_MS, QuizSolver.AnswerBox.UNKNOWN_GRACE_MS, QuizSolver.RoomNow(setOf(1 to 2), "Quiz")) }
		for (i in 0 until 400) add("qbox|$i") { boxRun(r.nextInt(1, 25)) }

		for ((i, n) in keyNames.withIndex()) add("key|$i") { KeyHighlight.keyOf(n)?.let { "${it.name} ${it.label} ${it.code}" } }
		add("keys") { KeyHighlight.Key.entries.map { "${it.name}/${it.label}/${it.code}" } }
		for ((i, s) in slots.withIndex()) add("doorbox|$i") { KeyHighlight.doorBox(s) }

		for ((i, b) in boards.withIndex()) add("leapcls|$i") { LeapMenuOptions.classesFromScoreboard(b).entries.joinToString() }
		for ((i, d) in headDisplays.withIndex()) add("leaphead|$i") { LeapMenuOptions.headName(d) }
		for ((i, q) in quadrantInputs.withIndex()) add("leapq|$i") { LeapMenuOptions.quadrantOf(q[0], q[1], q[2].toInt(), q[3].toInt()) }
		for (i in 0 until 200) {
			val names = List(r.nextInt(0, 6)) { if (r.nextInt(6) == 0) null else listOf(playerNames.pick(), playerNames.pick().uppercase()).pick() }
			val board = LeapMenuOptions.classesFromScoreboard(boards.pick()) + if (r.nextBoolean()) mapOf(playerNames.pick().lowercase() to "ABHMT".random(r)) else emptyMap()
			val letter = "ABHMTZ".random(r)
			add("leapidx|$i") { LeapMenuOptions.indexOfClass(names, board, letter) }
		}
		add("leapconst") { "${LeapMenuOptions.CONTAINER_LEFT_CLICK} ${LeapMenuOptions.CLASSES}" }
		for ((c, s) in listOf(0 to 0, 7 to 13, -1 to 54, 255 to -999)) add("leapclick|$c|$s") {
			val sent = ArrayList<Any>()
			LeapMenuOptions.clickLeapSlot(c, s) { a, b, d, e -> sent.addAll(listOf(a, b, d, e)) }
			sent
		}

		for ((i, l) in supplyLines.withIndex()) add("supply|$i") { KuudraKitLogic.supplyPickup(l) }
		for ((i, l) in buildNames.withIndex()) add("build|$i") { "${KuudraKitLogic.buildProgress(l)} ${KuudraKitLogic.PILE.containsMatchIn(l)} ${KuudraKitLogic.PILE.find(l)?.value}" }
		for ((i, l) in (manaLines + supplyLines.take(10)).withIndex()) add("mana|$i") { KuudraKitLogic.manaDrain(l) }
		for (p in perkNames) for (s in listOf(false, true)) add("perk|$p|$s") { KuudraKitLogic.uselessPerk(p, s) }
		for (i in 0 until 400) {
			val x = coords.pick(); val z = coords.pick(); val y = yaws.pick()
			add("giant|$i") { KuudraKitLogic.giantPickup(x, z, y) }
		}
		for (b in -3..130) add("colour|$b") { "${KuudraKitLogic.colourBuild(b)} ${KuudraKitLogic.colourBuilders(b)}" }
		add("spots") { "${KuudraKitLogic.dropSpots} ${KuudraKitLogic.pickupSpots}" }
		add("counters") { "${KuudraKitPorts.runs} ${KuudraKitPorts.paidChests} ${KuudraKitPorts.freeChests}" }

		for ((i, v) in skinValues.withIndex()) add("skin|$i") { RenderHides.Logic.skinHash(v) }
		add("skinconst") { listOf(RenderHides.Logic.TENTACLE, RenderHides.Logic.HEALER_FAIRY, RenderHides.Logic.SOUL_WEAVER) }
		for (h in hashes + listOf(null)) for (mask in 0 until 16) add("head|$h|$mask") {
			RenderHides.Logic.hiddenHead(h, mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0)
		}

		add("p3") { KitDevices.inP3() }
		add("configs") {
			configShape(BossTickTimers.TConfig, SpiritBearTimer.TConfig, TerracottaRespawnTimer.TConfig, WitherDragonHelper.TConfig, KingRelicHelper.TConfig,
				KitDevices.TConfig, RenderHides.TConfig)
		}
		add("bossstate") { listOf(BossTickTimers.state.goldorTick, SpiritBearTimer.state.display(false), TerracottaRespawnTimer.state.spawning, WitherDragonHelper.state.priority) }
		return out
	}
}
