// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import dev.ashtonite365.apex.features.menus.collections.LoreParse
import dev.ashtonite365.apex.util.IntUtil.toRomanNumeral

object EnchantLabel {
	data class Label(
		val name: String, val level: Int?, val ultimate: Boolean = false,

		val rarity: String? = null,
	) {
		val text: String get() = if (level == null) name else "$name ${roman(level)}"
	}

	data class LevelOption(val label: Label, val fullCost: Int?, val cost: Int?, val affordable: Boolean?)

	fun roman(n: Int): String = n.toRomanNumeral()

	private val RARITY_COLOUR = linkedMapOf(
		"COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6", "MYTHIC" to "§d",
		"DIVINE" to "§b", "SPECIAL" to "§c", "VERY SPECIAL" to "§c")

	const val ULTIMATE_STYLE = "§d§l"

	fun titleCase(s: String) = s.split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }

	fun fromId(id: String): Label? {
		val t = id.trim().uppercase()
		val (base, lvl) = when {
			t.startsWith("ENCHANTMENT_") -> t.removePrefix("ENCHANTMENT_").let { r ->
				val n = r.substringAfterLast('_').toIntOrNull()
				(if (n != null) r.substringBeforeLast('_') else r) to n
			}
			';' in t -> t.substringBefore(';') to t.substringAfter(';').toIntOrNull()
			else -> return null
		}
		if (base.isEmpty()) return null
		return Label(titleCase(base), lvl, base.startsWith("ULTIMATE_"))
	}

	fun fromText(text: String, ultimate: Boolean = false, rarity: String? = null): Label? {
		val (base, lvl) = LoreParse.splitTier(text)
		if (lvl == null || base.isBlank()) return null
		return Label(base, lvl, ultimate || MenuText.clean(text).startsWith("Ultimate ", true), rarity)
	}

	fun fromLore(lore: List<String>): Label? {
		val clean = lore.map(MenuText::clean)
		val ultimate = clean.any { it.contains("only have 1 Ultimate", true) } || lore.any { it.trimStart().startsWith(ULTIMATE_STYLE) }
		val rarity = clean.lastOrNull { l -> RARITY_COLOUR.keys.any { l.equals(it, true) || l.startsWith("$it ", true) } }
			?.let { l -> RARITY_COLOUR.keys.filter { l.startsWith(it, true) }.maxByOrNull { it.length } }

		val line = clean.firstOrNull { l -> l.isNotBlank() && !l.contains(':') && LoreParse.roman(l.substringAfterLast(' ', "")) != null && fromText(l) != null }
			?: return null
		return fromText(line, ultimate, rarity)
	}

	fun bazaarLabel(id: String, name: String, lore: List<String> = emptyList()): Label? {
		val book = name.trim().equals("Enchanted Book", true) || id.trim().uppercase().startsWith("ENCHANTMENT_")
		if (!book) return null
		val byId = fromId(id)
		val byLore = fromLore(lore) ?: return byId

		return if (byId?.ultimate == true && !byLore.ultimate) byLore.copy(ultimate = true) else byLore
	}

	fun bazaarName(id: String, name: String, lore: List<String> = emptyList()) = bazaarLabel(id, name, lore)?.text ?: name

	fun style(l: Label, fallback: String = "§9") = if (l.ultimate) ULTIMATE_STYLE else l.rarity?.let { RARITY_COLOUR[it] } ?: fallback

	fun wrapStyled(l: Label, maxWidth: Int, width: (String) -> Int, fallback: String = "§9"): List<String> {
		val st = style(l, fallback)
		return wrap(l.text, maxWidth) { width(st + it) }.map { st + it }
	}

	fun wrap(text: String, maxWidth: Int, width: (String) -> Int): List<String> {
		val out = mutableListOf<String>(); var cur = ""
		for (w in text.split(' ').filter { it.isNotEmpty() }) {
			val n = if (cur.isEmpty()) w else "$cur $w"
			if (cur.isNotEmpty() && width(n) > maxWidth) { out += cur; cur = w } else cur = n
		}
		if (cur.isNotEmpty() || out.isEmpty()) out += cur
		return out
	}

	private val COST = Regex("^(\\d[\\d,]*)(?:\\s+(\\d[\\d,]*))?\\s+Exp Levels?\\s*([✔✖])?", RegexOption.IGNORE_CASE)

	fun levelOption(name: String, lore: List<String>): LevelOption? {
		val label = fromText(name) ?: return null
		val clean = lore.map(MenuText::clean)
		val at = clean.indexOfFirst { it.equals("Cost", true) }
		val m = (if (at >= 0) clean.drop(at + 1) else clean).firstNotNullOfOrNull { COST.find(it) }
		fun n(s: String?) = s?.takeIf { it.isNotEmpty() }?.replace(",", "")?.toIntOrNull()
		val a = n(m?.groupValues?.get(1)); val b = n(m?.groupValues?.get(2))
		val tick = m?.groupValues?.get(3)?.takeIf { it.isNotEmpty() }
		return LevelOption(label, a, b ?: a, tick?.let { it == "✔" })
	}

	fun levelText(o: LevelOption): String = o.label.text + when {
		o.cost == null -> ""
		o.fullCost != null && o.fullCost != o.cost -> " · ${o.fullCost} → ${o.cost} Exp Levels"
		else -> " · ${o.cost} Exp Levels"
	}
}
