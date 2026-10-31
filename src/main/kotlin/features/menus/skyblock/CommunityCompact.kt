// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.MenuText

object CommunityCompact {
	const val GAP = 3
	const val PAD = 3
	const val LINE = 10
	const val ICON = 16

	const val SECTION_H = 12

	const val UPGRADE_MIN_W = 132

	const val MAX_LINES = 3

	const val BITS_MIN_W = 96
	const val BITS_H = PAD * 2 + 2 * LINE

	data class Card(val cell: UpgradeCell, val x: Int, val y: Int, val w: Int, val h: Int, val name: String, val lines: List<String>, val section: Boolean = false) {

		val textX get() = PAD + ICON + 3
	}
	data class Layout(val cards: List<Card>, val height: Int, val cols: Int)

	private val STATE = Regex("(?i)maxed out|cannot afford|click to|another upgrade is underway|time left|time remaining|ends in|ready in|in progress|currently upgrading")
	private val COST = Regex("(?i)^(cost|price)\\b")
	private val INFO = Regex("(?i)^(next tier|each tier|your bonus|your |current|limit|default|tier|size|total area|allowance|bonus)")

	fun merged(lore: List<String>): List<String> {
		val c = lore.map(MenuText::clean).map { it.trim() }.filter { it.isNotEmpty() }
		val out = mutableListOf<String>(); var i = 0
		while (i < c.size) {
			val l = c[i]
			val bare = (COST.containsMatchIn(l) || INFO.containsMatchIn(l)) && (l.endsWith(":") || !l.contains(':') && l.split(' ').size <= 2)
			if (bare && i + 1 < c.size && !STATE.containsMatchIn(c[i + 1]) && !COST.containsMatchIn(c[i + 1]) && !INFO.containsMatchIn(c[i + 1])) {
				out += l.removeSuffix(":") + ": " + c[i + 1]; i += 2
			} else { out += l; i++ }
		}
		return out
	}

	fun keyLines(lore: List<String>, max: Int = MAX_LINES): List<String> {
		val m = merged(lore)
		val state = m.lastOrNull { STATE.containsMatchIn(it) }
		val cost = m.firstOrNull { COST.containsMatchIn(it) }
		val info = m.filter { it != state && it != cost && INFO.containsMatchIn(it) }
		val room = max - listOfNotNull(cost, state).size
		return (info.take(maxOf(0, room)) + listOfNotNull(cost, state)).take(max)
	}

	fun upgrades(m: UpgradesModel, width: Int, measure: (String) -> Int): Layout {
		val cols = maxOf(1, (width + GAP) / (UPGRADE_MIN_W + GAP))
		val cw = (width - GAP * (cols - 1)) / cols
		val out = mutableListOf<Card>(); var y = 0
		for (r in m.rows) {
			val row = m.cells.filter { it.row == r }.sortedBy { it.col }
			row.filter { it.header }.forEach { h ->
				out += Card(h, 0, y, width, SECTION_H, MenuTheme.fit("§6§l" + MenuText.clean(h.name), width - PAD * 2, measure), emptyList(), true)
				y += SECTION_H + GAP
			}
			for (chunk in row.filter { !it.header }.chunked(cols)) {
				val laid = chunk.mapIndexed { i, c ->
					val tw = cw - PAD * 2 - ICON - 3
					val lines = keyLines(c.lore).map { MenuTheme.fit(UpgradesLayout.colour(it), tw, measure) }
					Card(c, i * (cw + GAP), y, cw, 0, MenuTheme.fit("§f" + MenuText.clean(c.name), tw, measure), lines)
				}
				val h = PAD * 2 + maxOf(ICON, (1 + (laid.maxOfOrNull { it.lines.size } ?: 0)) * LINE)
				laid.forEach { out += it.copy(h = h) }
				y += h + GAP
			}
		}
		return Layout(out, maxOf(0, y - GAP), cols)
	}

	data class BitsCard(val tile: CommunityTile, val x: Int, val y: Int, val w: Int, val h: Int, val name: String, val cost: String)

	private val AMOUNT = Regex("(?i)\\d[\\d,.]*\\s*(bits|gems|coins)\\b")

	fun bitsCost(t: CommunityTile): String {
		val m = merged(t.lore.ifEmpty { t.lines })
		m.firstNotNullOfOrNull { AMOUNT.find(it)?.value }?.let { return "§b$it" }
		if (m.any { it.contains("Click to browse", true) }) return "§eBrowse"
		return "§7" + (t.lines.firstOrNull { !it.startsWith("Click", true) } ?: "")
	}

	fun bits(tiles: List<CommunityTile>, width: Int, measure: (String) -> Int): Pair<List<BitsCard>, Int> {
		val cols = maxOf(1, (width + GAP) / (BITS_MIN_W + GAP))
		val cw = (width - GAP * (cols - 1)) / cols
		val tw = cw - PAD * 2 - ICON - 3
		val cards = tiles.mapIndexed { i, t ->
			BitsCard(t, (i % cols) * (cw + GAP), (i / cols) * (BITS_H + GAP), cw, BITS_H,
				MenuTheme.fit("§f" + t.name, tw, measure), MenuTheme.fit(bitsCost(t), tw, measure))
		}
		val rows = (tiles.size + cols - 1) / cols
		return cards to maxOf(0, rows * (BITS_H + GAP) - GAP)
	}
}
