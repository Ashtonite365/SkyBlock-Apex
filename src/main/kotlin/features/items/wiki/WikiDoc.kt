// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

object WikiDoc {

	data class Tip(val title: String, val lines: List<String>)

	data class Img(
		val src: String, val w: Int, val h: Int, val tip: Tip? = null, val slot: Boolean = false,
		val frames: List<Img> = emptyList(), val count: String? = null, val main: Boolean = false,

		val href: String? = null,
	)

	data class Live(val kind: String, val id: String, val buy: Boolean = true, val average: Boolean = false, val suffix: String = " coins", val entries: List<Pair<String, Double>> = emptyList())

	data class Span(
		val text: String, val href: String? = null, val bold: Boolean = false, val italic: Boolean = false, val color: Int? = null,
		val game: Boolean = false, val code: Boolean = false, val img: Img? = null, val live: Live? = null, val title: String? = null,
	)

	enum class Align { LEFT, CENTER, RIGHT }

	data class Cell(val blocks: List<Block>, val header: Boolean = false, val colspan: Int = 1, val rowspan: Int = 1, val align: Align = Align.LEFT, val bg: Int? = null) {

		val text: String get() = blocks.joinToString(" ") { plainOfBlock(it) }.trim()
	}

	sealed interface Item {
		data class Header(val spans: List<Span>) : Item

		data class Row(val label: List<Span>, val value: List<Block>) : Item

		data class Grid(val cols: Int, val cells: List<Row>) : Item

		data class Tabs(val id: Int, val labels: List<String>, val sections: List<List<Item>>, val active: Int) : Item
	}

	data class NavRow(val group: List<Span>?, val items: List<List<Span>>, val depth: Int = 0)
	data class NavSection(val id: Int, val header: List<Span>?, val collapsed: Boolean, val rows: List<NavRow>)

	sealed interface Block {
		data class Heading(val level: Int, val text: String, val anchor: String) : Block
		data class Para(val spans: List<Span>, val indent: Int = 0, val bullet: String? = null, val align: Align = Align.LEFT, val italic: Boolean = false) : Block
		data class Infobox(val title: String, val items: List<Item>) : Block
		data class Table(val rows: List<List<Cell>>, val bordered: Boolean = true, val sortable: Boolean = false) : Block {
			val cols: Int get() = rows.maxOfOrNull { r -> r.sumOf { it.colspan } } ?: 0
		}

		data class Grid(val kind: String, val rows: List<List<Img?>>, val output: Img?, val align: Align = Align.LEFT) : Block
		data class Figure(val img: Img, val caption: List<Span>, val align: Align = Align.RIGHT) : Block
		data class Navbox(val id: Int, val title: List<Span>, val sections: List<NavSection>, val collapsed: Boolean = false, val image: Img? = null) : Block
		data class Toc(val entries: List<Heading>) : Block
		data object Rule : Block
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

		fun images(): List<Img> = buildList { for (b in blocks) collectImages(b, this) }

		fun chars(): String = buildString { for (b in blocks) appendChars(b, this) }
	}

	val colours = mapOf(
		"black" to 0x000000, "dark_blue" to 0x353FCE, "dark_green" to 0x00AA00, "dark_aqua" to 0x00AAAA,
		"dark_red" to 0xD13228, "dark_purple" to 0xA335EE, "gold" to 0xFF9000, "gray" to 0xA8BFD2,
		"dark_gray" to 0x707592, "blue" to 0x459BFF, "green" to 0x55FF55, "aqua" to 0x55FFFF, "red" to 0xFF5555,
		"light_purple" to 0xFF55FF, "yellow" to 0xFFDE2F, "white" to 0xFFFFFF,
	)
	private val codeNames = listOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white")

	fun codeColour(c: Char): Int? = "0123456789abcdef".indexOf(c.lowercaseChar()).takeIf { it >= 0 }?.let { colours[codeNames[it]] }

	internal fun plainOfBlock(b: Block): String = when (b) {
		is Block.Para -> b.spans.joinToString("") { it.text }
		is Block.Heading -> b.text
		is Block.Table -> b.rows.joinToString(" ") { r -> r.joinToString(" ") { it.text } }
		is Block.Infobox -> b.title
		is Block.Figure -> b.caption.joinToString("") { it.text }
		else -> ""
	}

	private fun collectImages(b: Block, out: MutableList<Img>) {
		fun spans(s: List<Span>) { for (x in s) x.img?.let { out += it; out += it.frames } }
		fun items(items: List<Item>) {
			for (it in items) when (it) {
				is Item.Header -> spans(it.spans)
				is Item.Row -> { spans(it.label); it.value.forEach { v -> collectImages(v, out) } }
				is Item.Grid -> items(it.cells)
				is Item.Tabs -> it.sections.forEach(::items)
			}
		}
		when (b) {
			is Block.Para -> spans(b.spans)
			is Block.Infobox -> items(b.items)
			is Block.Table -> b.rows.forEach { r -> r.forEach { c -> c.blocks.forEach { collectImages(it, out) } } }
			is Block.Grid -> { b.rows.forEach { r -> r.forEach { s -> s?.let { out += it; out += it.frames } } }; b.output?.let { out += it; out += it.frames } }
			is Block.Figure -> out += b.img
			is Block.Navbox -> { b.image?.let { out += it }; b.sections.forEach { s -> s.rows.forEach { r -> r.items.forEach(::spans) } } }
			else -> {}
		}
	}

	private fun appendChars(b: Block, sb: StringBuilder) {
		fun spans(s: List<Span>?) { s?.forEach { sb.append(it.text) } }
		fun items(items: List<Item>) {
			for (it in items) when (it) {
				is Item.Header -> spans(it.spans)
				is Item.Row -> { spans(it.label); it.value.forEach { v -> appendChars(v, sb) } }
				is Item.Grid -> items(it.cells)
				is Item.Tabs -> { it.labels.forEach(sb::append); it.sections.forEach(::items) }
			}
		}
		when (b) {
			is Block.Heading -> sb.append(b.text)
			is Block.Para -> spans(b.spans)
			is Block.Infobox -> { sb.append(b.title); items(b.items) }
			is Block.Table -> b.rows.forEach { r -> r.forEach { c -> c.blocks.forEach { appendChars(it, sb) } } }
			is Block.Grid -> b.rows.forEach { r -> r.forEach { s -> s?.count?.let(sb::append) } }
			is Block.Figure -> spans(b.caption)
			is Block.Navbox -> { spans(b.title); b.sections.forEach { s -> spans(s.header); s.rows.forEach { r -> spans(r.group); r.items.forEach(::spans) } } }
			is Block.Toc -> b.entries.forEach { sb.append(it.text) }
			Block.Rule -> {}
		}
	}

	class Node(val tag: String, val attrs: Map<String, String>) {
		val children = mutableListOf<Any>()
		val classes: Set<String> by lazy { attrs["class"].orEmpty().split(' ', '\t', '\n').filter { it.isNotEmpty() }.toSet() }
		fun text(): String = buildString { appendText(this@Node, this) }
		private fun appendText(n: Node, sb: StringBuilder) {
			for (c in n.children) when (c) { is String -> sb.append(c); is Node -> if (!WikiDoc.dropped(c)) appendText(c, sb) }
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

	private val dropTags = setOf("script", "style", "noscript", "audio", "video", "svg", "map", "button", "input", "select", "textarea", "link", "meta")
	private val dropClasses = setOf(
		"mw-editsection", "reference", "references", "mw-references-wrap", "toc", "toclimit-2", "toclimit-3", "noprint", "metadata",
		"mw-empty-elt", "machine-metadata", "table-vedbuttons", "jcConfig", "infobox-section-labels", "auction-house-info-icon", "bazaar-info-icon",
		"error", "hsw-tooltip", "mw-collapsible-toggle", "printfooter", "catlinks", "mw-cite-backlink", "navbox-vde", "navbox-gutter",
		"navbox-image", "mcui-bubbling", "mcui-paths", "mcui-arrow", "mw-jump-link", "sortkey",
	)

	internal fun dropped(n: Node): Boolean {
		if (n.tag in dropTags) return true
		if (n.attrs["aria-hidden"] == "true") return true
		if (n.attrs["style"]?.replace(" ", "")?.contains("display:none") == true) return true
		if (n.classes.any { it in dropClasses }) return true
		return false
	}

	private fun isPua(c: Char) = c in '\uE000'..'\uF8FF'

	internal val puaSymbols = mapOf(
		'\uE050' to "❁", '\uE00D' to "❁", '\uE00B' to "⫽", '\uE003' to "✎", '\uE001' to "⚔",
		'\uE01A' to "✯", '\uE085' to "☠", '\uE067' to "⏣",
	)

	internal fun mapPua(text: String): String {
		if (text.none(::isPua)) return text
		val sb = StringBuilder(text.length)
		for (c in text) if (!isPua(c)) sb.append(c) else puaSymbols[c]?.let(sb::append)
		return sb.toString()
	}

	fun plain(html: String): String = tree(html).text().filterNot(::isPua).replace('\u00A0', ' ').replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()

	private fun plainText(n: Node) = n.text().filterNot(::isPua).replace('\u00A0', ' ').replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()

	private data class Style(
		val bold: Boolean = false, val italic: Boolean = false, val color: Int? = null, val href: String? = null,
		val game: Boolean = false, val code: Boolean = false, val title: String? = null,
	)

	private val hexColour = Regex("(?:^|;)\\s*color\\s*:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b")
	private val hexBg = Regex("(?:^|;)\\s*background(?:-color)?\\s*:\\s*#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b")

	private fun hex(h: String) = (if (h.length == 3) h.map { "$it$it" }.joinToString("") else h).toInt(16)

	private fun colourOf(n: Node): Int? {
		for (c in n.classes) {
			if (c.startsWith("color-")) colours[c.removePrefix("color-")]?.let { return it }
			if (c.startsWith("format-") && c.length == 8) codeColour(c[7])?.let { return it }
		}
		hexColour.find(n.attrs["style"].orEmpty())?.let { return hex(it.groupValues[1]) }
		return null
	}

	private fun bgOf(n: Node): Int? = hexBg.find(n.attrs["style"].orEmpty())?.let { hex(it.groupValues[1]) }

	private fun alignOf(n: Node, def: Align): Align {
		val st = n.attrs["style"].orEmpty().replace(" ", "").lowercase()
		val cl = n.classes
		return when {
			"text-align:center" in st || n.attrs["align"] == "center" || n.tag == "center" -> Align.CENTER
			"text-align:right" in st || n.attrs["align"] == "right" -> Align.RIGHT
			"text-align:left" in st || n.attrs["align"] == "left" -> Align.LEFT
			cl.any { it == "centertext" || it == "ct" || it == "centertxt" || it == "tc" || it == "center" } -> Align.CENTER
			else -> def
		}
	}

	private class Ctx { var nav = 0; var tabs = 0; var mainImage = false; var inInfobox = false }

	private class Builder(val ctx: Ctx) {
		val out = mutableListOf<Block>()
		var spans = mutableListOf<Span>()
		var indent = 0
		var bullet: String? = null

		var plain = false

		var tree = -1
		var align = Align.LEFT

		fun add(text: String, s: Style) {
			var t = mapPua(text).replace('\u00A0', ' ').replace(Regex("[\\s\\u200B]+"), " ")
			val last = spans.lastOrNull()

			if (t.startsWith(' ') && last != null && last.img == null && last.text.endsWith(' ')) t = t.substring(1)
			if (t.isEmpty()) return
			if (last != null && last.img == null && last.live == null && last.href == s.href && last.bold == s.bold && last.italic == s.italic &&
				last.color == s.color && last.game == s.game && last.code == s.code && last.title == s.title)
				spans[spans.size - 1] = last.copy(text = last.text + t)
			else spans += Span(t, s.href, s.bold, s.italic, s.color, s.game, s.code, title = s.title)
		}

		fun image(img: Img, s: Style) { spans += Span("", s.href, img = img, title = s.title) }

		fun flush() {
			val trimmed = tidy(trim(spans))
			if (trimmed.isNotEmpty()) out += Block.Para(trimmed, indent, bullet, align)
			spans = mutableListOf(); bullet = null
		}

		fun block(b: Block) { val ind = indent; flush(); indent = ind; out += b }
	}

	internal fun trim(spans: List<Span>): List<Span> {
		val res = mutableListOf<Span>()
		var prevSpace = true
		for (s in spans) {
			if (s.img != null) { res += s; prevSpace = false; continue }
			val sb = StringBuilder()
			for (ch in s.text) {
				if (ch == '\n') { while (sb.endsWith(' ')) sb.setLength(sb.length - 1); sb.append(ch); prevSpace = true }
				else if (ch == ' ') { if (!prevSpace) sb.append(' '); prevSpace = true } else { sb.append(ch); prevSpace = false }
			}
			if (sb.isNotEmpty()) res += s.copy(text = sb.toString())
		}
		while (res.isNotEmpty()) {
			val l = res.last()
			if (l.img != null) break
			val t = l.text.trimEnd()
			if (t.isEmpty()) res.removeAt(res.size - 1) else { res[res.size - 1] = l.copy(text = t); break }
		}
		while (res.isNotEmpty() && res.first().img == null && res.first().text.isBlank()) res.removeAt(0)
		return res
	}

	internal fun tidy(spans: List<Span>): List<Span> {
		if (spans.isEmpty()) return spans
		val res = ArrayList<Span>(spans.size)
		var prevEndsComma = false
		for (s in spans) {
			if (s.img != null) { res += s; prevEndsComma = false; continue }
			var t = s.text.replace(Regex(",(\\s*,)+"), ",").replace(Regex(" +,"), ",")
			if (prevEndsComma) t = t.replace(Regex("^\\s*,"), "")
			if (res.isEmpty() || res.all { it.img == null && it.text.isBlank() }) t = t.replace(Regex("^\\s*,\\s*"), "")
			if (t.isEmpty()) continue
			res += s.copy(text = t)
			prevEndsComma = t.trimEnd().endsWith(",")
		}
		return res
	}

	fun parse(title: String, html: String): Page {
		val root = tree(html)
		val b = Builder(Ctx())
		walk(root, Style(), b)
		b.flush()
		val blocks = b.out
		val kept = blocks.filterIndexed { i, blk ->
			if (blk !is Block.Heading) true
			else blocks.drop(i + 1).firstOrNull { it !is Block.Heading || it.level <= blk.level }.let { nxt -> nxt != null && nxt !is Block.Heading }
		}.toMutableList()
		val heads = kept.filterIsInstance<Block.Heading>().filter { it.level in 2..3 }
		if (heads.size >= 4) {
			val at = kept.indexOfFirst { it is Block.Heading }
			if (at >= 0) kept.add(at, Block.Toc(heads))
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

	const val TREE_MID = "├"
	const val TREE_LAST = "└"

	private val noMarker = Regex("list-style(-type)?\\s*:\\s*none")
	private val zeroMargin = Regex("margin-left\\s*:\\s*0(?![.0-9])")

	private val blockTags = setOf("p", "div", "blockquote", "center", "dl", "section", "article", "aside", "main", "header", "footer", "caption", "figcaption")

	private fun statIcon(n: Node): Boolean {
		if (n.attrs["aria-hidden"] != "true" || n.tag != "span") return false
		val t = n.text()
		return t.any { puaSymbols.containsKey(it) } && t.all { isPua(it) || it.isWhitespace() || it == '\u00A0' }
	}

	private fun element(n: Node, st: Style, b: Builder) {
		if (statIcon(n)) { walk(n, st, b); return }
		if (dropped(n)) return
		val cl = n.classes
		when {
			"infobox" in cl || "portable-infobox" in cl -> { b.block(infobox(n, b.ctx)); return }
			n.tag == "table" && "navbox" in cl -> { navbox(n, b.ctx)?.let(b::block); return }
			"mcui" in cl -> { grid(n, b.align)?.let(b::block); return }
			"invslot" in cl -> { slot(n)?.let { b.image(it, st) }; return }
			"gemstone-slot" in cl -> { gemSlot(n, st, b); return }
			"bazaar-data-placeholder" in cl || "auction-house-data-placeholder" in cl -> { live(n, st, b); return }
			"hatnote" in cl || "dablink" in cl -> {
				val ind = b.indent; b.flush(); b.indent = ind + 1; walk(n, st.copy(italic = true), b); b.flush(); b.indent = ind; return
			}
			"hlist" in cl -> { hlist(n, st, b); return }
			"treeview" in cl && b.tree < 0 -> { b.tree = 0; try { walk(n, st, b) } finally { b.tree = -1 }; return }
			"plainlist" in cl && n.tag != "ul" && n.tag != "ol" && !b.plain -> { b.plain = true; try { element(n, st, b) } finally { b.plain = false }; return }
			n.tag == "figure" || "thumb" in cl -> { figure(n, b)?.let(b::block); return }
			"hsw-gamefont" in cl || "mcui-text" in cl || "minecraft-font" in cl -> { walk(n, (colourOf(n)?.let { st.copy(color = it) } ?: st).copy(game = true), b); return }
		}
		when (n.tag) {
			"h1", "h2", "h3", "h4", "h5", "h6" -> {
				b.flush()
				val text = plainText(n)
				val anchor = n.attrs["id"] ?: n.find { it.classes.contains("mw-headline") }?.attrs?.get("id") ?: text.replace(' ', '_')
				if (text.isNotEmpty()) b.out += Block.Heading(n.tag[1].digitToInt(), text, anchor)
			}
			"img" -> imgOf(n, st)?.let { b.image(it, st) }
			"br" -> { val ind = b.indent; val bl = b.bullet; b.flush(); b.indent = ind; if (bl != null) b.bullet = "" }
			"hr" -> b.block(Block.Rule)
			"ul", "ol" -> {
				b.flush()
				val prevIndent = b.indent
				val style = n.attrs["style"].orEmpty()
				val plain = b.plain || "plainlist" in cl || "plainlinks-list" in cl || noMarker.containsMatchIn(style)

				val flush = b.plain || "plainlist" in cl || (plain && zeroMargin.containsMatchIn(style))
				b.indent = if (flush) prevIndent else prevIndent + 1
				val prevPlain = b.plain
				if ("plainlist" in cl) b.plain = true
				val treeDepth = b.tree
				if (treeDepth >= 0) { b.indent = if (treeDepth == 0) prevIndent else prevIndent + 1; b.tree = treeDepth + 1 }
				val lis = n.children.filterIsInstance<Node>().filter { it.tag == "li" && !dropped(it) }
				var k = 0
				for (li in n.children) {
					if (li !is Node) continue
					if (li.tag != "li") { element(li, st, b); continue }
					if (dropped(li)) continue
					k++
					b.flush()
					b.bullet = when {
						treeDepth == 0 -> null
						treeDepth > 0 -> if (li === lis.last()) TREE_LAST else TREE_MID
						plain -> null
						n.tag == "ol" -> "$k."
						else -> "•"
					}
					walk(li, st, b)
					b.flush()
				}
				b.indent = prevIndent
				b.plain = prevPlain
				b.tree = treeDepth
			}
			"li" -> { b.flush(); b.bullet = "•"; walk(n, st, b); b.flush() }
			"dd" -> { b.flush(); val p = b.indent; b.indent = p + 1; walk(n, st, b); b.flush(); b.indent = p }
			"dt" -> { b.flush(); walk(n, st.copy(bold = true), b); b.flush() }
			"table" -> { val al = b.align; when (val t = table(n, b.ctx, st)) { is Block.Table -> b.block(t); is Flat -> { b.flush(); t.blocks.forEach(b::block) } }; b.align = al }
			"b", "strong" -> walk(n, st.copy(bold = true), b)
			"i", "em", "cite", "var" -> walk(n, st.copy(italic = true), b)
			"code", "kbd", "samp", "tt" -> walk(n, st.copy(code = true), b)
			"pre" -> { b.flush(); walk(n, st.copy(code = true), b); b.flush() }
			"a" -> {
				val href = n.attrs["href"]
				val s2 = st.copy(href = href ?: st.href, color = colourOf(n) ?: st.color, bold = st.bold || "mw-selflink" in cl)
				walk(n, s2, b)
			}
			"abbr" -> walk(n, st.copy(title = n.attrs["title"] ?: st.title), b)
			"sup", "sub" -> walk(n, st, b)
			else -> {
				val col = colourOf(n)
				var s2 = if (col != null) st.copy(color = col) else st
				if ("format-l" in cl) s2 = s2.copy(bold = true)
				if ("format-o" in cl) s2 = s2.copy(italic = true)
				if (n.tag in blockTags) {
					val ind = b.indent; val al = b.align
					b.flush(); b.indent = ind; b.align = alignOf(n, al)
					walk(n, s2, b); b.flush(); b.indent = ind; b.align = al
				} else walk(n, s2, b)
			}
		}
	}

	private val gemSymbols = linkedMapOf(
		"Ruby" to "❤", "Amethyst" to "❈", "Jade" to "☘", "Sapphire" to "✎", "Amber" to "⸕", "Topaz" to "✧", "Jasper" to "❁",
		"Opal" to "❂", "Onyx" to "☠", "Aquamarine" to "α", "Citrine" to "☘", "Peridot" to "☘", "Combat" to "⚔", "Offensive" to "☠",
		"Defensive" to "☤", "Mining" to "✦", "Universal" to "❂", "Chisel" to "❂",
	)

	private fun gemSlot(n: Node, st: Style, b: Builder) {
		val tip = tipOf(n)
		val name = tip?.title?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").orEmpty()
		val sym = n.text().filterNot(::isPua).trim().ifEmpty { gemSymbols.entries.firstOrNull { it.key in name }?.value ?: "◆" }
		val title = tip?.let { (listOf(it.title) + it.lines).joinToString("\n") }
		b.spans += Span("[$sym]", st.href, st.bold, st.italic, colourOf(n) ?: st.color, game = true, title = title)
		b.add(" ", st)
	}

	private fun hlist(n: Node, st: Style, b: Builder) {
		val items = n.findAll({ it.tag == "li" })
		if (items.isEmpty()) { walk(n, st, b); return }
		items.forEachIndexed { i, li ->
			if (dropped(li)) return@forEachIndexed
			if (i > 0) b.add(" • ", st.copy(href = null, bold = true))
			walk(li, st, b)
		}
	}

	private fun absolutise(src: String): String = when {
		src.startsWith("//") -> "https:$src"
		src.startsWith("/") -> "https://${WikiHosts.HOST}$src"
		else -> src
	}

	private fun imgOf(n: Node, st: Style? = null): Img? {
		val src0 = n.attrs["src"] ?: n.attrs["data-src"] ?: return null
		if (src0.startsWith("data:")) return null
		val w = n.attrs["width"]?.toIntOrNull() ?: n.attrs["data-file-width"]?.toIntOrNull() ?: 16
		val h = n.attrs["height"]?.toIntOrNull() ?: n.attrs["data-file-height"]?.toIntOrNull() ?: w
		if (w < 2 || h < 2) return null

		val x2 = n.attrs["srcset"]?.split(',')?.map { it.trim() }?.firstOrNull { it.endsWith(" 2x") }?.removeSuffix(" 2x")?.trim()
		val src = if (x2 != null && w <= 96) x2 else src0
		if (src.substringBefore('?').endsWith(".svg", true)) return null
		return Img(absolutise(src), w, h)
	}

	private fun tipOf(n: Node): Tip? {
		val title = n.attrs["data-minetip-title"] ?: return null
		val text = n.attrs["data-minetip-text"].orEmpty()
		fun mc(s: String) = s.replace("&", "§").replace("\\/", "/")
		val lines = if (text.isEmpty()) emptyList() else text.split('/').map { mc(it) }
		return Tip(mc(title), lines)
	}

	private fun slot(n: Node): Img? {
		val items = n.findAll({ "invslot-item" in it.classes })
		val frames = items.mapNotNull { it ->
			val img = it.find { c -> c.tag == "img" }?.let { imgOf(it) } ?: return@mapNotNull null
			val count = it.find { c -> "invslot-stacksize" in c.classes }?.let(::plainText)?.takeIf { s -> s.isNotEmpty() }
			val link = it.find { c -> c.tag == "a" }?.attrs?.get("href")
			img.copy(slot = true, tip = tipOf(it), count = count, href = link) to ("animated-active" in it.classes)
		}
		if (frames.isEmpty()) return null
		val first = frames.firstOrNull { it.second } ?: frames.first()
		val rest = frames.filter { it !== first }.map { it.first }
		return first.first.copy(frames = if (rest.isEmpty()) emptyList() else listOf(first.first) + rest)
	}

	private fun grid(n: Node, align: Align): Block.Grid? {
		val kind = n.classes.firstOrNull { it.startsWith("mcui-") }?.removePrefix("mcui-") ?: "Grid"
		val input = n.find { "mcui-input" in it.classes } ?: n
		val rowNodes = input.findAll({ "mcui-row" in it.classes })
		val rows = if (rowNodes.isNotEmpty()) rowNodes.map { r -> r.findAll({ "invslot" in it.classes }).map { slot(it) } }
		else listOf(input.findAll({ "invslot" in it.classes }).map { slot(it) })
		val outSlots = n.find { "mcui-output" in it.classes }?.findAll({ "invslot" in it.classes })?.map { slot(it) }.orEmpty()
		if (rows.all { it.isEmpty() } && outSlots.isEmpty()) return null

		return if (kind.startsWith("Brewing")) Block.Grid(kind, rows + listOf(outSlots), null, align)
		else Block.Grid(kind, rows, outSlots.firstOrNull(), align)
	}

	private fun figure(n: Node, b: Builder): Block? {
		val img = n.find { it.tag == "img" }?.let { imgOf(it) } ?: return null
		val capNode = n.find { it.tag == "figcaption" || "thumbcaption" in it.classes }
		val cap = capNode?.let { spansOf(it, b.ctx) }.orEmpty()
		val cl = n.classes
		val align = when {
			"mw-halign-left" in cl || "tleft" in cl -> Align.LEFT
			"mw-halign-center" in cl || "tnone" in cl || "mw-halign-none" in cl -> Align.CENTER
			else -> Align.RIGHT
		}
		return Block.Figure(img, cap, align)
	}

	private fun b64Json(s: String): kotlinx.serialization.json.JsonObject? = runCatching {
		val txt = String(java.util.Base64.getDecoder().decode(s.trim()), Charsets.UTF_8)
		kotlinx.serialization.json.Json.parseToJsonElement(txt) as kotlinx.serialization.json.JsonObject
	}.getOrNull()

	private fun kotlinx.serialization.json.JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

	private fun live(n: Node, st: Style, b: Builder) {
		val data = (n.attrs["data-bazaar"] ?: n.attrs["data-auction-house"])?.let(::b64Json)
		val op = data?.str("operation")
		if (op == "priceChange") return
		val shown = plainText(n).ifEmpty { "???" }
		val suffix = shown.substringAfter("???", " coins").ifBlank { "" }.let { if (it.isNotEmpty() && !it.startsWith(" ")) " $it" else it }
		val colour = n.find { colourOf(it) != null }?.let(::colourOf) ?: st.color
		val live = when (op) {
			"productData" -> data.str("productId")?.let { Live("bazaar", it, buy = data.str("datatype") != "sell", suffix = suffix) }
			"priceSpread" -> data.str("productId")?.let { Live("spread", it, suffix = suffix) }
			"calcBuyPrices", "calcSellPrices", "calcPrices" -> {
				val entries = (data["entries"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { e ->
					val arr = e as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
					val id = (arr.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
					val q = (arr.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: 1.0
					id to q
				}.orEmpty()
				val buy = data.str("price_type")?.contains("sell", true) != true && op != "calcSellPrices"
				if (entries.isEmpty()) null else Live("recipe", entries.first().first, buy = buy, suffix = suffix, entries = entries)
			}
			"itemPrice" -> data.str("itemId")?.let { Live("auction", it, average = data.str("type") == "average", suffix = suffix) }
			else -> null
		}
		b.spans += Span(shown.replace('\u00A0', ' '), st.href, st.bold, st.italic, colour, live = live)
	}

	private fun flow(n: Node, ctx: Ctx, st: Style = Style(), align: Align = Align.LEFT): List<Block> {
		val b = Builder(ctx)
		b.align = alignOf(n, align)
		walk(n, st, b)
		b.flush()
		return b.out
	}

	private fun spansOf(n: Node, ctx: Ctx, st: Style = Style()): List<Span> {
		val blocks = flow(n, ctx, st)
		val out = mutableListOf<Span>()
		for (blk in blocks) {
			if (blk !is Block.Para) continue
			if (out.isNotEmpty()) out += Span("\n")
			out += blk.spans
		}
		return tidy(out)
	}

	private class Flat(val blocks: List<Block>)

	private fun rowsOf(t: Node): List<Node> {
		val out = mutableListOf<Node>()
		fun go(x: Node) { for (c in x.children) if (c is Node && !dropped(c)) { if (c.tag == "tr") out += c else if (c.tag != "table") go(c) } }
		go(t)
		return out
	}

	private fun table(n: Node, ctx: Ctx, st: Style): Any? {
		val bordered = n.classes.any { it == "wikitable" || it == "article-table" || it == "fandom-table" }
		val tAlign = alignOf(n, Align.LEFT)
		val rows = mutableListOf<List<Cell>>()
		val caption = n.children.filterIsInstance<Node>().firstOrNull { it.tag == "caption" && !dropped(it) }
		for (tr in rowsOf(n)) {
			val rAlign = alignOf(tr, tAlign)
			val cells = tr.children.filterIsInstance<Node>().filter { (it.tag == "td" || it.tag == "th") && !dropped(it) }.map { c ->
				val th = c.tag == "th"
				val al = alignOf(c, if (th) Align.CENTER else rAlign)
				Cell(
					flow(c, ctx, if (th) st.copy(bold = true) else st, al), th,
					c.attrs["colspan"]?.toIntOrNull()?.coerceIn(1, 50) ?: 1, c.attrs["rowspan"]?.toIntOrNull()?.coerceIn(1, 200) ?: 1,
					al, bgOf(c) ?: bgOf(tr),
				)
			}
			if (cells.isNotEmpty()) rows += cells
		}
		if (rows.isEmpty() || rows.all { r -> r.all { it.blocks.isEmpty() } }) return null
		val cols = rows.maxOf { r -> r.sumOf { it.colspan } }
		if (!bordered && cols <= 1) return Flat(rows.flatMap { r -> r.flatMap { it.blocks } })
		val capRow = caption?.let { listOf(Cell(flow(it, ctx, st.copy(bold = true), Align.CENTER), header = false, colspan = cols, align = Align.CENTER)) }
		return Block.Table(if (capRow != null && bordered.not()) listOf(capRow) + rows else rows, bordered, "sortable" in n.classes)
	}

	private fun infobox(n: Node, ctx: Ctx): Block.Infobox {
		val title = n.find { "infobox-title" in it.classes || "pi-title" in it.classes }?.let(::plainText)
			?: n.find { it.tag == "caption" }?.let(::plainText).orEmpty()
		val was = ctx.inInfobox
		ctx.inInfobox = true
		val items = ibItems(n, ctx)
		ctx.inInfobox = was
		return Block.Infobox(title, markMain(items, ctx))
	}

	private fun markMain(items: List<Item>, ctx: Ctx): List<Item> {
		if (ctx.mainImage) return items
		return items.map { it ->
			if (ctx.mainImage || it !is Item.Row || it.label.isNotEmpty()) return@map it
			val v = it.value.map { b ->
				if (ctx.mainImage || b !is Block.Para) b
				else b.copy(spans = b.spans.map { s -> if (!ctx.mainImage && s.img != null && !s.img.slot) { ctx.mainImage = true; s.copy(img = s.img.copy(main = true)) } else s })
			}
			it.copy(value = v)
		}
	}

	private fun headerSpans(n: Node, ctx: Ctx) = spansOf(n, ctx, Style(bold = true)).let { s -> trim(s.map { it.copy(text = it.text.replace("ⓘ", "")) }) }

	private fun ibItems(x: Node, ctx: Ctx): List<Item> {
		val out = mutableListOf<Item>()
		for (c in x.children) {
			if (c !is Node || dropped(c)) continue
			val cl = c.classes
			when {
				"infobox-title" in cl || "pi-title" in cl -> {}
				"infobox-panel" in cl -> {
					val labels = c.find { "infobox-section-labels" in it.classes }?.children?.filterIsInstance<Node>()
						?.filter { it.tag == "li" && it.attrs["style"]?.replace(" ", "")?.contains("display:none") != true }?.map(::plainText).orEmpty()
					val sections = c.children.filterIsInstance<Node>().filter { "section" in it.classes }
					if (labels.size > 1 && sections.size == labels.size) {
						val active = sections.indexOfFirst { "active" in it.classes }.coerceAtLeast(0)
						out += Item.Tabs(ctx.tabs++, labels, sections.map { ibItems(it, ctx) }, active)
					} else {
						val shown = sections.filter { "active" in it.classes }.ifEmpty { sections.take(1) }
						if (sections.isEmpty()) out += ibItems(c, ctx) else for (s in shown) out += ibItems(s, ctx)
					}
				}
				"infobox-header" in cl || "pi-header" in cl -> headerSpans(c, ctx).takeIf { it.isNotEmpty() }?.let { out += Item.Header(it) }
				"infobox-row-container" in cl || "pi-data" in cl -> {
					val label = c.find { "infobox-row-label" in it.classes || "pi-data-label" in it.classes }
					val value = c.find { "infobox-row-value" in it.classes || "pi-data-value" in it.classes }
					val l = label?.let { spansOf(it, ctx, Style(bold = true)) }.orEmpty()
					val v = value?.let { flow(it, ctx) }.orEmpty()
					if (v.isNotEmpty() || l.isNotEmpty()) out += Item.Row(l, v)
				}
				"group" in cl -> {
					val cols = Regex("repeat\\(\\s*(\\d+)").find(c.attrs["style"].orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
					val inner = ibItems(c, ctx)
					if (cols > 1) {
						var run = mutableListOf<Item.Row>()
						fun end() { if (run.isNotEmpty()) { out += Item.Grid(cols, run); run = mutableListOf() } }
						for (it in inner) if (it is Item.Row) run += it else { end(); out += it }
						end()
					} else out += inner
				}
				c.tag == "tr" -> {
					val th = c.children.filterIsInstance<Node>().firstOrNull { it.tag == "th" && !dropped(it) }
					val tds = c.children.filterIsInstance<Node>().filter { it.tag == "td" && !dropped(it) }
					when {
						th != null && tds.isNotEmpty() -> out += Item.Row(spansOf(th, ctx, Style(bold = true)), tds.flatMap { flow(it, ctx) })
						th != null -> headerSpans(th, ctx).takeIf { it.isNotEmpty() }?.let { out += Item.Header(it) }
						tds.isNotEmpty() -> tds.flatMap { flow(it, ctx, align = Align.CENTER) }.takeIf { it.isNotEmpty() }?.let { out += Item.Row(emptyList(), it) }
					}
				}
				else -> out += ibItems(c, ctx)
			}
		}
		return out
	}

	private fun navbox(t: Node, ctx: Ctx): Block.Navbox? {
		val base = (ctx.nav++ + 1) * 1000
		var title: List<Span> = emptyList()
		val sections = mutableListOf<NavSection>()
		val loose = mutableListOf<NavRow>()
		for (tr in rowsOf(t)) {
			when {
				"navbox-title" in tr.classes -> title = tr.children.filterIsInstance<Node>().firstOrNull { it.tag == "th" || it.tag == "td" }?.let { spansOf(it, ctx, Style(bold = true)) }.orEmpty()
				else -> {
					val nested = tr.findAll({ it.tag == "table" && ("navbox-section" in it.classes || "navbox" in it.classes || "navbox-inner" in it.classes) })
					if (nested.isNotEmpty()) for (s in nested) navSection(s, base + sections.size + 1, ctx)?.let { sections += it }
					else loose += navRows(tr, ctx, 0)
				}
			}
		}
		if (loose.isNotEmpty()) sections.add(0, NavSection(base + 999, null, false, loose))
		if (sections.isEmpty() && title.isEmpty()) return null

		val image = t.find { "navbox-image" in it.classes }?.find { it.tag == "img" }?.let { imgOf(it) }
		return Block.Navbox(base, title, sections, "mw-collapsed" in t.classes, image)
	}

	private fun navSection(t: Node, id: Int, ctx: Ctx): NavSection? {
		var header: List<Span>? = null
		val rows = mutableListOf<NavRow>()
		for (tr in rowsOf(t)) {
			val hdr = tr.children.filterIsInstance<Node>().firstOrNull { "navbox-header" in it.classes || "navbox-title" in tr.classes }
			if (hdr != null && header == null) { header = spansOf(hdr, ctx, Style(bold = true)); continue }
			rows += navRows(tr, ctx, 0)
		}
		if (rows.isEmpty() && header == null) return null
		return NavSection(id, header, "mw-collapsed" in t.classes, rows)
	}

	private fun navRows(tr: Node, ctx: Ctx, depth: Int): List<NavRow> {
		val cells = tr.children.filterIsInstance<Node>().filter { !dropped(it) }
		val group = cells.firstOrNull { "navbox-group" in it.classes }?.let { spansOf(it, ctx, Style(bold = true)) }
		val list = cells.firstOrNull { "navbox-list" in it.classes } ?: cells.firstOrNull { it.tag == "td" } ?: return emptyList()
		val out = mutableListOf<NavRow>()

		val items = mutableListOf<List<Span>>()
		fun collect(x: Node) {
			for (c in x.children) {
				if (c !is Node || dropped(c)) continue
				if (c.tag == "table") continue
				if (c.tag == "li") { val s = spansOf(c, ctx).map { if (it.text == "\n") it.copy(text = " ") else it }; if (s.isNotEmpty()) items += s; continue }
				collect(c)
			}
		}
		collect(list)
		if (items.isEmpty()) {
			val loose = list.children.any { it is String && it.isNotBlank() } || list.children.any { it is Node && it.tag != "table" && it.tag != "div" && !dropped(it) }
			if (loose) spansOf(list, ctx).takeIf { it.isNotEmpty() }?.let { items += it }
		}
		if (items.isNotEmpty() || group != null) out += NavRow(group, items, depth)
		val subs = list.findAll({ it.tag == "table" })
		for (s in subs) for (r in rowsOf(s)) out += navRows(r, ctx, depth + 1)
		return out.filter { it.items.isNotEmpty() || out.size == 1 }
	}
}
