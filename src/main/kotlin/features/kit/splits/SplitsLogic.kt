// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.splits

data class SplitDef(val regex: Regex, val name: String)
data class SplitRow(val name: String, val time: Long, val ticks: Long, val isCurrent: Boolean)

class SplitRun(val defs: List<SplitDef>) {
	private val hitAt = LongArray(defs.size)
	private val hitTick = LongArray(defs.size) { -1L }

	private fun reached(i: Int) = hitTick[i] >= 0

	val started get() = reached(0)
	val finished get() = reached(defs.lastIndex)

	fun onMessage(msg: String, now: Long, tick: Long): Int {
		var hit = -1
		for (i in defs.indices) if (defs[i].regex.matches(msg)) { hit = i; break }
		if (hit < 0) return -1
		if (reached(hit)) return -1
		if (hit > 0 && !started) return -1
		hitAt[hit] = now
		hitTick[hit] = tick
		return hit
	}

	fun rows(now: Long, tick: Long): List<SplitRow> {
		if (!started) return emptyList()
		val endIdx = defs.lastIndex
		val result = ArrayList<SplitRow>()
		var lastReached = -1
		var i = 0
		while (i <= endIdx) {
			if (!reached(i)) { i++; continue }
			lastReached = i
			if (i == endIdx) break
			var j = i + 1
			while (j <= endIdx && !reached(j)) j++
			result.add(
				if (j <= endIdx) SplitRow(defs[i].name, hitAt[j] - hitAt[i], hitTick[j] - hitTick[i], false)
				else SplitRow(defs[i].name, now - hitAt[i], tick - hitTick[i], true)
			)
			i = j
		}
		for (k in lastReached + 1 until endIdx) result.add(SplitRow(defs[k].name, 0, 0, false))
		val stopTime = if (finished) hitAt[endIdx] else now
		val stopTick = if (finished) hitTick[endIdx] else tick
		result.add(SplitRow("Total", stopTime - hitAt[0], stopTick - hitTick[0], false))
		return result
	}

	companion object {

		fun formatTicks(ticks: Long) = "%.2f".format(ticks / 20.0)
	}
}

object KitSplitDefs {
	private fun def(pattern: String, name: String) = SplitDef(Regex(pattern), name)

	val kuudra = listOf(
		def("^\\[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!$", "§2Supplies"),
		def("^\\[NPC] Elle: OMG! Great work collecting my supplies!$", "§bBuild"),
		def("^\\[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!$", "§dEaten"),
		def("^(?!Elle has been eaten by Kuudra!$)(.{1,16}) has been eaten by Kuudra!$", "§cStun"),
		def("^(.{1,16}) destroyed one of Kuudra's pods!$", "§4DPS"),
		def("^\\[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!$", "§4Cleared"),
		def("^\\[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!$", "Total"),
	)

	val bossPhases: List<List<SplitDef>> = listOf(
		emptyList(),
		listOf(
			def("^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$", "§cBonzo's Sike"),
			def("\\[BOSS] Bonzo: Oh I'm dead!", "§4Cleared"),
		),
		listOf(
			def("^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$", "§cScarf's minions"),
			def("^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$", "§4Cleared"),
		),
		listOf(
			def("^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$", "§cThe Guardians"),
			def("^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$", "§aThe Professor"),
			def("^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$", "§4Cleared"),
		),
		emptyList(),
		emptyList(),
		listOf(
			def("^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$", "§cTerracottas"),
			def("^\\[BOSS] Sadan: ENOUGH!$", "§aGiants"),
			def("^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$", "§4Cleared"),
		),
		listOf(
			def("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$", "§5Maxor"),
			def("\\[BOSS] Storm: Pathetic Maxor, just like expected\\.", "§3Storm"),
			def("\\[BOSS] Goldor: Who dares trespass into my domain\\?", "§6Terminals"),
			def("The Core entrance is opening!", "§7Goldor"),
			def("\\[BOSS] Necron: You went further than any human before, congratulations\\.", "§cNecron"),
			def("^\\[BOSS] Wither King: You\\.\\.\\. again\\?$", "§4Cleared"),
		),
	)

	val dungeonCleared = Regex("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$")

	fun floorNumber(floor: String?): Int? {
		if (floor == null) return null
		if (floor == "E") return 0
		return (if (floor.isEmpty()) floor else floor.substring(1)).toIntOrNull()
	}

	fun bossRun(floor: String?): SplitRun? {
		val n = floorNumber(floor) ?: return null
		if (n < 0 || n >= bossPhases.size) return null
		val phases = bossPhases[n]
		if (phases.size <= 1) return null
		val defs = ArrayList(phases.subList(0, phases.size - 1))
		defs.add(SplitDef(phases[phases.size - 1].regex, "end"))
		return SplitRun(defs)
	}
}

class RunSummary {
	private val collected = ArrayList<String>()
	var collecting = false
		private set

	fun feed(raw: String): Boolean {
		val line = raw.trim()
		if (DEFEATED.matches(raw)) {
			collecting = true
			collected.clear()
			collected.add("§6$line")
			return true
		}
		if (!collecting) return false
		for ((re, colour) in STATS) {
			if (re.matches(line)) {
				collected.add(colour + line)
				return true
			}
		}
		return line.all { it == '▬' }
	}

	fun take(): List<String>? {
		if (!collecting) return null
		if (collected.none { "Secrets Found" in it || "Deaths:" in it }) return null
		collecting = false
		return ArrayList(collected)
	}

	companion object {
		private val DEFEATED = KitSplitDefs.dungeonCleared
		private val STATS = listOf(
			Regex("^Team Score: (\\d+) \\((.{1,2})\\)\\s?(\\(NEW RECORD!\\))?$") to "§e",
			Regex("^\\+[\\d,.]+\\s?\\w+ Experience\\s?(?:\\(.+\\))?$") to "§3",
			Regex("^\\+\\d+ Bits$") to "§b",
			Regex("^Total Damage as .+: [\\d,.]+\\s?(\\(NEW RECORD!\\))?$") to "§e",
			Regex("^Ally Healing: [\\d,.]+\\s?(\\(NEW RECORD!\\))?$") to "§a",
			Regex("^Enemies Killed: \\d+\\s?(\\(NEW RECORD!\\))?$") to "§b",
			Regex("^Deaths: \\d+$") to "§c",
			Regex("^Secrets Found: \\d+$") to "§b",
		)
		val extraStatsHeader = Regex(" {29}> EXTRA STATS <")
	}
}
