// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki.harness

object OldWikiDoc {
	data class Span(val text: String, val href: String? = null, val bold: Boolean = false, val italic: Boolean = false, val color: Int? = null)
	data class Cell(val spans: List<Span>, val header: Boolean = false)

	sealed interface Block {
		data class Heading(val level: Int, val text: String, val anchor: String) : Block
		data class Para(val spans: List<Span>, val indent: Int = 0, val bullet: String? = null) : Block
		data class Stat(val key: String, val value: List<Span>) : Block
		data class Table(val rows: List<List<Cell>>) : Block
	}

	data class Page(val title: String, val blocks: List<Block>) {

		val sections: List<Pair<Block.Heading, Int>> get() = blocks.withIndex()
			.filter { (_, b) -> b is Block.Heading && b.level in 2..3 }
			.map { (i, b) -> (b as Block.Heading) to i }

		fun indexOfAnchor(anchor: String?): Int? {
			if (anchor.isNullOrBlank()) return null
			val want = anchor.replace('_', ' ').trim().lowercase()
			return blocks.indexOfFirst { it is Block.Heading && (it.anchor.replace('_', ' ').lowercase() == want || it.text.lowercase() == want) }.takeIf { it >= 0 }
		}
	}

	val colours = mapOf(
		"black" to 0x000000, "dark_blue" to 0x0000AA, "dark_green" to 0x00AA00, "dark_aqua" to 0x00AAAA,
		"dark_red" to 0xAA0000, "dark_purple" to 0xAA00AA, "gold" to 0xFFAA00, "gray" to 0xAAAAAA,
		"dark_gray" to 0x555555, "blue" to 0x5555FF, "green" to 0x55FF55, "aqua" to 0x55FFFF, "red" to 0xFF5555,
		"light_purple" to 0xFF55FF, "yellow" to 0xFFFF55, "white" to 0xFFFFFF,
	)

	class Node(val tag: String, val attrs: Map<String, String>) {
		val children = mutableListOf<Any>()
		val classes: Set<String> by lazy { attrs["class"].orEmpty().split(' ', '\t', '\n').filter { it.isNotEmpty() }.toSet() }
		fun text(): String = buildString { appendText(this@Node, this) }
		private fun appendText(n: Node, sb: StringBuilder) {
			for (c in n.children) when (c) { is String -> sb.append(c); is Node -> if (!OldWikiDoc.dropped(c)) appendText(c, sb) }
		}
		fun find(pred: (Node) -> Boolean): Node? {
			for (c in children) if (c is Node) { if (pred(c)) return c; c.find(pred)?.let { return it } }
			return null
		}
		fun findAll(pred: (Node) -> Boolean, out: MutableList<Node> = mutableListOf(), stopAtMatch: Boolean = true): List<Node> {
			for (c in children) if (c is Node) { if (pred(c)) { out += c; if (!stopAtMatch) c.findAll(pred, out, false) } else c.findAll(pred, out, stopAtMatch) }
			return out
		}
	}

	private val voidTags = setOf("br", "img", "hr", "meta", "link", "input", "wbr", "source", "col", "area", "base", "track", "embed")
	private val token = Regex("<!--[\\s\\S]*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>|[^<]+|<")
	private val attrRe = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*(?:=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?")

	fun decode(s: String): String {
		if ('&' !in s) return s
		return s.replace(Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")) { m ->
			val e = m.groupValues[1]
			when {
				e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
				e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { cp(it) } ?: m.value
				else -> when (e) {
					"amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> "\u00A0"
					"ndash" -> "–"; "mdash" -> "—"; "hellip" -> "…"; "times" -> "×"; "rarr" -> "→"; "larr" -> "←"
					"middot" -> "·"; "bull" -> "•"; "deg" -> "°"; "copy" -> "©"; "reg" -> "®"; "trade" -> "™"
					else -> m.value
				}
			}
		}
	}

	private fun cp(c: Int) = if (c in 0..0x10FFFF) String(Character.toChars(c)) else ""

	fun tree(html: String): Node {
		val root = Node("#root", emptyMap())
		val stack = ArrayList<Node>().apply { add(root) }
		for (m in token.findAll(html)) {
			val v = m.value
			if (v.startsWith("<!--")) continue
			val tag = m.groupValues[2]
			if (tag.isEmpty()) { stack.last().children += decode(v); continue }
			val name = tag.lowercase()
			if (m.groupValues[1] == "/") {
				val i = stack.indexOfLast { it.tag == name }
				if (i > 0) while (stack.size > i) stack.removeAt(stack.size - 1)
				continue
			}
			val attrs = LinkedHashMap<String, String>()
			for (a in attrRe.findAll(m.groupValues[3])) {
				val k = a.groupValues[1].lowercase()
				val value = a.groups[2]?.value ?: a.groups[3]?.value ?: a.groups[4]?.value ?: ""
				attrs.putIfAbsent(k, decode(value))
			}

			if (name == "p" || name == "li" || name == "tr" || name == "td" || name == "th") {
				val stopAt = when (name) { "li" -> setOf("ul", "ol"); "tr" -> setOf("table"); "td", "th" -> setOf("tr", "table"); else -> setOf("div", "td", "th", "li") }
				val i = stack.indexOfLast { it.tag == name || it.tag in stopAt }
				if (i > 0 && (stack[i].tag == name || (name in setOf("td", "th") && stack[i].tag in setOf("td", "th")))) while (stack.size > i) stack.removeAt(stack.size - 1)
			}
			val node = Node(name, attrs)
			stack.last().children += node
			if (name !in voidTags && !m.groupValues[3].trimEnd().endsWith("/")) stack += node
		}
		return root
	}

	private val dropTags = setOf("script", "style", "noscript", "figure", "figcaption", "img", "audio", "video", "svg", "map", "button", "input", "select", "textarea", "link", "meta")
	private val dropClasses = setOf(
		"mw-editsection", "reference", "references", "mw-references-wrap", "toc", "toclimit-2", "toclimit-3", "noprint", "metadata",
		"mw-empty-elt", "machine-metadata", "navbox", "navigation-not-searchable", "mcui", "invslot", "spriteicon",
		"gemstone-slot-list", "mw-file-description", "infobox-section-labels", "auction-house-info-icon", "error",
		"hsw-tooltip", "mw-collapsible-toggle", "printfooter", "catlinks", "mw-cite-backlink", "sprite-file",
	)

	internal fun dropped(n: Node): Boolean {
		if (n.tag in dropTags) return true
		if (n.attrs["aria-hidden"] == "true") return true
		if (n.attrs["style"]?.replace(" ", "")?.contains("display:none") == true) return true
		if (n.classes.any { it in dropClasses || it.startsWith("navbox") }) return true
		if (n.attrs["typeof"]?.startsWith("mw:File") == true) return true
		return false
	}

	private fun isPua(c: Char) = c in '\uE000'..'\uF8FF'

	fun plain(html: String): String = tree(html).text().filterNot(::isPua).replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

	private data class Style(val bold: Boolean = false, val italic: Boolean = false, val color: Int? = null, val href: String? = null)

	private fun colourOf(n: Node): Int? {
		for (c in n.classes) {
			if (c.startsWith("color-")) colours[c.removePrefix("color-")]?.let { return it }
		}
		val style = n.attrs["style"].orEmpty()
		Regex("(?:^|;)\\s*color\\s*:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})").find(style)?.let { m ->
			val h = m.groupValues[1]
			return (if (h.length == 3) h.map { "$it$it" }.joinToString("") else h).toInt(16)
		}
		return null
	}

	private class Builder {
		val out = mutableListOf<Block>()
		var spans = mutableListOf<Span>()
		var indent = 0
		var bullet: String? = null

		fun add(text: String, s: Style) {
			val t = text.filterNot(::isPua).replace('\u00A0', ' ').replace(Regex("[\\s\\u200B]+"), " ")
			if (t.isEmpty()) return
			val last = spans.lastOrNull()
			if (last != null && last.href == s.href && last.bold == s.bold && last.italic == s.italic && last.color == s.color)
				spans[spans.size - 1] = last.copy(text = last.text + t)
			else spans += Span(t, s.href, s.bold, s.italic, s.color)
		}

		fun flush() {
			val trimmed = trim(spans)
			if (trimmed.isNotEmpty()) out += Block.Para(trimmed, indent, bullet)
			spans = mutableListOf(); bullet = null
		}
	}

	internal fun trim(spans: List<Span>): List<Span> {
		val res = mutableListOf<Span>()
		var prevSpace = true
		for (s in spans) {
			val sb = StringBuilder()
			for (ch in s.text) { if (ch == ' ') { if (!prevSpace) sb.append(' '); prevSpace = true } else { sb.append(ch); prevSpace = false } }
			if (sb.isNotEmpty()) res += s.copy(text = sb.toString())
		}
		while (res.isNotEmpty()) {
			val l = res.last(); val t = l.text.trimEnd()
			if (t.isEmpty()) res.removeAt(res.size - 1) else { res[res.size - 1] = l.copy(text = t); break }
		}
		return res
	}

	fun parse(title: String, html: String): Page {
		val root = tree(html)
		val b = Builder()
		walk(root, Style(), b)
		b.flush()

		val blocks = b.out
		val kept = blocks.filterIndexed { i, blk ->
			if (blk !is Block.Heading) true
			else blocks.drop(i + 1).firstOrNull { it !is Block.Heading || it.level <= blk.level }.let { nxt -> nxt != null && nxt !is Block.Heading }
		}
		return Page(title, kept)
	}

	private fun walk(n: Node, st: Style, b: Builder) {
		for (c in n.children) {
			when (c) {
				is String -> b.add(c, st)
				is Node -> element(c, st, b)
			}
		}
	}

	private val blockTags = setOf("p", "div", "blockquote", "center", "dl", "section", "article", "aside", "pre", "main", "header", "footer", "caption")

	private fun element(n: Node, st: Style, b: Builder) {
		if (dropped(n)) return
		if ("infobox" in n.classes || "portable-infobox" in n.classes) { b.flush(); infobox(n, b); return }
		if (n.classes.any { it == "auction-house-data-placeholder" || it == "bazaar-data-placeholder" }) { b.add("(live price on the website)", st.copy(italic = true, color = 0xAAAAAA)); return }
		when (n.tag) {
			"h1", "h2", "h3", "h4", "h5", "h6" -> {
				b.flush()
				val text = n.text().filterNot(::isPua).replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
				val anchor = n.attrs["id"] ?: n.find { it.classes.contains("mw-headline") }?.attrs?.get("id") ?: text.replace(' ', '_')
				if (text.isNotEmpty()) b.out += Block.Heading(n.tag[1].digitToInt(), text, anchor)
			}
			"br" -> { val ind = b.indent; b.flush(); b.indent = ind }
			"hr" -> b.flush()
			"ul", "ol" -> {
				b.flush()
				val prevIndent = b.indent
				b.indent = prevIndent + 1
				var k = 0
				for (li in n.children) {
					if (li !is Node) continue
					if (li.tag != "li") { element(li, st, b); continue }
					if (dropped(li)) continue
					k++
					b.flush()
					b.bullet = if (n.tag == "ol") "$k." else "•"
					walk(li, st, b)
					b.flush()
				}
				b.indent = prevIndent
			}
			"li" -> { b.flush(); b.bullet = "•"; walk(n, st, b); b.flush() }
			"dd" -> { b.flush(); val p = b.indent; b.indent = p + 1; walk(n, st, b); b.flush(); b.indent = p }
			"dt" -> { b.flush(); walk(n, st.copy(bold = true), b); b.flush() }
			"table" -> { b.flush(); table(n, b) }
			"b", "strong", "th" -> walk(n, st.copy(bold = true), b)
			"i", "em", "cite", "var" -> walk(n, st.copy(italic = true), b)
			"a" -> {
				val href = n.attrs["href"]
				walk(n, st.copy(href = href ?: st.href, color = colourOf(n) ?: st.color), b)
			}
			"sup" -> { if ("reference" !in n.classes) walk(n, st, b) }
			else -> {
				val col = colourOf(n)
				val s2 = if (col != null) st.copy(color = col) else st
				if (n.tag in blockTags) { val ind = b.indent; b.flush(); b.indent = ind; walk(n, s2, b); b.flush(); b.indent = ind }
				else walk(n, s2, b)
			}
		}
	}

	private fun inline(n: Node): List<Span> {
		val b = Builder()
		fun go(x: Node, st: Style) {
			for (c in x.children) {
				when (c) {
					is String -> b.add(c, st)
					is Node -> {
						if (dropped(c)) continue
						when (c.tag) {
							"br", "li" -> { if (b.spans.isNotEmpty() && trim(b.spans).isNotEmpty()) b.add(", ", st); go(c, st) }
							"b", "strong" -> go(c, st.copy(bold = true))
							"i", "em" -> go(c, st.copy(italic = true))
							"a" -> go(c, st.copy(href = c.attrs["href"] ?: st.href, color = colourOf(c) ?: st.color))
							else -> {
								if (c.classes.any { it == "auction-house-data-placeholder" || it == "bazaar-data-placeholder" }) { b.add("(live on the website)", st.copy(italic = true, color = 0xAAAAAA)); continue }
								go(c, colourOf(c)?.let { st.copy(color = it) } ?: st)
							}
						}
					}
				}
			}
		}
		go(n, Style())
		return trim(b.spans).let { fixCommas(it) }
	}

	private fun fixCommas(spans: List<Span>): List<Span> {
		if (spans.isEmpty()) return spans
		val f = spans.first(); val l = spans.last()
		var res = spans
		if (f.text.startsWith(", ")) res = listOf(f.copy(text = f.text.removePrefix(", "))) + res.drop(1)
		if (l.text.endsWith(",")) res = res.dropLast(1) + l.copy(text = l.text.removeSuffix(","))
		return res.filter { it.text.isNotEmpty() }
	}

	private fun plainOf(spans: List<Span>) = spans.joinToString("") { it.text }.trim()

	private fun infobox(n: Node, b: Builder) {

		fun visit(x: Node) {
			for (c in x.children) {
				if (c !is Node || dropped(c)) continue
				when {
					"infobox-title" in c.classes || "pi-title" in c.classes -> {}
					"infobox-header" in c.classes || "pi-header" in c.classes -> {
						val t = plainOf(inline(c)).replace(Regex("\\s*ⓘ$"), "").trim()
						if (t.isNotEmpty()) b.out += Block.Heading(5, t, "")
					}
					"infobox-row-container" in c.classes || "pi-data" in c.classes -> {
						val label = c.find { "infobox-row-label" in it.classes || "pi-data-label" in it.classes }
						val value = c.find { "infobox-row-value" in it.classes || "pi-data-value" in it.classes }
						val v = value?.let(::inline).orEmpty()
						val k = label?.let { plainOf(inline(it)) }.orEmpty()
						if (v.isNotEmpty() || k.isNotEmpty()) {
							if (k.isEmpty()) b.out += Block.Para(v) else b.out += Block.Stat(k, v)
						}
					}
					c.tag == "tr" -> {
						val th = c.children.filterIsInstance<Node>().firstOrNull { it.tag == "th" && !dropped(it) }
						val tds = c.children.filterIsInstance<Node>().filter { it.tag == "td" && !dropped(it) }
						when {
							th != null && tds.isNotEmpty() -> {
								val k = plainOf(inline(th)); val v = tds.flatMap { inline(it) }
								if (k.isNotEmpty() || v.isNotEmpty()) b.out += Block.Stat(k, v)
							}
							th != null -> { val t = plainOf(inline(th)); if (t.isNotEmpty()) b.out += Block.Heading(5, t, "") }
							tds.isNotEmpty() -> { val v = tds.flatMap { inline(it) }; if (v.isNotEmpty()) b.out += Block.Para(v) }
						}
					}
					else -> visit(c)
				}
			}
		}
		visit(n)
	}

	private fun table(n: Node, b: Builder) {
		val rows = mutableListOf<List<Cell>>()
		fun collect(x: Node) {
			for (c in x.children) {
				if (c !is Node || dropped(c)) continue
				when (c.tag) {
					"tr" -> {
						val cells = c.children.filterIsInstance<Node>().filter { (it.tag == "td" || it.tag == "th") && !dropped(it) }.map { Cell(inline(it), it.tag == "th") }
						if (cells.any { it.spans.isNotEmpty() }) rows += cells
					}
					"table" -> {}
					else -> collect(c)
				}
			}
		}
		collect(n)
		if (rows.isEmpty()) return
		val cols = rows.maxOf { it.size }
		if (cols <= 1) { for (r in rows) r.firstOrNull()?.let { if (it.spans.isNotEmpty()) b.out += Block.Para(it.spans) }; return }
		b.out += Block.Table(rows)
	}
}
