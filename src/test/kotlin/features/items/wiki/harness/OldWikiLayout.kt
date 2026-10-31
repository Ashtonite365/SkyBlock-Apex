// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki.harness

object OldWikiLayout {
	fun interface Measure { fun width(text: String, bold: Boolean): Int }

	data class Seg(val text: String, val x: Int, val href: String? = null, val bold: Boolean = false, val italic: Boolean = false, val color: Int? = null)

	enum class Kind { HEADING, SUBHEADING, TEXT, STAT, TABLE, TABLE_HEADER, GAP }

	data class Line(val segs: List<Seg>, val y: Int, val h: Int, val kind: Kind, val block: Int, val level: Int = 0, val rule: Boolean = false)

	data class Laid(val lines: List<Line>, val height: Int, val blockY: IntArray) {
		fun yOfBlock(i: Int): Int = blockY.getOrElse(i) { 0 }

		fun lineAt(y: Int): Line? = lines.firstOrNull { y >= it.y && y < it.y + it.h }
		fun hrefAt(x: Int, y: Int, measure: Measure): String? {
			val l = lineAt(y) ?: return null
			return l.segs.firstOrNull { x >= it.x && x < it.x + measure.width(it.text, it.bold) }?.href
		}
	}

	const val LINE_H = 10

	fun wrap(spans: List<OldWikiDoc.Span>, width: Int, m: Measure, indent: Int = 0, hang: Int = indent): List<List<Seg>> {
		val out = mutableListOf<List<Seg>>()
		var cur = mutableListOf<Seg>()
		var x = indent
		val w = width.coerceAtLeast(20)
		fun newline() { out += cur; cur = mutableListOf(); x = hang }
		fun put(text: String, s: OldWikiDoc.Span) {
			val tw = m.width(text, s.bold)
			val last = cur.lastOrNull()
			if (last != null && last.href == s.href && last.bold == s.bold && last.italic == s.italic && last.color == s.color && last.x + m.width(last.text, last.bold) == x)
				cur[cur.lastIndex] = last.copy(text = last.text + text)
			else cur += Seg(text, x, s.href, s.bold, s.italic, s.color)
			x += tw
		}
		for (s in spans) {
			val tokens = Regex("\\S+|\\s+").findAll(s.text.replace('\u00A0', ' ').replace('\n', ' ')).map { it.value }
			for (t in tokens) {
				if (t.isBlank()) {
					if (x > hang && x < w) put(" ", s)
					continue
				}
				var word = t
				var tw = m.width(word, s.bold)
				if (x + tw > w && x > hang) { trimTrailingSpace(cur, m); newline() }
				while (x + tw > w && word.length > 1) {
					var n = word.length - 1
					while (n > 1 && x + m.width(word.substring(0, n), s.bold) > w) n--
					put(word.substring(0, n), s); newline()
					word = word.substring(n); tw = m.width(word, s.bold)
				}
				put(word, s)
			}
		}
		trimTrailingSpace(cur, m)
		if (cur.isNotEmpty() || out.isEmpty()) out += cur
		return out.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }
	}

	private fun trimTrailingSpace(cur: MutableList<Seg>, m: Measure) {
		val last = cur.lastOrNull() ?: return
		val t = last.text.trimEnd()
		if (t.isEmpty()) cur.removeAt(cur.lastIndex) else if (t != last.text) cur[cur.lastIndex] = last.copy(text = t)
	}

	fun layout(page: OldWikiDoc.Page, width: Int, m: Measure): Laid {
		val lines = mutableListOf<Line>()
		val blockY = IntArray(page.blocks.size)
		var y = 0
		fun add(segs: List<Seg>, kind: Kind, block: Int, h: Int = LINE_H, level: Int = 0, rule: Boolean = false) { lines += Line(segs, y, h, kind, block, level, rule); y += h }
		var prev: OldWikiDoc.Block? = null
		for ((i, b) in page.blocks.withIndex()) {
			when (b) {
				is OldWikiDoc.Block.Heading -> {
					if (y > 0) y += if (b.level <= 2) 8 else 5
					blockY[i] = y
					val kind = if (b.level <= 2) Kind.HEADING else Kind.SUBHEADING
					val rows = wrap(listOf(OldWikiDoc.Span(b.text, bold = true)), width, m)
					rows.forEachIndexed { k, r -> add(r, kind, i, level = b.level, rule = b.level <= 2 && k == rows.lastIndex) }
					if (b.level <= 2) y += 3
				}
				is OldWikiDoc.Block.Para -> {
					if (prev is OldWikiDoc.Block.Para && (prev as OldWikiDoc.Block.Para).bullet == null && b.bullet == null) y += 4
					if (prev is OldWikiDoc.Block.Table || prev is OldWikiDoc.Block.Stat) y += 3
					blockY[i] = y
					val base = b.indent * 10
					val spans = if (b.bullet != null) listOf(OldWikiDoc.Span(b.bullet + " ")) + b.spans else b.spans
					val hang = if (b.bullet != null) base + m.width(b.bullet + " ", false) else base
					wrap(spans, width, m, base, hang).forEach { add(it, Kind.TEXT, i) }
				}
				is OldWikiDoc.Block.Stat -> {
					blockY[i] = y
					val key = OldWikiDoc.Span(b.key + ": ", bold = true, color = 0xB8E0FF)
					wrap(listOf(key) + b.value, width, m, 0, 8).forEach { add(it, Kind.STAT, i) }
				}
				is OldWikiDoc.Block.Table -> {
					if (y > 0) y += 3
					blockY[i] = y
					val cols = b.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
					val colW = (width - 2) / cols
					for (row in b.rows) {
						val wrapped = row.map { c -> wrap(if (c.header) c.spans.map { it.copy(bold = true) } else c.spans, colW - 6, m) }
						val h = (wrapped.maxOfOrNull { it.size } ?: 1) * LINE_H + 2
						val segs = wrapped.flatMapIndexed { ci, rs -> rs.flatMapIndexed { ri, r -> r.map { s -> s.copy(x = ci * colW + 3 + s.x, text = s.text) to ri } } }

						val header = row.all { it.header }
						val sub = mutableListOf<Line>()
						val rowsCount = (h - 2) / LINE_H
						for (r in 0 until rowsCount) sub += Line(segs.filter { it.second == r }.map { it.first }, y + 1 + r * LINE_H, LINE_H, if (header) Kind.TABLE_HEADER else Kind.TABLE, i, cols)
						lines += Line(emptyList(), y, h, Kind.GAP, i, cols, rule = true)
						lines += sub
						y += h
					}
				}
			}
			prev = b
		}
		return Laid(lines, y, blockY)
	}
}
