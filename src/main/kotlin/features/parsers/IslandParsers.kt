/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * APEX-26.3 PORT: parsers and data models for the upcoming Jerry, Crimson Isle and Rift features.
 * Original implementation written for this port from feature descriptions only (similar features exist in
 * and); no code copied. Pure functions only: the timers, trackers, HUDs and alerts that use
 * them will be built on the shared systems (CATEGORY-API.md) once those are on this branch's base.
 * Chat wording is best effort and still needs in-game confirmation.
 */
package dev.ashtonite365.apex.features.parsers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun parseShortDuration(text: String): Duration? {
	val m = Regex("^\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?\\s*$").matchEntire(text) ?: return null
	if (m.groupValues.drop(1).all { it.isEmpty() }) return null
	fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
	return g(1).hours + g(2).minutes + g(3).seconds
}

object JerryParser {

	enum class Colour { GREEN, BLUE, PURPLE, GOLDEN }

	data class JerryData(
		val counts: MutableMap<Colour, Int> = mutableMapOf(),
		var lastJerryMillis: Long? = null,
	)

	private val FOUND = Regex("^(?:☺ )?(?:You (?:discovered|located|found)|There is) an? (Green|Blue|Purple|Golden) Jerry!.*")

	fun parse(line: String): Colour? =
		FOUND.matchEntire(line.trim())?.let { Colour.valueOf(it.groupValues[1].uppercase()) }

	fun apply(data: JerryData, line: String, nowMillis: Long): Boolean {
		val colour = parse(line) ?: return false
		data.counts.merge(colour, 1, Int::plus)
		data.lastJerryMillis = nowMillis
		return true
	}

	fun timeSinceLast(data: JerryData, nowMillis: Long): Duration? =
		data.lastJerryMillis?.let { (nowMillis - it).coerceAtLeast(0).let { ms -> (ms / 1000).seconds } }
}

object CrimsonParser {
	enum class Miniboss(val chatName: String) {
		BLADESOUL("BLADESOUL"),
		BARBARIAN_DUKE_X("BARBARIAN DUKE X"),
		MAGE_OUTLAW("MAGE OUTLAW"),
		ASHFANG("ASHFANG"),
		MAGMA_BOSS("MAGMA BOSS"),
	}

	enum class Faction { MAGE, BARBARIAN }

	private val DOWN = Regex("^\\s*([A-Z ]+?) DOWN!\\s*$")
	private val REPUTATION = Regex("^\\s*\\+([0-9,]+) (Mage|Barbarian) Reputation.*")

	fun parseMinibossDown(line: String): Miniboss? {
		val name = DOWN.matchEntire(line)?.groupValues?.get(1) ?: return null
		return Miniboss.entries.firstOrNull { it.chatName == name }
	}

	data class ReputationGain(val faction: Faction, val amount: Int)

	fun parseReputation(line: String): ReputationGain? {
		val m = REPUTATION.matchEntire(line) ?: return null
		return ReputationGain(Faction.valueOf(m.groupValues[2].uppercase()), m.groupValues[1].replace(",", "").toInt())
	}
}

object RiftParser {

	private val TIME_LEFT = Regex("^\\D*?((?:\\d+h\\s*)?(?:\\d+m\\s*)?(?:\\d+s)?)\\s*Left.*$")

	fun parseTimeLeft(line: String): Duration? {
		val m = TIME_LEFT.matchEntire(line.trim()) ?: return null
		return parseShortDuration(m.groupValues[1])
	}

	fun timeLeftFromScoreboard(lines: List<String>): Duration? = lines.firstNotNullOfOrNull { parseTimeLeft(it) }
}
