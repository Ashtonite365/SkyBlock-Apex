// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.reminders

import kotlin.math.sqrt

object PlusMiscLogic {
	private val KICK_LINES = setOf(
		"You were kicked while joining that server!",
		"There was a problem joining SkyBlock, try again in a moment!",
	)

	fun isKick(line: String) = line in KICK_LINES

	private val RESTART = Regex("Server closing: (\\d+):(\\d+)")

	fun restartSeconds(scoreboard: List<String>): Int? {
		for (line in scoreboard) {
			val (minutes, seconds) = RESTART.find(line)?.destructured ?: continue
			return minutes.toInt() * 60 + seconds.toInt()
		}
		return null
	}

	private val SEASON_DAY = Regex("^\\s*(?:Early |Late )?(?:Spring|Summer|Autumn|Winter) \\d+(?:st|nd|rd|th)$")
	private val CLOCK = Regex("^\\s*\\d{1,2}:\\d{2}(?:am|pm).*")

	fun dateLine(scoreboard: List<String>): String? {
		val day = scoreboard.find(SEASON_DAY::matches)?.trim() ?: return null
		val clock = scoreboard.find(CLOCK::matches)?.trim() ?: return day
		return "$day $clock"
	}

	fun speed(dx: Double, dz: Double, ticks: Int = 1) = sqrt(dx * dx + dz * dz) * 20.0 / ticks

	fun recentServers(history: Map<String, Long>, now: Long, windowMillis: Long): List<String> =
		history.entries.filter { now - it.value <= windowMillis }.sortedWith(compareByDescending { it.value }).map { it.key }
}
