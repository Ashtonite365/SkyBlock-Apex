// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import dev.ashtonite365.apex.util.stripSectionCodes

object EnchantedBookNames {

	private val romanPlaces = arrayOf(
		arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"),
		arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"),
		arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"),
	)

	fun roman(n: Int): String {
		if (n <= 0) return n.toString()
		val text = StringBuilder("M".repeat(n / 1000))
		for (place in 2 downTo 0) {
			var unit = 1
			repeat(place) { unit *= 10 }
			text.append(romanPlaces[place][n / unit % 10])
		}
		return text.toString()
	}

	private val bookId = "^([A-Z0-9_]+);(\\d+)$".toRegex()

	fun parseBookId(internalName: String): Pair<String, Int>? =
		bookId.matchEntire(internalName)?.let { it.groupValues[1] to it.groupValues[2].toInt() }

	private val idNameOverrides = mapOf(
		"AIMING" to "Dragon Tracer",
		"ARCANE" to "Woodsplitter",
		"DRAGON_HUNTER" to "Gravity",
		"FEROCIOUS_MANA" to "Vivacious Vitality",
		"HARDENED_MANA" to "Hardened Vitality",
		"STRONG_MANA" to "Strong Vitality",
		"MANA_VAMPIRE" to "Vampiric Vitality",
		"MAGMARIZER" to "Pyroclasm",
		"PRISTINE" to "Prismatic",
		"SYPHON" to "Drain",
		"TRIPLE_STRIKE" to "Triple-Strike",
		"JERRY" to "Ultimate Jerry",
		"ULTIMATE_JERRY" to "Ultimate Jerry",
		"ULTIMATE_WISE" to "Ultimate Wise",
		"ULTIMATE_REITERATE" to "Duplex",
	)
	private val lowerWords = setOf("of", "the")

	fun baseNameFromId(enchant: String): String {
		idNameOverrides[enchant]?.let { return it }
		val words = enchant.removePrefix("ULTIMATE_").lowercase().split('_').filter { it.isNotEmpty() }
		val titled = words.mapIndexed { i, w -> if (i > 0 && w in lowerWords) w else w.replaceFirstChar(Char::uppercaseChar) }
		if (titled.size > 1 && titled[0] == "Turbo") return "Turbo-" + titled.drop(1).joinToString(" ")
		return titled.joinToString(" ")
	}

	private val nameLine = "^([A-Z][A-Za-z'\\- ]*?) ([IVXLCDM]+|\\d+)$".toRegex()
	private val notNames = listOf("Requires ", "Enchanting ", "Apply Cost", "Applicable on")

	fun baseNameFromLore(lore: List<String>, level: Int): String? {
		var first: String? = null
		for (raw in lore) {
			val line = raw.stripSectionCodes().trim()
			if (line.length > 40 || notNames.any(line::startsWith)) continue
			val m = nameLine.matchEntire(line) ?: continue
			val base = m.groupValues[1].trim()
			if (m.groupValues[2] == roman(level) || m.groupValues[2] == level.toString()) return base
			if (first == null) first = base
		}
		return first
	}

	fun bookName(internalName: String, displayName: String, lore: List<String>): String? {
		if (!displayName.stripSectionCodes().trim().endsWith("Enchanted Book")) return null
		val (enchant, level) = parseBookId(internalName) ?: return null
		val base = baseNameFromLore(lore, level) ?: baseNameFromId(enchant)
		return base + " " + roman(level)
	}

	fun repoIdFor(enchantKey: String, level: Int): String = enchantKey.uppercase() + ";" + level

	fun bazaarStockFor(internalName: String): String? =
		parseBookId(internalName)?.let { (enchant, level) -> "ENCHANTMENT_${enchant}_$level" }
}
