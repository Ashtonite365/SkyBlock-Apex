// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object BoosterCookieLogic {
	sealed interface Status {

		data object Unknown : Status
		data object Inactive : Status

		data class Active(val text: String, val remaining: Duration?) : Status
	}

	enum class Warning { LOW, EXPIRED }

	private const val TAB_LABEL = "Cookie Buff"
	private const val NONE_IN_TAB = "Not active! Obtain booster cookies"

	private const val UNDER_AN_HOUR = "Less than an hour"

	private fun unitLength(unit: Char, n: Long): Duration? = when (unit) {
		's' -> n.seconds
		'm' -> n.minutes
		'h' -> n.hours
		'd' -> n.days
		'M' -> (n * 30).days
		'y' -> (n * 365).days
		else -> null
	}

	private fun isPatternSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

	fun parseRemaining(text: String): Duration? {
		val t = text.trim()
		if (t.equals(UNDER_AN_HOUR, ignoreCase = true)) return 59.minutes
		var total: Duration? = null
		var i = 0
		while (i < t.length) {
			var j = i
			while (j < t.length && t[j] in '0'..'9') j++
			var k = j
			while (k < t.length && isPatternSpace(t[k])) k++
			val part = if (j > i && k < t.length) unitLength(t[k], 0) else null
			if (part == null) {
				i++
				continue
			}
			total = (total ?: Duration.ZERO) + unitLength(t[k], t.substring(i, j).toLong())!!
			i = k + 1
		}
		return total
	}

	private fun meansInactive(text: String) = text.contains("INACTIVE", ignoreCase = true) || text.startsWith("Not active")

	fun readStatus(lines: List<String>): Status {
		for (i in lines.indices) {
			val line = lines[i].trim()
			if (line.startsWith(NONE_IN_TAB)) return Status.Inactive
			if (!line.startsWith(TAB_LABEL)) continue
			val sameLine = line.substring(TAB_LABEL.length).trimStart(':', ' ')
			val value = sameLine.ifEmpty { lines.getOrNull(i + 1)?.trim() ?: "" }
			return when {
				value.isEmpty() -> Status.Unknown
				meansInactive(value) -> Status.Inactive
				else -> Status.Active(value, parseRemaining(value))
			}
		}
		return Status.Unknown
	}

	fun readLore(lore: List<String>): Status {
		val lines = lore.map(String::trim)
		val status = lines.firstOrNull { it.startsWith("Status:") }
		if (status != null && status.contains("not active", ignoreCase = true)) return Status.Inactive
		val value = lines.firstOrNull { it.startsWith("Duration:") }?.substring("Duration:".length)?.trim()
		if (value.isNullOrEmpty()) return Status.Unknown
		if (value.contains("not active", ignoreCase = true) || value.contains("inactive", ignoreCase = true)) return Status.Inactive
		val left = parseRemaining(value)
		return when {
			left == null -> Status.Active(value, null)
			left.isPositive() -> Status.Active(value, left)
			else -> Status.Inactive
		}
	}

	fun combine(tab: Status, lore: Status): Status = if (tab == Status.Unknown) lore else tab

	fun statusLine(s: Status): String? = when (s) {
		is Status.Active -> "§6Cookie Buff: §a${s.text}"
		Status.Inactive -> "§6Cookie Buff: §cNot active"
		Status.Unknown -> null
	}

	fun isPending(status: Status, threshold: Duration): Boolean =
		status == Status.Inactive || (status is Status.Active && status.remaining != null && status.remaining < threshold)

	class Warner {
		private val given = mutableSetOf<Warning>()

		fun reset() = given.clear()

		private fun once(w: Warning, wanted: Boolean): Warning? = if (wanted && given.add(w)) w else null

		fun update(status: Status, threshold: Duration, warnLow: Boolean, warnExpired: Boolean): Warning? {
			if (status == Status.Unknown) return null
			if (status !is Status.Active) return once(Warning.EXPIRED, warnExpired)
			given -= Warning.EXPIRED
			val left = status.remaining ?: return null
			if (left >= threshold) {
				given -= Warning.LOW
				return null
			}
			return once(Warning.LOW, warnLow)
		}
	}
}
