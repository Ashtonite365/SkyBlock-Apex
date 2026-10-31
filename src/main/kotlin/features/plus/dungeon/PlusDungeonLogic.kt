// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.dungeon

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object PlusDungeonLogic {

	val bloodOpen = listOf(
		"[BOSS] The Watcher: Things feel a little more roomy now, eh?",
		"[BOSS] The Watcher: Oh.. hello?",
		"[BOSS] The Watcher: I'm starting to get tired of seeing you around here...",
		"[BOSS] The Watcher: You've managed to scratch and claw your way here, eh?",
		"[BOSS] The Watcher: So you made it this far... interesting.",
		"[BOSS] The Watcher: Ah, we meet again...",
		"[BOSS] The Watcher: Ah, you've finally arrived.",
	)
	const val BLOOD_MOVE = "[BOSS] The Watcher: Let's see how you can handle this."

	private val bloodBuckets = listOf(
		Triple(31.0, 34.0, 36), Triple(28.0, 31.0, 33), Triple(25.0, 28.0, 30), Triple(22.0, 25.0, 27), Triple(1.0, 22.0, 24),
	)

	fun bloodMovePrediction(moveTime: Duration, lag: Duration = Duration.ZERO): Duration? {
		val secs = moveTime.inWholeMilliseconds / 1000.0
		for ((lo, hi, predicted) in bloodBuckets) if (secs >= lo && secs <= hi) return lag + predicted.seconds
		return null
	}

	private val puzzleFail = Regex("^PUZZLE FAIL! (\\S+) .*")
	private val quizFail = Regex("^\\[STATUE] Oruo the Omniscient: (\\S+) chose the wrong .*")

	fun puzzleFailer(line: String): String? {
		val m = puzzleFail.matchEntire(line) ?: quizFail.matchEntire(line) ?: return null
		return m.groupValues[1]
	}

	private val bossNames = listOf(
		"The Watcher", "Bonzo", "Scarf", "The Professor", "Livid", "Enderman", "Thorn", "Sadan",
		"Maxor", "Storm", "Goldor", "Necron", "Wither King",
	)

	private const val BOSS_PREFIX = "[BOSS] "

	private fun bossSpeaker(line: String): String? {
		if (!line.startsWith(BOSS_PREFIX)) return null
		val rest = line.substring(BOSS_PREFIX.length)
		val colon = rest.indexOf(':')
		return if (colon < 0) rest else rest.substring(0, colon)
	}

	fun isBossMessage(line: String): Boolean {
		when (line) {
			"[BOSS] The Watcher: You have proven yourself. You may pass." -> return false
			"The Crystal withers your soul as you hold it in your hands!", "It doesn't seem like that is supposed to go there." -> return true
		}
		val speaker = bossSpeaker(line) ?: return false
		for (b in bossNames) if (speaker == b || speaker.endsWith(" $b")) return true
		return false
	}

	fun floorBossOf(line: String): String? {
		val speaker = bossSpeaker(line) ?: return null
		if (speaker == "The Watcher") return null
		return if (speaker in bossNames) speaker else null
	}

	enum class FilterType(vararg val patterns: String) {
		PREPARE(
			"^\\[NPC] Mort: (?:Here, I found this map|You should find it useful|Good luck\\.|Talk to me to change your class).*",
			".* is now ready!$", "^Dungeon starts in \\d+ seconds?\\.$", ".* has started the dungeon countdown\\..*",
		),
		START(
			"^Your active Potion Effects have been paused and stored\\..*",
			"^You can no longer consume or splash any potions during the remainder of this Dungeon run!$",
		),
		AMBIENCE("^A shiver runs down your spine\\.\\.\\.$"),
		PICKUP(
			".* has obtained (?:Superboom TNT|Revive Stone|Premium Flesh|Beating Heart|Blessing of .*).*",
			".*found a Wither Essence! Everyone gains an extra essence!$",
			"^.* the Fairy: (?:You killed me!|Have a great life!).*",
			"^A Blessing of .* was picked up!$",
		),
		REMINDER("^RIGHT CLICK on (?:a WITHER door|the BLOOD DOOR) to open it\\..*"),
		BUFF("^DUNGEON BUFF! .*", "^BUFF! You have gained Healing V!$", "^ {5}(?:Granted|Grants|Also granted) you .*"),
		NOT_POSSIBLE(
			"^You cannot hit the silverfish while it's moving!$", "^You cannot move the silverfish in that direction!$",
			"^There are blocks in the way!$", "^This chest has already been searched!$", "^This lever has already been used\\.$",
			"^You cannot do that in this room!$", "^You do not have the key for this door!$",
			"^You have already opened this dungeon chest!$", "^You cannot use abilities in this room!$",
			"^A mystical force in this room prevents you from using that ability!$",
		),
		DAMAGE(
			"^The .* (?:struck|hit) you for .* damage!$", ".* (?:struck|hit) you for [\\d,.]+ (?:true )?damage\\.$",
			".* exploded, hitting you for .* damage\\.$", "^Your .* hit .* (?:enemy|enemies) for .* damage\\.$",
			"^The Mage's Magma burnt you for .* true damage\\.$",
		),
		ABILITY(
			".* used .* on you!$", "^Your Guided Sheep hit .*", "^You were healed for .* health by .*",
			".* picked up your .* Orb!$", "^This ability is on cooldown for .*s\\.$", ".* healed you for .* health!$",
			"^Your bone plating reduced the damage you took by .*", ".* formed a tether with you!$",
			"^Your tether with .* healed you for .*", "^Your Spirit Pet (?:healed|hit) .*", "^You need at least .* mana to activate this!$",
			"^Your fairy healed .*", ".* fairy healed you for .*", "^(?:Guided Sheep|Creeper Veil|Rapid Fire|Castle of Stone|Ragnarok|Thunderstorm).* (?:is now available|Activated|De-activated|is ready to use).*",
		),
		PUZZLE(
			"^PUZZLE SOLVED! .* (?:wasn't fooled by|tied Tic Tac Toe).*",
			"^\\[STATUE] Oruo the Omniscient: (?!.*chose the wrong).*",
		),
		;

		val regexes = patterns.map { Regex(it) }

		fun matches(line: String): Boolean {
			for (r in regexes) if (r.matches(line)) return true
			return false
		}
	}

	fun filterType(line: String): FilterType? {
		for (t in FilterType.entries) if (t.matches(line)) return t
		return null
	}

	private val keyRegex = Regex("(?:^A |has obtained )(Wither|Blood) Key(?: was picked up)?!")

	fun copilotStep(line: String, playerName: String?): String? {
		if (line.endsWith(" has started the dungeon countdown. The dungeon will begin in 1 minute.")) return "Ready up"
		if (playerName != null && line.endsWith(" is now ready!") && line.contains(playerName)) return "Wait for the dungeon to start!"
		if (line.startsWith("[NPC] Mort: Here, I found this map")) return "Clear first room"
		keyRegex.find(line)?.let { return "Open ${it.groupValues[1]} Door" }
		if (line.endsWith(" opened a WITHER door!")) return "Clear next room"
		return when (line) {
			"The BLOOD DOOR has been opened!" -> "Wait for Blood Room to fully spawn"
			"[BOSS] The Watcher: That will be enough for now." -> "Clear Blood Room"
			"[BOSS] The Watcher: You have proven yourself. You may pass." -> "Enter Boss Room"
			else -> if (floorBossOf(line) != null) "Defeat the boss! Good luck :)" else null
		}
	}

	private val death = Regex("^ ☠ .+(?:(?:crush|di(?:sconnect)?|kill|suffocat)ed|fell|burnt).+became a ghost\\.$")
	fun isDeath(line: String) = death.matches(line)

	fun deathColour(deaths: Int) = if (deaths == 1 || deaths == 2) "§e" else if (deaths == 3) "§c" else "§4"

	private val milestone = Regex(".*Milestone .: You have (?:tanked and )?(?:dealt|healed) .*so far! .*")
	fun isMilestone(line: String) = milestone.matches(line)

	private val entered = Regex("^(?:\\[[^]]+] )?(\\w{1,16}) entered (?:MM )?The Catacombs, .*!$")

	fun enteredPlayer(line: String): String? = entered.matchEntire(line)?.let { it.groupValues[1] }

	private val teammate = Regex("^(?:§.)*\\[([A-Z])] (?:§.)*(\\w{1,16})(?:§.)* (?:§.)*?§(.)([\\d,]+|DEAD)")

	data class Teammate(val cls: String, val name: String, val colour: Char, val health: String) {

		val isLow get() = colour == 'c' && health != "DEAD"
	}

	fun parseTeammate(formatted: String): Teammate? {
		val m = teammate.find(formatted) ?: return null
		val g = m.groupValues
		return Teammate(g[1], g[2], g[3][0], g[4])
	}

	private val puzzles = Regex("Puzzles: \\((\\d+)\\)")

	fun puzzleCount(tabLines: List<String>): Int? {
		for (l in tabLines) {
			val m = puzzles.find(l) ?: continue
			return m.groupValues[1].toIntOrNull()
		}
		return null
	}

	const val TERRACOTTA_START = "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!"
	const val TERRACOTTA_END = "[BOSS] Sadan: ENOUGH!"

	private val chestsSpawned = Regex("^ *(?:Master Mode )?The Catacombs - Floor .*")
	fun isChestsSpawned(line: String) = chestsSpawned.matches(line)

	private val floor = Regex("The Catacombs \\(([FME]\\d?)\\)")

	fun floorOf(scoreboard: List<String>): String? {
		for (l in scoreboard) {
			val m = floor.find(l) ?: continue
			return m.groupValues[1]
		}
		return null
	}
}
