// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.wardrobe

import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.util.ApexFormatters

object WardrobeValue {

	data class Piece(val name: String, val value: Double?)

	fun lines(setNumber: Int, pieces: List<Piece>, fmt: (Double) -> String = { ApexFormatters.compactNumber(it) }): List<String> {
		if (pieces.isEmpty()) return listOf("§6Set $setNumber value", "§8No armour in this set")
		val known = pieces.mapNotNull { it.value }
		val total = known.sum()
		val head = when {
			known.isEmpty() -> "§6Set $setNumber value: §cno live price"
			known.size < pieces.size -> "§6Set $setNumber value: §e${fmt(total)} coins §8(partial: ${pieces.size - known.size} without a live price)"
			else -> "§6Set $setNumber value: §e${fmt(total)} coins"
		}
		return listOf(head) + pieces.map { p -> "§7${p.name}: " + (p.value?.let { "§e${fmt(it)}" } ?: "§cno live price") }
	}

	fun wrap(lines: List<String>, maxWidth: Int, width: (String) -> Int): List<String> = lines.flatMap { l ->
		val st = Regex("^(?:§.)+").find(l)?.value ?: ""
		EnchantLabel.wrap(l, maxWidth, width).mapIndexed { i, s -> if (i == 0) s else st + s }
	}
}
