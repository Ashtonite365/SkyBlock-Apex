// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch39Kit {
	val r = Random(3939)

	val splitLines = listOf(
		"[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!", "[NPC] Elle: OMG! Great work collecting my supplies!",
		"[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!",
		"Steve has been eaten by Kuudra!", "Elle has been eaten by Kuudra!", "ThisNameIsWayTooLong has been eaten by Kuudra!",
		"Alex destroyed one of Kuudra's pods!", "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!",
		"[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!",
		"[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.", "[BOSS] Bonzo: Oh I'm dead!", "x [BOSS] Bonzo: Oh I'm dead!",
		"[BOSS] Scarf: This is where the journey ends for you, Adventurers.", "[BOSS] Scarf: Did you forget? I was taught by the best! Let's dance.",
		"[BOSS] The Professor: I was burdened with terrible news recently...", "[BOSS] The Professor: Oh? You found my Guardians' one weakness?",
		"[BOSS] The Professor: What?! My Guardian power is unbeatable!",
		"[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", "[BOSS] Sadan: ENOUGH!",
		"[BOSS] Sadan: You did it. I understand now, you have earned my respect.",
		"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "[BOSS] Storm: Pathetic Maxor, just like expected.",
		"[BOSS] Goldor: Who dares trespass into my domain?", "The Core entrance is opening!",
		"[BOSS] Necron: You went further than any human before, congratulations.", "[BOSS] Wither King: You... again?", "", "random",
	)

	val floors: List<String?> = listOf(null, "", "E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M3", "M6", "M7", "F8", "F-1", "F", "7", "X7", "F07", "E1")

	data class Ev(val line: Int, val now: Long, val tick: Long, val rowsAt: Boolean)

	val scripts: List<List<Ev>> = buildList {
		repeat(150) {
			add(buildList {
				var now = r.nextLong(0, 100_000); var tick = r.nextLong(0, 1000)
				repeat(r.nextInt(3, 30)) {
					add(Ev(r.nextInt(splitLines.size), now, tick, r.nextInt(3) == 0))
					now += r.nextLong(0, 30_000); tick += r.nextLong(0, 600)
				}
			})
		}

		add((0 until 9).map { Ev(it, it * 1000L, it * 20L, true) })
		add((9..25).map { Ev(it, it * 1000L, it * 20L, true) })
	}
	val ticks = listOf(0L, 1L, 19L, 20L, 21L, -1L, -25L, 12345L, Long.MAX_VALUE, Long.MIN_VALUE, 3L, 7L)

	val summaryLines = listOf(
		"           ☠ Defeated Maxor, Storm, Goldor, and Necron in 05m 12s", "☠ Defeated The Watcher in 0 4m 2s (NEW RECORD!)", "☠ Defeated X in 1h",
		"  ☠ Defeated Bonzo in 01m 02s   ", "☠ Defeated Deaths: in 1m",
		"Team Score: 317 (S+)", "  Team Score: 300 (S) (NEW RECORD!)", "Team Score: 3 (ABC)", "+1,234.5 Catacombs Experience", "+12 Healer Experience (Team Bonus)",
		"+100 Bits", "+ 100 Bits", "Total Damage as Mage: 1,234,567", "Total Damage as Mage: 1,234 (NEW RECORD!)", "Ally Healing: 99.5",
		"Enemies Killed: 77", "Enemies Killed: 77 (NEW RECORD!)", "Deaths: 2", "Deaths: x", "Secrets Found: 31", "Secrets Found: 31 ", "",
		"▬▬▬▬▬▬▬▬", "  ▬▬ ", "▬▬x", "The Catacombs - Floor VII", "                             > EXTRA STATS <", "Something else",
	)
	val summaryScripts: List<List<Int>> = buildList {
		add(listOf(0, 22, 5, 8, 10, 12, 14, 15, 17, 19, 22))
		add(listOf(19, 0, 19, 19))
		add(listOf(4))
		repeat(120) { add((0 until r.nextInt(1, 25)).map { r.nextInt(summaryLines.size) }) }
	}

	data class SOp(val kind: Int, val y: Int, val z: Int, val n: Int)
	val simonScripts: List<List<SOp>> = buildList {
		repeat(200) {
			add(buildList {
				repeat(r.nextInt(5, 60)) {
					val k = r.nextInt(12)
					val kind = when { k < 4 -> 0; k < 7 -> 1; k < 10 -> 3; k < 11 -> 2; else -> 4 }
					add(SOp(kind, r.nextInt(119, 125), r.nextInt(91, 97), r.nextInt(0, 13)))
				}
			})
		}
	}

	val gridPositions: List<Triple<Int, Int, Int>> = buildList {
		for (x in listOf(-3, -2, -1, 109, 110, 111)) for (y in 118..126) for (z in 73..97 step 2) add(Triple(x, y, z))
		add(Triple(-2, Int.MAX_VALUE, 75)); add(Triple(-2, 120, Int.MIN_VALUE)); add(Triple(-2, 121, 74)); add(Triple(-2, 119, 76))
	}

	val rotationSets: List<List<Int>> = buildList {
		val sols = listOf(
			listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
			listOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
			listOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
			listOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
		)
		for (s in sols) {
			add(s)
			repeat(15) { add(s.map { if (it == -1) -1 else r.nextInt(0, 8) }) }
			add(s.mapIndexed { i, v -> if (i == 3 && v == -1) 0 else v })
		}
		add(List(25) { -1 }); add(List(25) { 0 }); add(List(24) { -1 }); add(List(5) { 1 }); add(emptyList())
		repeat(20) { add(List(25) { r.nextInt(-1, 8) }) }
	}

	data class AOp(val kind: Int, val idx: Int, val now: Long, val sneak: Boolean, val server: Int)
	val alignScripts: List<List<AOp>> = buildList {
		repeat(120) {
			add(buildList {
				var now = r.nextLong(-2000, 5000)
				repeat(r.nextInt(5, 50)) {
					add(AOp(r.nextInt(5), r.nextInt(-1, 26), now, r.nextInt(4) == 0, r.nextInt(rotationSets.size)))
					now += listOf(0L, 50L, 400L, 999L, 1000L, 1500L)[r.nextInt(6)]
				}
			})
		}
	}
}
