// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch26Misc {
	val tabLists: List<List<String>> by lazy {
		val r = Random(2601)
		val pool = listOf("Puzzles: (3)", " Puzzles: (2) ", "Puzzles: (0)", "Puzzles: (99999999999)", "Puzzles: (1)", "Puzzles:(1)", "puzzles: (1)",
			"Creeper Beams: [✔] (Ashton)", " Three Weirdos: [✖] (Bob)", "Tic Tac Toe: [✦]", "???: [✦]", "Ice Fill: [✔]  ", "Water Board: [✔](x)", "Boulder: [?]",
			"Quiz: [✔] (Ashton) extra", "Teleport Maze: [✦] ", " Higher Or Lower: [✖]", "Bomb Defuse: [✔] (Ash_ton)", "  Two Spaces: [✔]", "Name-Dash: [✔]", "", "Deaths: 3")
		List(250) { List(r.nextInt(0, 9)) { pool[r.nextInt(pool.size)] } }
	}
	val bossLines = listOf("[BOSS] Maxor: hi", "[BOSS] The Watcher: Congratulations", "[BOSS] The Watcherx", "[BOSS]Maxor", "[BOSS] ", "[BOSS]", " [BOSS] Maxor", "[boss] Maxor", "")

	val slayerScripts: List<List<List<String?>>> by lazy {
		val r = Random(2602)
		val names = listOf("Revenant Horror IV", "Tarantula Broodfather III", null)
		val progress = listOf("Slay the boss!", "(120/1500) Combat XP", null, "Slay the boss! ")
		val chats = listOf("SLAYER QUEST STARTED!", "NICE! SLAYER BOSS SLAIN!", "SLAYER QUEST COMPLETE!", "SLAYER QUEST FAILED!", "Your Slayer Quest has been cancelled!", "Your Slayer Quest has been cancelled",
			"slayer quest started!", "random")
		List(150) { List(r.nextInt(3, 30)) {
			when (r.nextInt(3)) {
				0 -> listOf("sb", names[r.nextInt(3)], progress[r.nextInt(4)])
				1 -> listOf("chat", chats[r.nextInt(chats.size)])
				else -> listOf("q")
			}
		} }
	}
}
