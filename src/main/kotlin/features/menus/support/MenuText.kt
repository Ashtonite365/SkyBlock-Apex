// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

data class SlotText(val slot: Int, val name: String, val lore: List<String> = emptyList()) {
	val cleanName get() = MenuText.clean(name)
	val cleanLore get() = lore.map(MenuText::clean)
}

object MenuText {

	data class LabelFit(val lines: List<String>, val scale: Float)

	fun fitLabel(text: String, width: Int, measure: (String) -> Int, maxLines: Int = 2, maxH: Int = Int.MAX_VALUE, minScale: Float = 0.5f): LabelFit {
		val t = text.trim()
		if (width <= 0 || measure(t) <= width) return LabelFit(listOf(t), 1f)
		val words = t.split(' ').filter { it.isNotEmpty() }
		fun wrap(w: Float): List<String> {
			val out = mutableListOf<String>(); var cur = ""
			for (wd in words) { val n = if (cur.isEmpty()) wd else "$cur $wd"; if (cur.isEmpty() || measure(n) <= w) cur = n else { out += cur; cur = wd } }
			if (cur.isNotEmpty()) out += cur
			return out
		}
		val linesFit = maxOf(1, minOf(maxLines, if (maxH == Int.MAX_VALUE) maxLines else maxH / 9))
		var scale = 1f
		while (scale > minScale) {
			val ls = wrap(width / scale)
			if (ls.size <= linesFit && ls.all { measure(it) * scale <= width }) return LabelFit(ls, scale)
			scale -= 0.05f
		}
		return LabelFit(wrap(width / minScale), minScale)
	}

	private val COLOUR = Regex("§.?")
	private val SPACES = Regex("[\\s\\u00a0\\u2007\\u202f]+")

	fun clean(s: String) = s.replace(COLOUR, "").replace(SPACES, " ").trim()

	private val NUMBER = Regex("([+-]?\\d[\\d,]*(?:\\.\\d+)?|[+-]?\\.\\d+)\\s*([kKmMbBtT])?(?![a-zA-Z])")

	private val AMOUNT_INPUT = Regex("^\\s*(\\d[\\d,]*(?:\\.\\d+)?|\\.\\d+)\\s*([kKmMbB])?\\s*$")

	fun parseAmount(s: String): Long? {
		val m = AMOUNT_INPUT.matchEntire(s) ?: return null
		val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		val mult = when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
		val v = kotlin.math.floor(base * mult)
		return if (v >= 1 && v < 1e15) v.toLong() else null
	}

	fun number(s: String): Double? {
		val m = NUMBER.find(clean(s)) ?: return null
		val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		val mult = when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; "t" -> 1e12; else -> 1.0 }
		return base * mult
	}

	private val DUR_PART = Regex("((?<![\\d.])\\d+(?:\\.\\d+)?|(?<![a-zA-Z])(?:an?|one))\\s*(y(?:ears?|rs?)?|mo(?:nths?)?|w(?:eeks?|ks?)?|d(?:ays?)?|h(?:ours?|rs?)?|m(?:inutes?|ins?)?|s(?:econds?|ecs?)?)(?![a-zA-Z])", RegexOption.IGNORE_CASE)

	fun durationSeconds(s: String): Long? {
		val t = clean(s)
		Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})").find(t)?.let { m ->
			val (h, mi, se) = m.destructured
			return (h.toLongOrNull() ?: 0) * 3600 + mi.toLong() * 60 + se.toLong()
		}
		var total = 0.0; var any = false
		for (m in DUR_PART.findAll(t)) {
			val n = when (m.groupValues[1].lowercase()) { "a", "an", "one" -> 1.0; else -> m.groupValues[1].toDouble() }
			val u = m.groupValues[2].lowercase()
			val mult = when {
				u.startsWith("mo") -> 2_592_000.0
				u.startsWith("y") -> 31_536_000.0
				u.startsWith("w") -> 604_800.0
				u.startsWith("d") -> 86_400.0
				u.startsWith("h") -> 3_600.0
				u.startsWith("m") -> 60.0
				else -> 1.0
			}
			total += n * mult; any = true
		}
		return if (any) total.toLong() else null
	}

	fun formatDuration(seconds: Long): String {
		val s = seconds.coerceAtLeast(0)
		val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60
		return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m ${s % 60}s"; else -> "${s}s" }
	}

	fun coins(v: Double): String {
		val a = kotlin.math.abs(v)
		fun f(x: Double) = ("%.2f".format(java.util.Locale.ROOT, x)).trimEnd('0').trimEnd('.')
		return when { a >= 1e9 -> f(v / 1e9) + "B"; a >= 1e6 -> f(v / 1e6) + "M"; a >= 1e4 -> f(v / 1e3) + "k"; else -> f(v) }
	}
}
