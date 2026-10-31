// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.dungeon

import dev.ashtonite365.apex.repo.ItemNameMatch

object ChestLootPricing {
	interface Lookup {

		fun byName(name: String): String?

		fun bookByName(enchantName: String): String?
		fun exists(id: String): Boolean
	}

	sealed interface Line {
		data class Essence(val type: String, val amount: Int) : Line {
			val id get() = "ESSENCE_" + type.uppercase()
		}

		data class Item(val id: String?, val amount: Int = 1) : Line
	}

	private val essenceLine = "^(\\w+) Essence x([\\d,]+)$".toRegex()
	private val bookLine = "^Enchanted Book \\((.+)\\)$".toRegex()
	private val countSuffix = "^(.+?) x([\\d,]+)$".toRegex()
	private val masterFloor = "\\bM[1-7]\\b".toRegex()
	private val masterWord = "\\bMaster\\b".toRegex(RegexOption.IGNORE_CASE)

	fun isMasterMode(title: String): Boolean = masterWord.containsMatchIn(title) || masterFloor.containsMatchIn(title)

	fun isChestPreviewTitle(title: String): Boolean {
		val t = title.trim()
		return t.contains("Catacombs - ") || t.startsWith("Kuudra - ")
	}

	fun isCroesusPreview(title: String, slots: List<Pair<String, List<String>>>): Boolean {
		if (title.trim() == "Croesus") return false
		if (isChestPreviewTitle(title)) return true
		val backToCroesus = slots.any { (name, lore) -> name.trim() == "Go Back" && lore.any { it.trim() == "To Croesus" } }
		return backToCroesus && slots.any { croesusSplit(it.second) != null }
	}

	private fun number(s: String) = s.replace(",", "").toIntOrNull() ?: 1

	fun isBookLine(text: String) = bookLine.matches(text.trim())

	fun parseLine(text: String, masterMode: Boolean, lookup: Lookup): Line? {
		val t = text.trim()
		if (t.isEmpty()) return null
		essenceLine.matchEntire(t)?.let { return Line.Essence(it.groupValues[1], number(it.groupValues[2])) }

		bookLine.matchEntire(t)?.let {
			val name = it.groupValues[1].trim()
			return Line.Item(lookup.bookByName(name) ?: lookup.byName(name))
		}
		lookup.byName(t)?.let { return Line.Item(variant(it, masterMode, lookup)) }
		countSuffix.matchEntire(t)?.let { m ->
			lookup.byName(m.groupValues[1].trim())?.let { return Line.Item(variant(it, masterMode, lookup), number(m.groupValues[2])) }
		}
		return Line.Item(null)
	}

	private fun variant(id: String, masterMode: Boolean, lookup: Lookup) =
		ItemNameMatch.dungeonVariant(id, masterMode, lookup::exists)

	fun amountFromName(name: String, stackCount: Int): Int =
		countSuffix.matchEntire(name.trim())?.let { number(it.groupValues[2]) } ?: stackCount

	fun contentsBookLines(lore: List<String>): List<String> = lore.map { it.trim() }.filter(::isBookLine)

	fun value(line: Line, price: (String) -> Double?): Double? = when (line) {
		is Line.Essence -> price(line.id)?.times(line.amount)
		is Line.Item -> line.id?.let(price)?.times(line.amount)
	}

	private val coinsLine = "^([\\d,]+) Coins$".toRegex()
	private val croesusSkip = setOf("", "Can't open another chest!", "Already opened!", "Click to open!")

	fun coinCost(lines: List<String>): Double =
		lines.sumOf { l -> coinsLine.matchEntire(l.trim())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }

	fun croesusSplit(lore: List<String>): Pair<List<Int>, Int>? {
		if (lore.firstOrNull()?.trim() != "Contents") return null
		val items = mutableListOf<Int>()
		for (i in 1 until lore.size) {
			val t = lore[i].trim()
			if (t == "Cost") return items to i + 1
			if (t !in croesusSkip) items.add(i)
		}
		return null
	}
}
