// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.hub

import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout

object SelectorTileLayout {
	const val LINE_H = 10
	const val PAD_TOP = 3
	const val GAP = 1
	const val BAR_H = 10
	const val PAD_BOTTOM = 2

	const val TEXT_X = 22

	data class Tile(val title: List<String>, val sub: List<String>, val titleY: List<Int>, val subY: List<Int>, val barY: Int, val height: Int)

	fun tile(title: String, sub: String?, tw: Int, width: (String) -> Int): Tile {
		val textW = (tw - TEXT_X - 4).coerceAtLeast(8)
		val t = BzMenuLayout.wrapText(title, textW, width)
		val s = sub?.let { BzMenuLayout.wrapText(it, textW, width) } ?: emptyList()
		val ty = t.indices.map { PAD_TOP + it * LINE_H }
		val sy = s.indices.map { PAD_TOP + (t.size + it) * LINE_H }

		val barY = maxOf(PAD_TOP + (t.size + s.size) * LINE_H, 19) + GAP
		return Tile(t, s, ty, sy, barY, barY + BAR_H + PAD_BOTTOM)
	}

	fun barLabel(players: Int, capacity: Int, barW: Int, width: (String) -> Int): String? =
		"$players/$capacity".takeIf { width(it) <= barW - 2 }
}
