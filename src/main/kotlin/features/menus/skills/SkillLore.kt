// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

enum class Skill(val label: String, val group: SkillGroup) {
	FARMING("Farming", SkillGroup.GATHERING), MINING("Mining", SkillGroup.GATHERING), FORAGING("Foraging", SkillGroup.GATHERING),
	FISHING("Fishing", SkillGroup.GATHERING), HUNTING("Hunting", SkillGroup.GATHERING),
	COMBAT("Combat", SkillGroup.COMBAT),
	ENCHANTING("Enchanting", SkillGroup.CRAFTING), ALCHEMY("Alchemy", SkillGroup.CRAFTING), CARPENTRY("Carpentry", SkillGroup.CRAFTING),
	TAMING("Taming", SkillGroup.CRAFTING),
	RUNECRAFTING("Runecrafting", SkillGroup.COSMETIC), SOCIAL("Social", SkillGroup.COSMETIC);

	companion object {
		fun byLabel(s: String) = entries.firstOrNull { it.label.equals(s.trim(), true) }
		val NAMES = entries.joinToString("|") { it.label }
	}
}

enum class SkillGroup(val label: String) { GATHERING("Gathering"), COMBAT("Combat"), CRAFTING("Crafting & Pets"), COSMETIC("Cosmetic") }

data class Progress(val percent: Double?, val current: Double?, val needed: Double?, val maxed: Boolean = false) {

	val fraction: Double get() = when {
		maxed -> 1.0
		current != null && needed != null && needed > 0 -> (current / needed).coerceIn(0.0, 1.0)
		percent != null -> (percent / 100).coerceIn(0.0, 1.0)
		else -> 0.0
	}
	fun label(): String = when {
		maxed -> "MAX"
		current != null && needed != null -> "${MenuText.coins(current)}/${MenuText.coins(needed)}" + (percent?.let { " (${fmt(it)}%)" } ?: "")
		percent != null -> "${fmt(percent)}%"
		else -> ""
	}
	private fun fmt(d: Double) = "%.1f".format(java.util.Locale.ROOT, d).removeSuffix(".0")
}

object SkillLore {
	private val ROMAN = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)

	fun level(s: String): Int? {
		val t = s.trim()
		t.toIntOrNull()?.let { return it }
		if (t.isEmpty() || t.any { it !in ROMAN }) return null
		var total = 0
		for (i in t.indices) {
			val v = ROMAN.getValue(t[i]); val n = t.getOrNull(i + 1)?.let(ROMAN::getValue) ?: 0
			total += if (v < n) -v else v
		}
		return total.takeIf { it > 0 }
	}

	private val PCT = Regex("Progress(?: to (?:Level|[A-Za-z ]+?))?\\s*([IVXLCDM\\d]+)?\\s*:\\s*([\\d.,]+)%", RegexOption.IGNORE_CASE)
	private val FRACTION = Regex("([\\d.,]+[kKmMbB]?)\\s*/\\s*([\\d.,]+[kKmMbB]?)")
	private val MAXED = Regex("MAX(?:ED)? (?:SKILL|LEVEL)|Max(?:imum)? level reached", RegexOption.IGNORE_CASE)

	fun progress(lore: List<String>): Progress? {
		val clean = lore.map(MenuText::clean)
		if (clean.any { MAXED.containsMatchIn(it) }) return Progress(100.0, null, null, maxed = true)
		val pctLine = clean.indexOfFirst { PCT.containsMatchIn(it) }
		val pct = if (pctLine >= 0) PCT.find(clean[pctLine])!!.groupValues[2].replace(",", "").toDoubleOrNull() else null

		val fracLine = (if (pctLine >= 0) clean.drop(pctLine + 1).take(2) else clean).firstOrNull { FRACTION.containsMatchIn(it) }
		val f = fracLine?.let { FRACTION.find(it) }
		val cur = f?.let { MenuText.number(it.groupValues[1]) }; val need = f?.let { MenuText.number(it.groupValues[2]) }
		if (pct == null && cur == null) return null
		return Progress(pct, cur, need)
	}

	fun nextLevel(lore: List<String>): Int? = lore.map(MenuText::clean).firstNotNullOfOrNull { PCT.find(it)?.groupValues?.get(1)?.let(::level) }

	private val REWARDS_HDR = Regex("(?:Level [IVXLCDM\\d]+ )?Rewards?:?$", RegexOption.IGNORE_CASE)

	fun rewards(lore: List<String>): List<String> {
		val clean = lore.map(MenuText::clean)
		val i = clean.indexOfFirst { REWARDS_HDR.containsMatchIn(it) }
		if (i < 0) return emptyList()
		return clean.drop(i + 1).takeWhile { it.isNotEmpty() && !it.startsWith("Click", true) }
	}

	fun isFiller(s: SlotText) = s.cleanName.isEmpty() || s.cleanName == " "

	fun extras(slots: List<SlotText>, exclude: Set<Int>): List<Pair<String, Int>> {
		val nav = nav(slots).values.toSet()
		return slots.filter { !isFiller(it) && it.slot !in nav && it.slot !in exclude }.map { it.cleanName to it.slot }
	}

	fun nav(slots: List<SlotText>): Map<String, Int> {
		val names = listOf("Close", "Go Back", "Next Page", "Previous Page", "Scroll Left", "Scroll Right", "Re-Center")
		return slots.mapNotNull { s -> names.firstOrNull { it.equals(s.cleanName, true) }?.let { it to s.slot } }.toMap()
	}
}
