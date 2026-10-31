// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

object ActionBarStats {
	data class Parsed(
		val health: Int? = null, val maxHealth: Int? = null,
		val mana: Int? = null, val maxMana: Int? = null,
		val defence: Int? = null,

		val vitality: Int? = null, val maxVitality: Int? = null,

		val healthRange: IntRange? = null, val manaRange: IntRange? = null, val defenceRange: IntRange? = null,
		val vitalityRange: IntRange? = null,
	) {
		val any get() = health != null || mana != null || defence != null || vitality != null
	}

	private class Tok(val ch: Char, val codes: String, val pos: Int = -1)

	private fun tokenize(legacy: String): List<Tok> {
		val out = ArrayList<Tok>(legacy.length)
		var colour = ""
		var fmt = ""
		var i = 0
		while (i < legacy.length) {
			val c = legacy[i]
			if (c == '§' && i + 1 < legacy.length) {
				val k = legacy[i + 1].lowercaseChar()
				when {
					k in "0123456789abcdef" -> { colour = "§$k"; fmt = "" }
					k == 'r' -> { colour = ""; fmt = "" }
					k in "klmno" -> if ("§$k" !in fmt) fmt += "§$k"
				}
				i += 2; continue
			}
			if (c == '§') { i++; continue }
			out += Tok(normaliseSpace(c), colour + fmt, i)
			i++
		}
		return out
	}

	private fun normaliseSpace(c: Char): Char = when (c) {
		'\u00A0', '\u2007', '\u202F', '\u3000', '\u200B', '\u200C', '\u200D', '\uFEFF', '\t' -> ' '
		in '\u2000'..'\u200A' -> ' '
		else -> c
	}

	fun isPrivateUse(c: Char) = c in '\uE000'..'\uF8FF'

	private const val NUM = "(\\d[\\d,]*)"
	private const val GLYPH = "([❤♥✎❈❂\\uE000-\\uF8FF])?"
	private val pairRegex = Regex("$NUM *(?:/ *)$NUM *$GLYPH *(Health|Mana)?(?![\\w])", RegexOption.IGNORE_CASE)
	private val defenceRegex = Regex("(?<![\\d,/])$NUM *([❈\\uE000-\\uF8FF])? *(Defen[sc]e)?(?! */)", RegexOption.IGNORE_CASE)

	private const val VIT_LABEL = "((?:Total +)?Vitality)"

	private val vitalityRegex = Regex("(?<![\\d,])$NUM *(?:/ *)$NUM *(?:(♨) *$VIT_LABEL?|([\\uE000-\\uF8FF])? *$VIT_LABEL|(\\uE028))(?![\\w])", RegexOption.IGNORE_CASE)

	const val GLYPH_HEALTH = '\uE010'
	const val GLYPH_DEFENCE = '\uE008'
	const val GLYPH_MANA = '\uE003'
	const val GLYPH_VITALITY = '\uE028'

	private val vitalityLabelFirstRegex = Regex("(?:Total +)?Vitality *:? *[^\\d\\s/]? *$NUM *(?:/ *)$NUM", RegexOption.IGNORE_CASE)
	private val formatCode = Regex("§.?")

	fun findVitality(plain: String): MatchResult? = vitalityRegex.find(plain) ?: vitalityLabelFirstRegex.find(plain)

	fun parseVitality(line: String): Pair<Int, Int>? {
		val plain = line.replace(formatCode, "").map(::normaliseSpace).joinToString("")
		val m = findVitality(plain) ?: return colourVitality(line)
		return (m.groupValues[1].num() ?: return null) to (m.groupValues[2].num() ?: return null)
	}

	private val HMD_GLYPHS = "❤♥✎❈❂$GLYPH_HEALTH$GLYPH_MANA$GLYPH_DEFENCE"
	private val anyPairRegex = Regex("(?<![\\d,])$NUM *(?:/ *)$NUM *(.)?")

	private fun colourVitality(line: String): Pair<Int, Int>? {
		val toks = tokenize(line)
		return colourVitality(toks, String(CharArray(toks.size) { toks[it].ch }), emptyList())
	}

	private fun colourVitality(toks: List<Tok>, plain: String, taken: List<IntRange>): Pair<Int, Int>? {
		val m = colourVitalityMatch(toks, plain, taken) ?: return null
		return (m.groupValues[1].num() ?: return null) to (m.groupValues[2].num() ?: return null)
	}

	private fun colourVitalityMatch(toks: List<Tok>, plain: String, taken: List<IntRange>): MatchResult? {
		return anyPairRegex.findAll(plain).firstOrNull { p ->
			colourAt(toks, p.range.first) == "§4" && p.groupValues[3].let { it.isEmpty() || it[0] !in HMD_GLYPHS } &&
				taken.none { p.range.first in it }
		}
	}

	private fun String.num() = replace(",", "").toIntOrNull()

	private fun colourAt(toks: List<Tok>, i: Int) = toks.getOrNull(i)?.codes?.take(2) ?: ""

	fun parse(legacy: String): Parsed {
		val toks = tokenize(legacy)
		val plain = String(CharArray(toks.size) { toks[it].ch })
		var health: Pair<MatchResult, Int>? = null
		var mana: Pair<MatchResult, Int>? = null
		val unknown = mutableListOf<MatchResult>()
		val vit = findVitality(plain)
		for (m in pairRegex.findAll(plain)) {
			if (vit != null && m.range.first in vit.range) continue
			val glyph = m.groupValues[3]
			val kw = m.groupValues[4].lowercase()
			val col = colourAt(toks, m.range.first)
			val kind = when {
				glyph == "❤" || glyph == "♥" || glyph == GLYPH_HEALTH.toString() || kw == "health" -> 'h'
				glyph == "✎" || glyph == GLYPH_MANA.toString() || kw == "mana" -> 'm'
				glyph.isNotEmpty() && isPrivateUse(glyph[0]) && col in listOf("§c", "§6") -> 'h'
				glyph.isNotEmpty() && isPrivateUse(glyph[0]) && col in listOf("§b", "§3", "§9") -> 'm'
				else -> '?'
			}
			when {
				kind == 'h' && health == null -> health = m to 0
				kind == 'm' && mana == null -> mana = m to 0
				kind == '?' -> unknown += m
			}
		}
		for (m in unknown) {
			if (health == null && (mana == null || m.range.first < mana.first.range.first)) health = m to 1
			else if (mana == null && (health == null || m.range.first > health.first.range.first)) mana = m to 1
		}
		val taken = listOfNotNull(health?.first?.range, mana?.first?.range, vit?.range)
		var defence: MatchResult? = null
		for (m in defenceRegex.findAll(plain)) {
			if (taken.any { m.range.first in it }) continue
			val glyph = m.groupValues[2]
			val kw = m.groupValues[3]
			val col = colourAt(toks, m.range.first)
			val ok = glyph == "❈" || glyph == GLYPH_DEFENCE.toString() || kw.isNotEmpty() || (glyph.isNotEmpty() && col == "§a")
			if (ok) { defence = m; break }
		}

		val vitM = vit ?: colourVitalityMatch(toks, plain, listOfNotNull(health?.first?.range, mana?.first?.range, defence?.range))
		val vitPair = vitM?.let { m -> m.groupValues[1].num()?.let { a -> m.groupValues[2].num()?.let { a to it } } }
		fun trimRange(r: IntRange): IntRange { var e = r.last; while (e > r.first && plain[e] == ' ') e--; return r.first..e }
		return Parsed(
			health = health?.first?.groupValues?.get(1)?.num(), maxHealth = health?.first?.groupValues?.get(2)?.num(),
			mana = mana?.first?.groupValues?.get(1)?.num(), maxMana = mana?.first?.groupValues?.get(2)?.num(),
			defence = defence?.groupValues?.get(1)?.num(),
			vitality = vitPair?.first, maxVitality = vitPair?.second,
			healthRange = health?.first?.range?.let(::trimRange), manaRange = mana?.first?.range?.let(::trimRange),
			defenceRange = defence?.range?.let(::trimRange), vitalityRange = vitM?.range?.let(::trimRange),
		)
	}

	fun strip(legacy: String, parsed: Parsed, health: Boolean, mana: Boolean, defence: Boolean, vitality: Boolean = false): String {
		val toks = tokenize(legacy)
		val plain = String(CharArray(toks.size) { toks[it].ch })
		val remove = BooleanArray(toks.size)
		fun mark(r: IntRange?) { r?.forEach { if (it in remove.indices) remove[it] = true } }
		if (health) mark(parsed.healthRange)
		if (defence) mark(parsed.defenceRange)
		if (vitality) mark(parsed.vitalityRange)
		if (mana) {
			mark(parsed.manaRange)
			parsed.manaRange?.let { r ->
				Regex("\\G *\\+?\\d[\\d,]*[ʬʧʩ\\uE000-\\uF8FF]").find(plain, r.last + 1)?.let { mark(it.range) }
			}
		}
		if (remove.none { it }) return legacy
		tidyGaps(toks, remove)
		return rebuild(legacy, toks, remove)
	}

	private fun rebuild(legacy: String, toks: List<Tok>, remove: BooleanArray): String {
		val sb = StringBuilder()
		var colour = ""; var fmt = ""
		fun apply(k: Char) {
			when {
				k in "0123456789abcdef" -> { colour = "§$k"; fmt = "" }
				k == 'r' -> { colour = ""; fmt = "" }
				k in "klmno" -> if ("§$k" !in fmt) fmt += "§$k"
			}
		}
		val tailKept = toks.isNotEmpty() && !remove[toks.lastIndex]
		var t = 0
		var i = 0
		while (i < legacy.length) {
			val c = legacy[i]
			if (c == '§') {
				if (i + 1 >= legacy.length) { if (tailKept || t < toks.size) sb.append(c); i++; continue }
				val keep = if (t < toks.size) !remove[t] else tailKept
				val k = legacy[i + 1].lowercaseChar()
				if (keep) { sb.append(c).append(legacy[i + 1]); apply(k) }
				i += 2; continue
			}
			val tok = toks[t]
			if (!remove[t]) {
				if (colour + fmt != tok.codes) {
					sb.append(if (tok.codes.isEmpty()) "§r" else tok.codes)
					colour = ""; fmt = ""
					var j = 0
					while (j + 1 < tok.codes.length) { apply(tok.codes[j + 1]); j += 2 }
				}
				sb.append(c)
			}
			t++; i++
		}
		return sb.toString()
	}

	private fun tidyGaps(toks: List<Tok>, remove: BooleanArray) {
		val removedSegment = remove.copyOf()
		val kept = toks.indices.filter { !remove[it] }
		var k = 0
		while (k < kept.size) {
			if (toks[kept[k]].ch != ' ') { k++; continue }
			var e = k
			while (e + 1 < kept.size && toks[kept[e + 1]].ch == ' ') e++
			val first = kept[k]; val lastIdx = kept[e]
			val touches = (first > 0 && removedSegment[first - 1]) || (lastIdx + 1 < toks.size && removedSegment[lastIdx + 1]) ||
				lastIdx - first != e - k
			val run = kept.subList(k, e + 1)
			if (k == 0 || e == kept.size - 1) run.forEach { remove[it] = true }
			else if (touches) {

				val gaps = mutableListOf(mutableListOf(run[0]))
				for (j in 1 until run.size) if (run[j] == run[j - 1] + 1) gaps.last() += run[j] else gaps += mutableListOf(run[j])
				val keep = gaps.maxBy { it.size }
				for (g in gaps) if (g !== keep) g.forEach { remove[it] = true }
			}
			k = e + 1
		}
	}

	fun hasVisibleText(legacy: String) = legacy.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").isNotBlank()
}
