// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.collections

import dev.ashtonite365.apex.features.menus.support.MenuText

data class LoreProgress(val label: String, val pct: Double?, val current: Double?, val max: Double?) {

	val fraction: Double? get() = when {
		current != null && max != null && max > 0 -> (current / max).coerceIn(0.0, 1.0)
		pct != null -> (pct / 100).coerceIn(0.0, 1.0)
		else -> null
	}
	val done get() = (fraction ?: 0.0) >= 1.0
}

object LoreParse {
	private val PCT_LINE = Regex("^(.*?):\\s*([\\d.,]+)\\s*%\\s*$")

	private val FRACTION = Regex("([\\d][\\d,]*(?:\\.\\d+)?\\s*[kKmMbB]?)\\s*/\\s*([\\d][\\d,]*(?:\\.\\d+)?\\s*[kKmMbB]?)\\s*$")

	fun fraction(line: String): Pair<Double, Double>? {
		val m = FRACTION.find(MenuText.clean(line)) ?: return null
		val a = MenuText.number(m.groupValues[1]) ?: return null
		val b = MenuText.number(m.groupValues[2]) ?: return null
		return a to b
	}

	fun progress(lore: List<String>): List<LoreProgress> {
		val clean = lore.map(MenuText::clean)
		val out = mutableListOf<LoreProgress>()
		clean.forEachIndexed { i, l ->
			val m = PCT_LINE.matchEntire(l) ?: return@forEachIndexed
			val pct = m.groupValues[2].replace(",", "").toDoubleOrNull()
			val fr = clean.getOrNull(i + 1)?.takeIf { PCT_LINE.matchEntire(it) == null }?.let(::fraction)
			out += LoreProgress(m.groupValues[1].trim(), pct, fr?.first, fr?.second)
		}
		return out
	}

	fun progress(lore: List<String>, prefix: String) = progress(lore).firstOrNull { it.label.startsWith(prefix, true) }

	fun value(lore: List<String>, label: String): Double? = lore.map(MenuText::clean)
		.firstOrNull { it.startsWith("$label:", true) }?.substringAfter(':')?.let(MenuText::number)

	fun block(lore: List<String>, header: String): List<String> {
		val clean = lore.map(MenuText::clean)

		val names = listOf(header, header.removeSuffix("s"))
		val i = clean.indexOfFirst { l -> l.endsWith(':') && names.any { h -> l.equals("$h:", true) || l.endsWith(" $h:", true) } }
		if (i < 0) return emptyList()
		return clean.drop(i + 1).takeWhile { it.isNotBlank() }
	}

	private val ROMAN = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
	fun roman(s: String): Int? {
		if (s.isEmpty() || s.any { it !in ROMAN }) return null
		var total = 0
		for (i in s.indices) {
			val v = ROMAN.getValue(s[i]); val n = s.getOrNull(i + 1)?.let(ROMAN::getValue) ?: 0
			total += if (v < n) -v else v
		}
		return total.takeIf { it > 0 }
	}

	fun splitTier(name: String): Pair<String, Int?> {
		val t = MenuText.clean(name)
		val last = t.substringAfterLast(' ', "")
		if (last.isEmpty() || last.length == t.length) return t to null
		val n = last.toIntOrNull() ?: roman(last) ?: return t to null
		return t.substringBeforeLast(' ').trim() to n
	}

	val NAV = setOf("go back", "close", "next page", "previous page", "search", "sort", "filter")
	fun isNav(name: String) = MenuText.clean(name).lowercase().let { n -> NAV.any { n == it || n.startsWith("$it ") } }

	fun pageOf(title: String): Pair<Int, Int>? = Regex("^\\((\\d+)/(\\d+)\\)|\\((\\d+)/(\\d+)\\)$").find(MenuText.clean(title).trim())
		?.let { m -> m.groupValues.drop(1).filter { it.isNotEmpty() }.let { g -> g[0].toInt() to g[1].toInt() } }
}
