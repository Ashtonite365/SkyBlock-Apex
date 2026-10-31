// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.skyblock

import kotlin.math.ceil
import kotlin.math.floor

object DungeonSecretCounts {
	private const val PUA_FIRST = '\uE000'
	private const val PUA_LAST = '\uF8FF'
	private val wideSpaces = setOf('\u00A0', '\u2007', '\u202F')
	private val lineBreaks = setOf('\n', '\r', '\u0085', '\u2028', '\u2029')

	private const val AMOUNT = """(\d{1,3}(?:,\d{3})+|\d+)"""
	private val roomCount = Regex("""$AMOUNT */ *$AMOUNT *secrets?\b""", RegexOption.IGNORE_CASE)
	private val roomWithout = Regex("""(?:^|\s)no secrets\b""", RegexOption.IGNORE_CASE)
	private val tabShare = Regex("""^\s*secrets found:\s*$AMOUNT(?:\.(\d+))?\s*%""", RegexOption.IGNORE_CASE)
	private val tabTally = Regex("""^\s*secrets found:\s*$AMOUNT\s*$""", RegexOption.IGNORE_CASE)

	fun plain(text: String): String {
		val out = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val c = text[i++]
			when {
				c == '§' -> if (i < text.length && text[i] !in lineBreaks) i++
				c in PUA_FIRST..PUA_LAST || c in wideSpaces -> out.append(' ')
				else -> out.append(c)
			}
		}
		return out.toString()
	}

	private fun amount(digits: String): Int? = digits.filter { it != ',' }.toIntOrNull()

	data class RoomCount(val found: Int, val total: Int) {
		val done: Boolean get() = total > 0 && found >= total
	}

	fun fromActionBar(line: String): RoomCount? {
		val text = plain(line)
		val hit = roomCount.find(text) ?: return if (roomWithout.containsMatchIn(text)) RoomCount(0, 0) else null
		val found = amount(hit.groupValues[1]) ?: return null
		val total = amount(hit.groupValues[2]) ?: return null
		return RoomCount(found, total).takeIf { total <= 20 && found <= total }
	}

	data class TeamCount(val found: Int?, val share: Double?) {

		val estimatedTotal: Int?
			get() {
				if (found == null || share == null || found <= 0 || share <= 0.0) return null
				return floor(found * 100.0 / share + 0.5).toInt()
			}
	}

	fun fromTab(lines: List<String>): TeamCount {
		var found: Int? = null
		var share: Double? = null
		for (line in lines.map(::plain)) {
			val s = tabShare.find(line)
			if (s == null) {
				tabTally.find(line)?.let { found = amount(it.groupValues[1]) }
				continue
			}
			val whole = s.groupValues[1].filter { it != ',' }
			share = (whole + "." + s.groupValues[2].ifEmpty { "0" }).toDoubleOrNull()
		}
		return TeamCount(found, share)
	}

	fun stillNeeded(team: TeamCount, fraction: Double): Int? {
		val total = team.estimatedTotal ?: return null
		return maxOf(0, ceil(total * fraction).toInt() - (team.found ?: 0))
	}

	fun colourFor(found: Int, total: Int): String =
		if (2 * found < total) "§c" else if (4 * found < 3 * total) "§e" else "§a"
}
