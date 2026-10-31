// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.menus

object MenuTitles {

	const val LIMIT = 32
	const val MIN_CUT = 30

	private const val ARROW = "[^\\p{L}\\p{N}\\s\"'()&.,!?-]\\s*"

	fun endsWith(suffix: String, prefix: String = "", ignoreCase: Boolean = true): Regex {
		val s = suffix.trim()
		val cuts = (s.length - 1 downTo 1).map { Regex.escape(s.substring(0, it).trimEnd()) }.distinct().filter { it != "\\Q\\E" }
		val cut = if (cuts.isEmpty()) "" else "|^(?=.{$MIN_CUT,}$).*?$ARROW(?:${cuts.joinToString("|")})\\s*$"
		val p = "$prefix${Regex.escape(s)}\\s*$$cut"
		return if (ignoreCase) Regex(p, RegexOption.IGNORE_CASE) else Regex(p)
	}

	fun cutPrefixOf(full: String, ignoreCase: Boolean = true): Regex {
		val f = full.trim()
		val alts = (f.length - 1 downTo MIN_CUT - 1).filter { it > 0 }.map { Regex.escape(f.substring(0, it).trimEnd()) }.distinct()
		val p = if (alts.isEmpty()) "(?!)" else "^(?=.{$MIN_CUT,}$)(?:${alts.joinToString("|")})\\s*$"
		return if (ignoreCase) Regex(p, RegexOption.IGNORE_CASE) else Regex(p)
	}

	fun cutWord(word: String): String {
		val w = word.trim(); require(w.isNotEmpty())
		var p = ""
		for (i in w.length - 1 downTo 1) p = "(?:" + Regex.escape(w[i].toString()) + p + ")?"
		return Regex.escape(w[0].toString()) + p
	}

	fun cutTail(name: String, suffix: String, ignoreCase: Boolean = true): Regex {
		val p = "^(?=.{$MIN_CUT,}$)$name\\s${cutWord(suffix)}$"
		return if (ignoreCase) Regex(p, RegexOption.IGNORE_CASE) else Regex(p)
	}

	val ELECTION = Regex("^Election(?:, Year \\d+)?$", RegexOption.IGNORE_CASE)

	fun maybeCut(title: String) = title.length >= MIN_CUT

	class PhraseList(entries: List<String>) {
		private val patterns = entries.map { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it.lowercase()) + "(?![\\p{L}\\p{N}])") }

		fun matches(text: String): Boolean = text.lowercase().let { t -> patterns.any { it.containsMatchIn(t) } }
	}

	fun anyOf(vararg r: Regex): Regex = Regex(r.joinToString("|") { "(?:${it.pattern})" }, RegexOption.IGNORE_CASE)

	fun matchesEnd(title: String, suffix: String, sep: String = "➜"): Boolean {
		val t = title.trimEnd(); val s = suffix.trim()
		if (t.endsWith(s, true)) return true
		if (title.length < MIN_CUT) return false
		val tail = t.substringAfterLast(sep, "").trim()
		return tail.isNotEmpty() && tail.length < s.length && s.startsWith(tail, true)
	}
}
