// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.hunting

import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

data class CycleOption(val name: String, val slot: Int, val options: List<String>, val selected: String?)

data class AttributeInfo(val name: String, val category: String, val effect: String)

object HuntingLore {
	private val ATTR = Regex("^(.+?) \\((Combat|Fishing|Farming|Foraging|Mining|Taming|Enchanting|Alchemy|Carpentry|Hunting|Social|Runecrafting|[A-Z][a-z]+)\\)$")
	private val FAMILY = Regex("^(.+ Family)$")
	private val OWNED = Regex("^Owned: ([\\d,]+) Shards?", RegexOption.IGNORE_CASE)
	private val NEXT = Regex("Syphon ([\\d,]+) more to level up", RegexOption.IGNORE_CASE)
	private val SELECTED = Regex("^▶\\s*(.+)$")

	fun cycle(s: SlotText): CycleOption? {
		val lore = s.cleanLore
		if (lore.none { SELECTED.matches(it) }) return null
		val opts = lore.filter { it.isNotEmpty() && !it.startsWith("Click", true) && !it.startsWith("Right-Click", true) }
			.map { it.removePrefix("▶").trim() }
		return CycleOption(s.cleanName, s.slot, opts, lore.firstNotNullOfOrNull { SELECTED.matchEntire(it)?.groupValues?.get(1)?.trim() })
	}

	fun attribute(lore: List<String>): AttributeInfo? {
		val i = lore.indexOfFirst { ATTR.matches(it) }
		if (i < 0) return null
		val m = ATTR.matchEntire(lore[i])!!
		return AttributeInfo(m.groupValues[1], m.groupValues[2], lore.drop(i + 1).takeWhile { it.isNotEmpty() }.joinToString(" "))
	}

	fun family(lore: List<String>) = lore.firstNotNullOfOrNull { FAMILY.matchEntire(it)?.groupValues?.get(1) }
	fun owned(lore: List<String>) = lore.firstNotNullOfOrNull { OWNED.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
	fun toNextLevel(lore: List<String>) = lore.firstNotNullOfOrNull { NEXT.find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
	fun maxed(lore: List<String>) = lore.any { it.contains("maxed", true) && !it.contains("not maxed", true) || it.contains("MAXED") }

	fun counter(lore: List<String>, label: String): String? =
		lore.firstNotNullOfOrNull { Regex("^${Regex.escape(label)}: (.+)$", RegexOption.IGNORE_CASE).matchEntire(it)?.groupValues?.get(1) }

	fun splitLevel(name: String): Pair<String, Int?> {
		val m = Regex("^(.+?) ([IVX]+|\\d+)$").matchEntire(MenuText.clean(name)) ?: return MenuText.clean(name) to null
		val lvl = dev.ashtonite365.apex.features.menus.skills.SkillLore.level(m.groupValues[2]) ?: return MenuText.clean(name) to null
		return m.groupValues[1] to lvl
	}
}
