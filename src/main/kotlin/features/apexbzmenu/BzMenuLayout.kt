/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): pure layout helpers for the bazaar menu and its tabs (no Minecraft state, unit tested).
 * Shared by the custom menus (header columns, empty-state text); the Bazaar pages lay out with BzPageLayout.
 */
package dev.ashtonite365.apex.features.apexbzmenu

object BzMenuLayout {

	data class ColumnSpec(val key: String, val minWidth: Int, val weight: Double = 1.0, val dropOrder: Int = 0)

	data class Column(val key: String, val x: Int, val width: Int)

	fun layoutColumns(totalWidth: Int, specs: List<ColumnSpec>, gap: Int = 2): List<Column> {
		val kept = specs.toMutableList()
		fun need() = kept.sumOf { it.minWidth } + gap * maxOf(0, kept.size - 1)
		while (need() > totalWidth) {
			val victim = kept.filter { it.dropOrder > 0 }.maxByOrNull { it.dropOrder } ?: break
			kept.remove(victim)
		}
		if (kept.isEmpty()) return listOf()
		val avail = maxOf(0, totalWidth - gap * (kept.size - 1))
		val minSum = kept.sumOf { it.minWidth }
		val widths = if (minSum >= avail) {

			kept.map { it.minWidth * avail / maxOf(1, minSum) }.toMutableList()
		} else {
			val spare = avail - minSum
			val wsum = kept.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0
			kept.map { it.minWidth + (spare * it.weight / wsum).toInt() }.toMutableList()
		}

		val used = widths.sum()
		if (used < avail && widths.isNotEmpty()) widths[0] += avail - used
		var x = 0
		return kept.mapIndexed { i, s -> Column(s.key, x, widths[i]).also { x += widths[i] + gap } }
	}

	fun wrapText(text: String, maxWidth: Int, width: (String) -> Int): List<String> {
		if (maxWidth <= 0) return listOf(text)
		val out = mutableListOf<String>()
		var line = ""
		var carry = ""
		fun lastCode(s: String): String {
			val i = s.lastIndexOf('§'); return if (i >= 0 && i + 1 < s.length) s.substring(i, i + 2) else ""
		}
		fun flush() { out += line; carry = lastCode(line).ifEmpty { carry }; line = carry }
		for (word in text.split(' ')) {
			val candidate = if (line.isEmpty() || line == carry) line + word else "$line $word"
			if (width(candidate) <= maxWidth) { line = candidate; continue }
			if (line.isNotEmpty() && line != carry) flush()
			var rest = word
			while (width(line + rest) > maxWidth && rest.length > 1) {
				var n = rest.length
				while (n > 1 && width(line + rest.take(n)) > maxWidth) n--
				line += rest.take(n); rest = rest.drop(n); flush()
			}
			line += rest
		}
		if (line.isNotEmpty() && line != carry) out += line
		return out.ifEmpty { listOf("") }
	}

	fun detailProductId(hovered: String?, selected: String?, known: (String) -> Boolean): String? =
		hovered?.takeIf(known) ?: selected?.takeIf(known)

	data class TabSlot(val row: Int, val x: Int, val width: Int)

	fun wrapTabs(widths: List<Int>, totalWidth: Int, gap: Int = 2): List<TabSlot> {
		val out = ArrayList<TabSlot>(widths.size)
		var row = 0; var cx = 0
		for (w in widths) {
			val tw = minOf(w, totalWidth)
			if (cx > 0 && cx + tw > totalWidth) { row++; cx = 0 }
			out += TabSlot(row, cx, tw)
			cx += tw + gap
		}
		return out
	}
}
