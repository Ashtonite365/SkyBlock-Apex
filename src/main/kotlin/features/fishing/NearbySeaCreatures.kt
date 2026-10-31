// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fishing

object NearbySeaCreatures {
	data class Creature(val name: String, val rare: Boolean, val health: String?, val maxHealth: String?)

	data class Options(
		val healthList: Boolean,
		val limit: Int,
		val rareFirst: Boolean,
	)

	private const val AMOUNT = "[\\d.,]+[kMB]?"
	private val HEALTH_PAIR = Regex("($AMOUNT)/($AMOUNT)❤")
	private val SUFFIXES = mapOf('k' to 1e3, 'M' to 1e6, 'B' to 1e9)

	fun parseHealth(tag: String): Pair<String, String>? {
		val m = HEALTH_PAIR.find(tag) ?: return null
		return Pair(m.groupValues[1], m.groupValues[2])
	}

	fun parseShort(s: String): Double? {
		val text = s.replace(",", "").trim()
		val scale = text.lastOrNull()?.let(SUFFIXES::get)
		val digits = if (scale == null) text else text.dropLast(1)
		val value = digits.toDoubleOrNull() ?: return null
		return value * (scale ?: 1.0)
	}

	fun healthColour(cur: Double, max: Double): String {
		val share = if (max > 0) cur / max else 0.0
		return when {
			share > 0.66 -> "§a"
			share > 0.33 -> "§e"
			else -> "§c"
		}
	}

	fun fraction(c: Creature): Double {
		val cur = c.health?.let(::parseShort)
		val max = c.maxHealth?.let(::parseShort)
		return if (cur == null || max == null || !(max > 0)) 1.0 else cur / max
	}

	fun fromTag(tag: String, known: List<Pair<String, Boolean>>): Creature? {
		if (!tag.contains('❤')) return null
		val match = known.firstOrNull { (name, _) -> name in tag } ?: return null
		val pair = parseHealth(tag)
		return Creature(match.first, match.second, pair?.first, pair?.second)
	}

	fun sorted(creatures: List<Creature>, rareFirst: Boolean): List<Creature> {
		if (!rareFirst) return creatures
		return creatures.sortedWith(compareBy({ if (it.rare) 0 else 1 }, { fraction(it) }))
	}

	fun summaryLine(count: Int, since: String?): String? {
		if (count < 1) return null
		val timer = if (since == null) "" else " §7($since)"
		return "§bSea creatures: §f$count$timer"
	}

	fun healthLine(c: Creature): String {
		val nameColour = if (c.rare) "§6" else "§b"
		val cur = c.health
		val max = c.maxHealth
		if (cur == null || max == null) return nameColour + c.name
		val colour = healthColour(parseShort(cur) ?: 0.0, parseShort(max) ?: 0.0)
		return "$nameColour${c.name} $colour$cur§f/§a$max§c ❤"
	}

	fun lines(creatures: List<Creature>, since: String?, opts: Options): Pair<List<String>, List<String>> {
		val summary = summaryLine(creatures.size, since)?.let { listOf(it) } ?: emptyList()
		if (!opts.healthList) return Pair(summary, emptyList())
		val shown = sorted(creatures.filter { it.health != null }, opts.rareFirst)
		val cap = maxOf(1, opts.limit)
		val out = shown.take(cap).mapTo(ArrayList(), ::healthLine)
		val hidden = shown.size - cap
		if (hidden > 0) out.add("§7+$hidden more")
		return Pair(summary, out)
	}
}
