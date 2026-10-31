// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Align
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Block
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Img
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Item
import dev.ashtonite365.apex.features.items.wiki.WikiDoc.Span
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WikiLayout {

	enum class Face { SANS, SERIF, GAME }

	data class TStyle(val face: Face = Face.SANS, val bold: Boolean = false, val italic: Boolean = false, val scale: Float = 1f)

	fun interface Measure { fun width(text: String, st: TStyle): Float }

	fun interface Prices {
		fun price(live: WikiDoc.Live): Double?
		companion object { val NONE = Prices { null } }
	}

	data class Theme(
		val page: Int, val text: Int, val heading: Int, val link: Int, val rule: Int, val band: Int,
		val ibBorder: Int, val ibBlue: Int, val ibBlueText: Int, val ibTab: Int,
		val tableBg: Int, val th: Int, val td: Int, val tableBorder: Int,
		val toc: Int, val tocBorder: Int,
		val navBorder: Int, val navBg: Int, val navTitle: Int, val navTitleText: Int, val navGroup: Int, val navGroupText: Int, val navList: Int,
		val guiFrame: Int, val guiBg: Int, val slot: Int, val slotDark: Int, val slotLight: Int, val muted: Int,
		val dark: Boolean,
	) {

		fun span(rgb: Int): Int = 0xFF000000.toInt() or (if (dark) darkMap[rgb] ?: rgb else rgb)

		companion object {
			private val darkMap: Map<Int, Int> = run {
				val mc = mapOf(
					"black" to 0x000000, "dark_blue" to 0x0000AA, "dark_green" to 0x00AA00, "dark_aqua" to 0x00AAAA,
					"dark_red" to 0xAA0000, "dark_purple" to 0xAA00AA, "gold" to 0xFFAA00, "gray" to 0xAAAAAA,
					"dark_gray" to 0x555555, "blue" to 0x5555FF, "green" to 0x55FF55, "aqua" to 0x55FFFF, "red" to 0xFF5555,
					"light_purple" to 0xFF55FF, "yellow" to 0xFFFF55, "white" to 0xFFFFFF,
				)
				WikiDoc.colours.entries.associate { (k, v) -> v to (mc[k] ?: v) }
			}
			private fun c(x: Long) = x.toInt()
			val LIGHT = Theme(
				page = c(0xFFEEEEEE), text = c(0xFF202122), heading = c(0xFF101418), link = c(0xFF546A9A), rule = c(0xFFA2A9B1), band = c(0xFFE4E4E4),
				ibBorder = c(0xFF92BFD4), ibBlue = c(0xFF3790BB), ibBlueText = c(0xFFF2F2F2), ibTab = c(0xFFD6E6EF),
				tableBg = c(0xFFF8F9FA), th = c(0xFFE6E6E6), td = c(0xFFEEEEEE), tableBorder = c(0xFFA2A9B1),
				toc = c(0xFFDEDEDE), tocBorder = c(0xFF555555),
				navBorder = c(0xFF3790BB), navBg = c(0xFF666666), navTitle = c(0xFF555555), navTitleText = c(0xFFDDDDDD),
				navGroup = c(0xFFE2E2E2), navGroupText = c(0xFF000000), navList = c(0xFFFFFFFF),
				guiFrame = c(0xFF222222), guiBg = c(0xFFC6C6C6), slot = c(0xFF8B8B8B), slotDark = c(0xFF373737), slotLight = c(0xFFFFFFFF), muted = c(0xFF72777D),
				dark = false,
			)
			val DARK = Theme(
				page = c(0xFF161616), text = c(0xFFDDDDDD), heading = c(0xFFF0F0F0), link = c(0xFF8AB4F8), rule = c(0xFF4A4F55), band = c(0xFF242424),
				ibBorder = c(0xFF2F5F78), ibBlue = c(0xFF1F5F80), ibBlueText = c(0xFFF2F2F2), ibTab = c(0xFF22343F),
				tableBg = c(0xFF1C1C1C), th = c(0xFF2A2A2A), td = c(0xFF1E1E1E), tableBorder = c(0xFF4A4F55),
				toc = c(0xFF222222), tocBorder = c(0xFF888888),
				navBorder = c(0xFF1F5F80), navBg = c(0xFF333333), navTitle = c(0xFF2A2A2A), navTitleText = c(0xFFDDDDDD),
				navGroup = c(0xFF2C2C2C), navGroupText = c(0xFFEEEEEE), navList = c(0xFF1A1A1A),
				guiFrame = c(0xFF111111), guiBg = c(0xFFC6C6C6), slot = c(0xFF8B8B8B), slotDark = c(0xFF373737), slotLight = c(0xFFFFFFFF), muted = c(0xFF9A9A9A),
				dark = true,
			)
		}
	}

	sealed interface Op {
		val x: Int; val y: Int; val w: Int; val h: Int
		fun shift(dx: Int, dy: Int): Op
		fun contains(px: Int, py: Int) = px >= x && px < x + w && py >= y && py < y + h
	}
	data class Rect(override val x: Int, override val y: Int, override val w: Int, override val h: Int, val argb: Int) : Op {
		override fun shift(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)
	}

	data class Text(
		override val x: Int, override val y: Int, override val w: Int, override val h: Int, val text: String, val st: TStyle, val argb: Int,
		val href: String? = null, val tip: String? = null, val sub: Float = 0f,
	) : Op {
		override fun shift(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)
	}

	data class Image(
		override val x: Int, override val y: Int, override val w: Int, override val h: Int, val img: Img?, val slot: Boolean = false,
		val href: String? = null,
	) : Op {
		override fun shift(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)
	}

	data class Toggle(override val x: Int, override val y: Int, override val w: Int, override val h: Int, val id: Int, val tab: Int = -1) : Op {
		override fun shift(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)
	}

	data class State(val flipped: Set<Int> = emptySet(), val tabs: Map<Int, Int> = emptyMap()) {
		fun flip(id: Int) = copy(flipped = if (id in flipped) flipped - id else flipped + id)
		fun tab(id: Int, i: Int) = copy(tabs = tabs + (id to i))
	}

	class Laid(val ops: List<Op>, val height: Int, val blockY: IntArray) {
		fun yOfBlock(i: Int): Int = blockY.getOrElse(i) { 0 }

		fun hrefAt(x: Int, y: Int, frameOf: ((Img) -> Img)? = null): String? {
			for (i in ops.indices.reversed()) {
				val o = ops[i]
				if (!o.contains(x, y)) continue
				when (o) {
					is Text -> if (o.href != null) return o.href
					is Image -> if (o.href != null) {
						val img = o.img

						return if (o.slot && img != null && img.frames.isNotEmpty() && frameOf != null && (img.href != null || img.frames.any { it.href != null }))
							frameOf(img).href else o.href
					}
					else -> {}
				}
			}
			return null
		}
		fun toggleAt(x: Int, y: Int): Toggle? = ops.lastOrNull { it is Toggle && it.contains(x, y) } as Toggle?
		fun opAt(x: Int, y: Int, pred: (Op) -> Boolean): Op? { for (i in ops.indices.reversed()) { val o = ops[i]; if (o.contains(x, y) && pred(o)) return o }; return null }

		fun text(): String = ops.filterIsInstance<Text>().joinToString(" ") { it.text }
	}

	const val LINE_H = 14

	const val CSS = 0.65f

	private class Env(
		val m: Measure, val theme: Theme, val prices: Prices, val state: State, val smooth: Boolean,
	) {

		fun scale(css: Float): Float {
			val s = css / 14f
			if (smooth) return s
			return when { s >= 1.75f -> 2f; s >= 1.3f -> 1.5f; s >= 1.12f -> 1.25f; else -> 1f }
		}
		fun face(f: Face) = if (smooth) f else Face.GAME
		fun textW(t: String, st: TStyle): Int = ceil(textF(t, st) - 0.01f).toInt()
		fun textF(t: String, st: TStyle): Float = m.width(t, st) * st.scale
		fun textH(st: TStyle): Int = ceil(9f * st.scale).toInt()
	}

	private data class Ctx(val st: TStyle, val colour: Int, val lineH: Int, val align: Align = Align.LEFT, val href: Boolean = true)

	fun layout(
		page: WikiDoc.Page, width: Int, m: Measure, theme: Theme = Theme.LIGHT, prices: Prices = Prices.NONE,
		state: State = State(), smooth: Boolean = true, title: String? = page.title,
	): Laid {
		val env = Env(m, theme, prices, state, smooth)
		val out = ArrayList<Op>(512)
		val blockY = IntArray(page.blocks.size)
		var y = 0
		if (!title.isNullOrBlank()) {
			val st = TStyle(env.face(Face.SERIF), bold = true, scale = env.scale(25f))
			y += inline(env, listOf(Span(title)), 0, 0, width, Ctx(st, theme.heading, env.textH(st) + 4), out)
			y += 2
			out += Rect(0, y, width, 1, theme.rule)
			y += 6
		}
		val body = Ctx(TStyle(env.face(Face.SANS)), theme.text, LINE_H)
		y = flow(env, page.blocks, 0, y, width, body, out, blockY, top = true)
		return Laid(out, y + 6, blockY)
	}

	private class Piece(val text: String?, val img: Img?, val span: Span, val st: TStyle, val w: Int, val h: Int, val space: Boolean, val nl: Boolean = false)

	private fun spanStyle(env: Env, s: Span, base: TStyle): TStyle {
		val face = if (s.game || s.code) Face.GAME else base.face
		return base.copy(face = env.face(face), bold = base.bold || s.bold, italic = base.italic || s.italic)
	}

	fun formatPrice(v: Double): String {
		val s = String.format(Locale.US, "%,.1f", v)
		return if (s.endsWith(".0")) s.dropLast(2) else s
	}

	private fun liveText(env: Env, s: Span): String {
		val live = s.live ?: return s.text
		val v = runCatching { env.prices.price(live) }.getOrNull() ?: return s.text
		return formatPrice(v) + live.suffix
	}

	private fun imgSize(img: Img, maxW: Int): Pair<Int, Int> {
		if (img.slot) return 18 to 18
		var w = max(4, (img.w * CSS).roundToInt()); var h = max(4, (img.h * CSS).roundToInt())
		if (w > maxW && maxW > 8) { h = max(4, h * maxW / w); w = maxW }
		return w to h
	}

	private fun pieces(env: Env, spans: List<Span>, base: TStyle, maxW: Int): List<Piece> {
		val out = ArrayList<Piece>()
		for (s in spans) {
			if (s.img != null) {
				val (w, h) = imgSize(s.img, maxW)
				out += Piece(null, s.img, s, base, w, h, false)
				continue
			}
			val st = spanStyle(env, s, base)
			val text = liveText(env, s)
			var i = 0
			while (i < text.length) {
				val c = text[i]
				when {
					c == '\n' -> { out += Piece("", null, s, st, 0, 0, false, nl = true); i++ }
					c == ' ' -> { out += Piece(" ", null, s, st, env.textW(" ", st), env.textH(st), true); i++ }
					else -> {
						var j = i
						while (j < text.length && text[j] != ' ' && text[j] != '\n') j++
						val word = text.substring(i, j)
						out += Piece(word, null, s, st, env.textW(word, st), env.textH(st), false)
						i = j
					}
				}
			}
		}
		return out
	}

	private fun colourOf(env: Env, s: Span, base: Int, links: Boolean): Int {
		if (s.color != null) return env.theme.span(s.color)
		if (links && s.href != null) {
			return when (val k = WikiLinks.classify(s.href)) {
				is WikiLinks.Kind.Internal, is WikiLinks.Kind.Anchor -> env.theme.link
				is WikiLinks.Kind.External -> if (k.url != null) env.theme.link else base
			}
		}
		return base
	}

	private fun hrefOf(s: Span): String? = hrefOf(s.href)
	private fun hrefOf(href: String?): String? = href?.takeIf { h ->
		when (val k = WikiLinks.classify(h)) { is WikiLinks.Kind.External -> k.url != null; else -> true }
	}

	private fun slotImg(s: Img?): Img? = s?.copy(href = hrefOf(s.href), frames = s.frames.map { it.copy(href = hrefOf(it.href)) })
	private fun slotOp(x: Int, y: Int, w: Int, h: Int, s: Img?): Image {
		val img = slotImg(s)

		val href = img?.href ?: img?.frames?.firstNotNullOfOrNull { it.href }
		return Image(x, y, w, h, img, slot = true, href = href)
	}

	private fun inline(env: Env, spans: List<Span>, x: Int, y: Int, w: Int, ctx: Ctx, out: MutableList<Op>, hang: Int = 0, wAt: ((Int) -> Int)? = null): Int {
		if (spans.isEmpty()) return 0
		val ps = pieces(env, spans, ctx.st, w)
		val lines = ArrayList<ArrayList<Piece>>()
		var cur = ArrayList<Piece>()
		var cx = 0
		var cy = y
		fun lineH(l: List<Piece>) = max(ctx.lineH, (l.maxOfOrNull { if (it.img != null) it.h + 2 else if (it.text != null) it.h + (ctx.lineH - env.textH(ctx.st)) else 0 } ?: 0))
		fun avail(): Int = (wAt?.invoke(cy) ?: w) - (if (lines.isEmpty()) 0 else hang)
		fun newline() { while (cur.isNotEmpty() && cur.last().space) cur.removeAt(cur.size - 1); lines += cur; cy += lineH(cur); cur = ArrayList(); cx = 0 }
		for (p in ps) {
			if (p.nl) { newline(); continue }
			if (p.space) { if (cur.isNotEmpty()) { cur += p; cx += p.w }; continue }
			if (cx + p.w > avail() && cur.isNotEmpty()) newline()
			if (p.text != null && p.w > avail() && p.text.length > 1) {

				var word: String = p.text
				while (word.isNotEmpty()) {
					var n = word.length
					while (n > 1 && cx + env.textW(word.substring(0, n), p.st) > avail()) n--
					val part = word.substring(0, n)
					val pw = env.textW(part, p.st)
					cur += Piece(part, null, p.span, p.st, pw, p.h, false); cx += pw
					word = word.substring(n)
					if (word.isNotEmpty()) newline()
				}
				continue
			}
			cur += p; cx += p.w
		}
		if (cur.isNotEmpty() || lines.isEmpty()) newline()

		var ly = y
		for ((li, line) in lines.withIndex()) {
			val lh = lineH(line)
			val lw = line.sumOf { it.w }
			val aw = (wAt?.invoke(ly) ?: w) - (if (li == 0) 0 else hang)
			val off = (if (li == 0) 0 else hang) + when (ctx.align) { Align.LEFT -> 0; Align.CENTER -> max(0, (aw - lw) / 2); Align.RIGHT -> max(0, aw - lw) }

			var fx = (x + off).toFloat()
			var k = 0
			while (k < line.size) {
				val p = line[k]
				if (p.img != null) {
					val ix = fx.roundToInt()
					out += if (p.img.slot) slotOp(ix, ly + (lh - p.h) / 2, p.w, p.h, p.img).let { op -> op.copy(href = hrefOf(p.span) ?: op.href) }
						else Image(ix, ly + (lh - p.h) / 2, p.w, p.h, p.img, false, hrefOf(p.span))
					fx = (ix + p.w).toFloat(); k++; continue
				}

				val sb = StringBuilder(p.text)
				var j = k + 1
				while (j < line.size && line[j].img == null && line[j].span === p.span && line[j].st == p.st) { sb.append(line[j].text); j++ }
				val text = sb.toString()
				val exact = env.textF(text, p.st)
				if (text.isNotBlank()) {
					val th = env.textH(p.st)
					val tx = fx.roundToInt()
					val rw = max(1, ceil(fx + exact - 0.01f).toInt() - tx)
					val tip = p.span.title ?: if (p.span.live != null && text == p.span.text) "Live price: not available in-game right now" else null

					out += Text(tx, ly + (lh - th) / 2, rw, th, text, p.st, colourOf(env, p.span, ctx.colour, ctx.href), hrefOf(p.span), tip, fx - tx)
				}
				fx += exact; k = j
			}
			ly += lh
		}
		return ly - y
	}

	private fun inlineMinMax(env: Env, spans: List<Span>, st: TStyle): Pair<Int, Int> {
		val ps = pieces(env, spans, st, 10_000)
		var mn = 0; var mx = 0; var line = 0
		for (p in ps) {
			if (p.nl) { mx = max(mx, line); line = 0; continue }
			if (!p.space) mn = max(mn, p.w)
			line += p.w
		}
		mx = max(mx, line)
		return mn to mx
	}

	private fun flow(env: Env, blocks: List<Block>, x: Int, y0: Int, w: Int, ctx: Ctx, out: MutableList<Op>, blockY: IntArray? = null, top: Boolean = false): Int {
		var y = y0
		var floatBottom = Int.MIN_VALUE
		var floatW = 0
		val gap = 10
		fun availAt(yy: Int) = if (yy < floatBottom) w - floatW - gap else w
		fun clear() { if (y < floatBottom) y = floatBottom; floatBottom = Int.MIN_VALUE; floatW = 0 }
		var prev: Block? = null
		for ((i, b) in blocks.withIndex()) {
			when (b) {
				is Block.Heading -> {
					val first = prev == null
					if (b.level <= 2) {
						if (!first) y += 12
						blockY?.set(i, y)
						val st = TStyle(env.face(Face.SERIF), scale = env.scale(21f))
						val bw = availAt(y)
						val start = out.size
						val h = inline(env, listOf(Span(b.text)), x + 4, y + 3, bw - 8, Ctx(st, env.theme.heading, env.textH(st) + 2), out)
						out.add(start, Rect(x, y, bw, h + 6, env.theme.band))
						out += Rect(x, y + h + 6, bw, 1, env.theme.rule)
						y += h + 7 + 5
					} else {
						if (!first) y += if (prev is Block.Heading) 2 else 6
						blockY?.set(i, y)
						val css = when (b.level) { 3 -> 16.8f; 4 -> 15f; else -> 14f }
						val st = TStyle(env.face(Face.SANS), bold = true, scale = env.scale(css))
						val start = out.size
						val h = inline(env, listOf(Span(b.text)), x, y + 2, availAt(y), Ctx(st, env.theme.heading, env.textH(st) + 3), out)
						val tw = out.subList(start, out.size).maxOfOrNull { it.x + it.w - x } ?: 0
						if (b.level == 3) out += Rect(x, y + h + 3, tw, 1, env.theme.rule)
						y += h + 3 + 5
					}
				}
				is Block.Para -> {
					if (prev is Block.Para && (prev.bullet == null || b.bullet == null) && prev.indent == b.indent && b.bullet != "") y += if (top) 5 else 2
					if (prev is Block.Table || prev is Block.Grid) y += 4
					blockY?.set(i, y)
					val ind = b.indent * 13
					val c = ctx.copy(align = if (b.align != Align.LEFT) b.align else ctx.align)
					val tree = b.bullet == WikiDoc.TREE_MID || b.bullet == WikiDoc.TREE_LAST
					if (!b.bullet.isNullOrEmpty() && !tree) {
						val bx = x + ind - (if (b.bullet == "•") 8 else env.textW(b.bullet + " ", ctx.st))
						out += Text(bx, y + (ctx.lineH - env.textH(ctx.st)) / 2, env.textW(b.bullet, ctx.st), env.textH(ctx.st), b.bullet, ctx.st, ctx.colour)
					}
					val yy = y
					val wAt: (Int) -> Int = { ly -> availAt(ly) - ind }
					y += inline(env, b.spans, x + ind, y, w - ind, c, out, wAt = if (top) wAt else null)
					if (y == yy) y += ctx.lineH
					if (tree) {

						val lx = x + ind - 9; val mid = yy + ctx.lineH / 2
						out += Rect(lx, yy, 1, (if (b.bullet == WikiDoc.TREE_MID) y else mid + 1) - yy, env.theme.rule)
						out += Rect(lx + 1, mid, 6, 1, env.theme.rule)
					}
				}
				is Block.Infobox -> {
					if (!top) { y = infobox(env, b, x, y, w, out) + 6; prev = b; continue }
					blockY?.set(i, y)
					val iw = (w * 0.31f).toInt().coerceIn(min(w, 140), 200)
					val ix = x + w - iw
					val bottom = infobox(env, b, ix, if (floatBottom > y) floatBottom else y, iw, out)
					floatBottom = bottom + 6
					floatW = max(floatW, iw)
				}
				is Block.Figure -> {
					val (iw0, ih) = imgSize(b.img, (w * 0.4f).toInt())
					val fw = iw0 + 8
					if (top && b.align == Align.RIGHT) {
						blockY?.set(i, y)
						val fy = if (floatBottom > y) floatBottom else y
						val bottom = figure(env, b, x + w - fw, fy, fw, iw0, ih, ctx, out)
						floatBottom = bottom + 6; floatW = max(floatW, fw)
					} else {
						blockY?.set(i, y)
						val fx = when (b.align) { Align.CENTER -> x + (availAt(y) - fw) / 2; else -> x }
						y = figure(env, b, fx, y, fw, iw0, ih, ctx, out) + 6
					}
				}
				is Block.Table -> {
					y += if (prev == null) 0 else 6
					var aw = availAt(y)
					val (mn, _) = tableMinMax(env, b, ctx)
					if (mn > aw && y < floatBottom) { clear(); aw = w }
					blockY?.set(i, y)
					y = table(env, b, x, y, aw, ctx, out) + 6
				}
				is Block.Grid -> {
					y += 3
					blockY?.set(i, y)
					val (gw, _) = gridSize(b)
					val aw = availAt(y)
					val gx = when (b.align) { Align.CENTER -> x + max(0, (min(aw, w) - gw) / 2); Align.RIGHT -> x + max(0, aw - gw); else -> x }
					y = grid(env, b, gx, y, out) + 4
				}
				is Block.Navbox -> {
					clear()
					y += 8
					blockY?.set(i, y)
					y = navbox(env, b, x, y, w, out) + 4
				}
				is Block.Toc -> {
					y += 6
					blockY?.set(i, y)
					y = toc(env, b, x, y, availAt(y), out) + 6
				}
				Block.Rule -> { y += 4; blockY?.set(i, y); out += Rect(x, y, availAt(y), 1, env.theme.rule); y += 5 }
			}
			prev = b
		}
		if (top && floatBottom > y) y = floatBottom
		return y
	}

	private fun scratch(env: Env, blocks: List<Block>, w: Int, ctx: Ctx): Pair<List<Op>, Int> {
		val tmp = ArrayList<Op>()
		val h = flow(env, blocks, 0, 0, w, ctx, tmp)
		return tmp to h
	}

	private fun place(ops: List<Op>, dx: Int, dy: Int, out: MutableList<Op>) { for (o in ops) out += o.shift(dx, dy) }

	private fun blocksMinMax(env: Env, blocks: List<Block>, ctx: Ctx): Pair<Int, Int> {
		var mn = 0; var mx = 0
		for (b in blocks) {
			val (a, c) = when (b) {
				is Block.Para -> inlineMinMax(env, b.spans, ctx.st).let { (p, q) -> (p + b.indent * 13) to (q + b.indent * 13) }
				is Block.Heading -> inlineMinMax(env, listOf(Span(b.text)), ctx.st.copy(bold = true))
				is Block.Grid -> gridSize(b).first.let { it to it }
				is Block.Table -> tableMinMax(env, b, ctx)
				is Block.Figure -> imgSize(b.img, 200).first.let { (it + 8) to (it + 8) }
				is Block.Infobox -> 140 to 190
				else -> 20 to 60
			}
			mn = max(mn, a); mx = max(mx, c)
		}
		return mn to mx
	}

	private class Placed(val cell: WikiDoc.Cell, val r: Int, val c: Int)

	private fun placeCells(t: Block.Table): Pair<List<Placed>, Int> {
		val taken = HashSet<Long>()
		val placed = ArrayList<Placed>()
		var cols = 0
		for ((r, row) in t.rows.withIndex()) {
			var c = 0
			for (cell in row) {
				while (taken.contains(r.toLong() shl 32 or c.toLong())) c++
				placed += Placed(cell, r, c)
				for (dr in 0 until cell.rowspan) for (dc in 0 until cell.colspan) taken += ((r + dr).toLong() shl 32) or (c + dc).toLong()
				c += cell.colspan
				cols = max(cols, c)
			}
		}
		return placed to cols
	}

	private const val CELL_PX = 4
	private const val CELL_PY = 2

	private fun cellCtx(env: Env, ctx: Ctx, cell: WikiDoc.Cell) = ctx.copy(st = ctx.st, align = cell.align)

	private fun colWidths(env: Env, t: Block.Table, ctx: Ctx, avail: Int): IntArray {
		val (placed, cols) = placeCells(t)
		val mn = IntArray(cols); val mx = IntArray(cols)
		for (p in placed.sortedBy { it.cell.colspan }) {
			val (a, b) = blocksMinMax(env, p.cell.blocks, cellCtx(env, ctx, p.cell))
			val a2 = a + CELL_PX * 2 + 1; val b2 = b + CELL_PX * 2 + 1
			if (p.cell.colspan == 1) { mn[p.c] = max(mn[p.c], a2); mx[p.c] = max(mx[p.c], b2) }
			else {
				val span = p.c until min(cols, p.c + p.cell.colspan)
				val haveMn = span.sumOf { mn[it] }; val haveMx = span.sumOf { mx[it] }
				if (a2 > haveMn) { val extra = (a2 - haveMn) / span.count() + 1; for (k in span) mn[k] += extra }
				if (b2 > haveMx) { val extra = (b2 - haveMx) / span.count() + 1; for (k in span) mx[k] += extra }
			}
		}
		for (k in 0 until cols) mx[k] = max(mx[k], mn[k])
		val inner = avail - 1
		val sMn = mn.sum(); val sMx = mx.sum()
		val wds = IntArray(cols)
		when {
			sMx <= inner -> for (k in 0 until cols) wds[k] = mx[k]
			sMn >= inner -> {
				for (k in 0 until cols) wds[k] = max(12, mn[k] * inner / max(1, sMn))
			}
			else -> {
				val extra = inner - sMn; val range = sMx - sMn
				for (k in 0 until cols) wds[k] = mn[k] + ((mx[k] - mn[k]).toLong() * extra / max(1, range)).toInt()
			}
		}
		return wds
	}

	private fun tableMinMax(env: Env, t: Block.Table, ctx: Ctx): Pair<Int, Int> {
		val (placed, cols) = placeCells(t)
		val mn = IntArray(cols); val mx = IntArray(cols)
		for (p in placed) if (p.cell.colspan == 1) {
			val (a, b) = blocksMinMax(env, p.cell.blocks, ctx)
			mn[p.c] = max(mn[p.c], a + CELL_PX * 2 + 1); mx[p.c] = max(mx[p.c], b + CELL_PX * 2 + 1)
		}
		return (mn.sum() + 1) to (mx.sum() + 1)
	}

	private fun table(env: Env, t: Block.Table, x: Int, y: Int, avail: Int, ctx: Ctx, out: MutableList<Op>): Int {
		val (placed, cols) = placeCells(t)
		if (cols == 0) return y
		if (cols > 1 && tableMinMax(env, t, ctx).first > avail) return stacked(env, t, x, y, avail, ctx, out)
		val wds = colWidths(env, t, ctx, avail)
		val colX = IntArray(cols + 1).also { for (k in 0 until cols) it[k + 1] = it[k] + wds[k] }
		val rows = t.rows.size

		class LaidCell(val p: Placed, val ops: List<Op>, val h: Int, val w: Int)
		val laid = placed.map { p ->
			val cw = colX[min(cols, p.c + p.cell.colspan)] - colX[p.c]
			val c = cellCtx(env, ctx, p.cell).let { if (p.cell.header) it.copy(st = it.st.copy(bold = true)) else it }
			val (ops, h) = scratch(env, p.cell.blocks, cw - CELL_PX * 2 - 1, c)
			LaidCell(p, ops, h, cw)
		}
		val rowH = IntArray(rows) { 0 }
		for (l in laid) if (l.p.cell.rowspan == 1) rowH[l.p.r] = max(rowH[l.p.r], l.h + CELL_PY * 2 + 1)
		for (k in 0 until rows) rowH[k] = max(rowH[k], ctx.lineH + CELL_PY * 2 + 1)
		for (l in laid) if (l.p.cell.rowspan > 1) {
			val last = min(rows, l.p.r + l.p.cell.rowspan)
			val have = (l.p.r until last).sumOf { rowH[it] }
			val need = l.h + CELL_PY * 2 + 1
			if (need > have) rowH[last - 1] += need - have
		}
		val rowY = IntArray(rows + 1).also { for (k in 0 until rows) it[k + 1] = it[k] + rowH[k] }
		val tw = colX[cols] + 1; val th = rowY[rows] + 1
		val th0 = env.theme
		if (t.bordered) out += Rect(x, y, tw, th, th0.tableBorder)
		for (l in laid) {
			val p = l.p
			val cx = x + colX[p.c]; val cy = y + rowY[p.r]
			val cw = l.w; val ch = rowY[min(rows, p.r + p.cell.rowspan)] - rowY[p.r]
			if (t.bordered) {
				val bg = p.cell.bg?.let { 0xFF000000.toInt() or (if (th0.dark) it.dim() else it) } ?: if (p.cell.header) th0.th else th0.td
				out += Rect(cx + 1, cy + 1, cw - 1, ch - 1, bg)
			} else p.cell.bg?.let { out += Rect(cx, cy, cw, ch, 0xFF000000.toInt() or it) }
			val dy = cy + 1 + max(CELL_PY, (ch - 1 - l.h) / 2)
			place(l.ops, cx + 1 + CELL_PX, dy, out)
		}
		return y + th
	}

	private fun stacked(env: Env, t: Block.Table, x: Int, y: Int, avail: Int, ctx: Ctx, out: MutableList<Op>): Int {
		val th0 = env.theme
		val start = out.size
		var cy = y + 1
		for (row in t.rows) for (cell in row) {
			val c = cellCtx(env, ctx, cell).let { if (cell.header) it.copy(st = it.st.copy(bold = true)) else it }
			val (ops, h) = scratch(env, cell.blocks, avail - CELL_PX * 2 - 2, c)
			val ch = max(h, ctx.lineH) + CELL_PY * 2
			if (t.bordered) out += Rect(x + 1, cy, avail - 2, ch, cell.bg?.let { 0xFF000000.toInt() or (if (th0.dark) it.dim() else it) } ?: if (cell.header) th0.th else th0.td)
			place(ops, x + 1 + CELL_PX, cy + CELL_PY, out)
			cy += ch + 1
		}
		if (t.bordered) out.add(start, Rect(x, y, avail, cy - y, th0.tableBorder))
		return cy
	}

	private fun Int.dim(): Int { val r = (this shr 16 and 255) / 3; val g = (this shr 8 and 255) / 3; val b = (this and 255) / 3; return (r shl 16) or (g shl 8) or b }

	private fun infobox(env: Env, b: Block.Infobox, x: Int, y: Int, w: Int, out: MutableList<Op>): Int {
		val t = env.theme
		val start = out.size
		val ix = x + 3; val iw = w - 6
		var cy = y + 3
		val small = Ctx(TStyle(env.face(Face.SANS), scale = env.scale(12.5f)), t.text, 13)
		if (b.title.isNotEmpty()) {
			val st = TStyle(env.face(Face.SANS), bold = true, scale = env.scale(18.75f))
			val s2 = out.size
			val h = inline(env, listOf(Span(b.title)), ix + 6, cy + 6, iw - 12, Ctx(st, t.ibBlueText, env.textH(st) + 3, Align.CENTER), out)
			out.add(s2, Rect(ix, cy, iw, h + 12, t.ibBlue))
			cy += h + 12
		}
		cy = ibItems(env, b.items, ix, cy, iw, small, out)
		val bottom = cy + 3
		out.add(start, Rect(x + 1, y + 1, w - 2, bottom - y - 2, t.page))
		out.add(start, Rect(x, y, w, bottom - y, t.ibBorder))
		return bottom
	}

	private fun ibItems(env: Env, items: List<Item>, ix: Int, y0: Int, iw: Int, small: Ctx, out: MutableList<Op>): Int {
		val t = env.theme
		var cy = y0
		for (it in items) {
			when (it) {
				is Item.Header -> {
					val st = TStyle(env.face(Face.SANS), bold = true, scale = env.scale(15f))
					val s2 = out.size
					val h = inline(env, it.spans, ix + 4, cy + 3, iw - 8, Ctx(st, t.ibBlueText, env.textH(st) + 3, Align.CENTER, href = false), out)

					out.add(s2, Rect(ix, cy, iw, h + 6, t.ibBlue))
					cy += h + 6
				}
				is Item.Row -> {
					out += Rect(ix, cy, iw, 1, t.ibBorder)
					cy += 1
					val pad = 4
					if (it.label.isEmpty()) {
						val (ops, h) = scratch(env, it.value.map(::centre), iw - 12, small.copy(align = Align.CENTER))
						place(ops, ix + 6, cy + pad, out)
						cy += h + pad * 2
					} else {
						val labelSt = small.st.copy(bold = true)

						val lw = max((iw * 0.34f).toInt(), inlineMinMax(env, it.label, labelSt).first + 10).coerceAtMost(max(1, iw / 2))
						val lh = inline(env, it.label, ix + 6, cy + pad, lw - 8, small.copy(st = labelSt), out)
						val (ops, vh) = scratch(env, it.value, iw - lw - 10, small)
						place(ops, ix + lw, cy + pad, out)
						cy += max(lh, vh) + pad * 2
					}
				}
				is Item.Grid -> {
					val cols = it.cols.coerceIn(1, 4)
					var k = 0
					while (k < it.cells.size) {
						val rowCells = it.cells.subList(k, min(it.cells.size, k + cols))
						k += rowCells.size
						out += Rect(ix, cy, iw, 1, t.ibBorder)
						cy += 1
						val cw = iw / rowCells.size
						var rh = 0
						val laid = rowCells.mapIndexed { ci, cell ->
							val tmp = ArrayList<Op>()
							val cx = ci * cw
							var h = inline(env, cell.label, cx + 4, 0, cw - 8, small.copy(st = small.st.copy(bold = true), align = Align.CENTER), tmp)
							val (ops, vh) = scratch(env, cell.value.map(::centre), cw - 8, small.copy(align = Align.CENTER))
							place(ops, cx + 4, h, tmp)
							h += vh
							rh = max(rh, h)
							tmp
						}
						for (ops in laid) place(ops, ix, cy + 4, out)
						for (ci in 1 until rowCells.size) out += Rect(ix + ci * cw, cy, 1, rh + 8, t.ibBorder)
						cy += rh + 8
					}
				}
				is Item.Tabs -> {
					val chosen = env.state.tabs[it.id] ?: it.active
					out += Rect(ix, cy, iw, 1, t.ibBorder)
					cy += 2

					val st = small.st
					val th = env.textH(st) + 8
					for ((li, label) in it.labels.withIndex()) {
						val sel = li == chosen
						if (sel) { out += Rect(ix, cy, iw, th, t.ibTab); out += Rect(ix, cy + th - 1, iw, 1, t.ibBorder) }
						val lw = min(iw - 8, env.textW(label, st))
						out += Text(ix + (iw - lw) / 2, cy + 4, lw, env.textH(st), label, st, t.link)
						out += Toggle(ix, cy, iw, th, it.id, li)
						cy += th
					}
					cy += 2
					cy = ibItems(env, it.sections.getOrElse(chosen) { emptyList() }, ix, cy, iw, small, out)
				}
			}
		}
		return cy
	}

	private fun centre(b: Block): Block = when (b) {
		is Block.Para -> b.copy(align = Align.CENTER)
		is Block.Grid -> b.copy(align = Align.CENTER)
		else -> b
	}

	private fun figure(env: Env, b: Block.Figure, x: Int, y: Int, fw: Int, iw: Int, ih: Int, ctx: Ctx, out: MutableList<Op>): Int {
		val t = env.theme
		val start = out.size
		out += Image(x + 4, y + 4, iw, ih, b.img)
		var cy = y + 4 + ih + 3
		if (b.caption.isNotEmpty()) {
			val st = ctx.st.copy(scale = ctx.st.scale * 0.9f)
			cy += inline(env, b.caption, x + 4, cy, fw - 8, ctx.copy(st = st, lineH = 12), out)
		}
		cy += 3
		out.add(start, Rect(x + 1, y + 1, fw - 2, cy - y - 2, t.tableBg))
		out.add(start, Rect(x, y, fw, cy - y, t.tableBorder))
		return cy
	}

	private const val SLOT = 18

	private fun gridSize(g: Block.Grid): Pair<Int, Int> {
		val cols = g.rows.maxOfOrNull { it.size } ?: 0
		return when {
			g.kind.startsWith("Brewing") -> (8 + 3 * SLOT + 2 * 10) to (8 + SLOT * 2 + 14 + 6)
			g.output != null -> (8 + cols * SLOT + 30 + 26) to (8 + max(g.rows.size * SLOT, 26))
			else -> (8 + cols * SLOT) to (8 + g.rows.size * SLOT)
		}
	}

	private fun grid(env: Env, g: Block.Grid, x: Int, y: Int, out: MutableList<Op>): Int {
		val t = env.theme
		val (w, h) = gridSize(g)
		if (g.kind.startsWith("Brewing")) {
			out += Rect(x, y, w, h, t.guiFrame)
			out += Rect(x + 3, y + 3, w - 6, h - 6, t.guiBg)
			val ing = g.rows.firstOrNull()?.firstOrNull()
			val cx = x + (w - SLOT) / 2
			out += slotOp(cx, y + 5, SLOT, SLOT, ing)
			val bottles = g.rows.getOrNull(1).orEmpty()
			val by = y + 5 + SLOT + 10
			val bx = listOf(x + 5, cx, x + w - 5 - SLOT)
			for (k in 0 until 3) out += slotOp(bx[k], by + (if (k == 1) 6 else 0), SLOT, SLOT, bottles.getOrNull(k))
			return y + h
		}
		out += Rect(x, y, w, h, t.slotLight)
		out += Rect(x + 1, y + 1, w - 1, h - 1, t.slotDark)
		out += Rect(x + 1, y + 1, w - 2, h - 2, t.guiBg)
		val gy = y + 4 + (if (g.output != null) max(0, (26 - g.rows.size * SLOT) / 2) else 0)
		for ((r, row) in g.rows.withIndex()) for ((c, s) in row.withIndex()) out += slotOp(x + 4 + c * SLOT, gy + r * SLOT, SLOT, SLOT, s)
		if (g.output != null) {
			val cols = g.rows.maxOfOrNull { it.size } ?: 0
			val ax = x + 4 + cols * SLOT + 6
			val my = y + h / 2

			out += Rect(ax, my - 2, 12, 4, t.slot)
			for (k in 0 until 6) out += Rect(ax + 12 + k, my - 6 + k, 1, 12 - 2 * k, t.slot)
			out += slotOp(ax + 24, my - 13, 26, 26, g.output)
		}
		return y + h
	}

	private fun toc(env: Env, b: Block.Toc, x: Int, y: Int, avail: Int, out: MutableList<Op>): Int {
		val t = env.theme
		val st = TStyle(env.face(Face.SANS), scale = env.scale(12.6f))
		val hidden = 1 in env.state.flipped
		val titleSt = st.copy(bold = true)
		val entries = b.entries
		val ew = entries.maxOfOrNull { env.textW(it.text, st) + if (it.level > 2) 16 else 0 } ?: 0
		val tw = env.textW("Contents", titleSt) + env.textW(" [hide]", st)
		val w = min(avail, max(ew + 18, tw + 18))
		val start = out.size
		var cy = y + 5
		val label = if (hidden) "[show]" else "[hide]"
		val cw = env.textW("Contents", titleSt); val lw = env.textW(label, st)
		if (tw + 8 <= w) {
			val tx = x + (w - tw) / 2
			out += Text(tx, cy, cw, env.textH(titleSt), "Contents", titleSt, t.text)
			val hx = tx + env.textW("Contents ", titleSt)
			out += Text(hx, cy, lw, env.textH(st), label, st, t.link)
			out += Toggle(hx, cy - 1, lw, env.textH(st) + 2, 1)
			cy += env.textH(titleSt) + 4
		} else {
			out += Text(x + 4, cy, min(cw, w - 5), env.textH(titleSt), "Contents", titleSt, t.text)
			cy += env.textH(titleSt) + 2
			out += Text(x + 4, cy, min(lw, w - 5), env.textH(st), label, st, t.link)
			out += Toggle(x + 4, cy - 1, min(lw, w - 5), env.textH(st) + 2, 1)
			cy += env.textH(st) + 4
		}
		if (!hidden) {
			out += Rect(x + 8, cy, w - 16, 1, t.ibBorder)
			cy += 4
			for (e in entries) {
				val ind = if (e.level > 2) 16 else 0
				if (e.level > 2) out += Rect(x + 10, cy, 1, 13, t.rule)
				cy += inline(env, listOf(Span(e.text, href = "#" + e.anchor)), x + 8 + ind, cy, w - 12 - ind, Ctx(st, t.link, 13), out)
			}
		}
		cy += 5
		out.add(start, Rect(x + 1, y, w - 1, cy - y, t.toc))
		out.add(start, Rect(x, y, 1, cy - y, t.tocBorder))
		return cy
	}

	private fun navbox(env: Env, b: Block.Navbox, x: Int, y: Int, w: Int, out: MutableList<Op>): Int {
		val t = env.theme
		val st = TStyle(env.face(Face.SANS), scale = env.scale(11.76f))
		val bold = st.copy(bold = true)
		val start = out.size
		val ix = x + 4; var iw = w - 8
		var cy = y + 4
		val open = b.collapsed == (b.id in env.state.flipped)
		fun toggleText(id: Int, shown: Boolean, ry: Int, rh: Int, colour: Int) {
			val label = if (shown) "[Hide]" else "[Show]"
			val lw = env.textW(label, st)
			out += Text(ix + iw - lw - 4, ry + (rh - env.textH(st)) / 2, lw, env.textH(st), label, st, colour)
			out += Toggle(ix + iw - lw - 6, ry, lw + 6, rh, id)
		}

		run {
			val s2 = out.size
			val h = inline(env, b.title, ix + 40, cy + 3, iw - 80, Ctx(bold, t.navTitleText, env.textH(bold) + 3, Align.CENTER, href = false), out)
			val rh = h + 6
			out.add(s2, Rect(ix, cy, iw, rh, t.navTitle))
			toggleText(b.id, open, cy, rh, t.navTitleText)
			cy += rh + 2
		}

		val pic = if (open && b.sections.isNotEmpty()) b.image?.let { imgSize(it, 64) } else null
		val picW = pic?.let { it.first + 12 } ?: 0
		val bodyTop = cy
		val iwAll = iw
		if (picW > 0) iw -= picW + 2
		if (open) for (sec in b.sections) {
			val secOpen = sec.header == null || (sec.collapsed == (sec.id in env.state.flipped))
			if (sec.header != null) {
				val s2 = out.size
				val h = inline(env, sec.header, ix + 40, cy + 2, iw - 80, Ctx(bold, t.navGroupText, env.textH(bold) + 3, Align.CENTER, href = false), out)
				val rh = h + 4
				out.add(s2, Rect(ix, cy, iw, rh, t.navGroup))
				toggleText(sec.id, secOpen, cy, rh, t.link)
				cy += rh + 2
			}
			if (!secOpen) continue
			val gw = sec.rows.mapNotNull { r -> r.group?.let { inlineMinMax(env, it, bold).second } }.maxOrNull()?.let { (it + 10).coerceIn(min(50, iw / 4), max(1, iw / 4)) } ?: 0
			for (r in sec.rows) {
				val lx = ix + (if (gw > 0) gw + 2 else 0)
				val lw = iw - (lx - ix)
				val spans = ArrayList<Span>()
				r.items.forEachIndexed { k, item -> if (k > 0) spans += Span(" • ", bold = true); spans += item }
				val s2 = out.size
				val lh = inline(env, spans, lx + 4, cy + 2, lw - 8, Ctx(st, t.text, 12, if (r.group == null) Align.CENTER else Align.LEFT), out)
				var gh = 0
				if (gw > 0 && r.group != null) gh = inline(env, r.group, ix + 3, cy + 2, gw - 6, Ctx(bold, t.navGroupText, 12, Align.CENTER), out)
				val rh = max(lh, gh) + 4
				out.add(s2, Rect(lx, cy, lw, rh, t.navList))
				if (gw > 0) out.add(s2, Rect(ix, cy, gw, rh, t.navGroup))
				cy += rh + 2
			}
		}
		if (pic != null && b.image != null && cy > bodyTop) {
			val px = ix + iwAll - picW
			val colH = cy - bodyTop - 2

			val ph = min(pic.second, max(8, colH - 4)); val pw = max(1, pic.first * ph / max(1, pic.second))
			out += Rect(px, bodyTop, picW, colH, t.navList)
			out += Image(px + (picW - pw) / 2, bodyTop + max(0, (colH - ph) / 2), pw, ph, b.image)
		}
		val bottom = cy + 2
		out.add(start, Rect(x + 2, y + 2, w - 4, bottom - y - 4, t.navBg))
		out.add(start, Rect(x, y, w, bottom - y, t.navBorder))
		return bottom
	}
}
