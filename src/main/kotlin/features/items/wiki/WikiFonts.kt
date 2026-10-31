// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.wiki.WikiLayout.Face
import dev.ashtonite365.apex.features.items.wiki.WikiLayout.TStyle
import dev.ashtonite365.apex.util.Client

object WikiFonts {
	private fun res(name: String): Style = Style.EMPTY.withFont(FontDescription.Resource(Apex.identifier(name)))
	private val SANS = res("wiki_sans")
	private val SANS_BOLD = res("wiki_sans_bold")
	private val SERIF = res("wiki_serif")
	private val SERIF_BOLD = res("wiki_serif_bold")
	private val GAME = Style.EMPTY
	private val GAME_BOLD = Style.EMPTY.withBold(true)

	fun style(st: TStyle): Style {
		val s = when (st.face) {
			Face.SANS -> if (st.bold) SANS_BOLD else SANS
			Face.SERIF -> if (st.bold) SERIF_BOLD else SERIF
			Face.GAME -> if (st.bold) GAME_BOLD else GAME
		}
		return if (st.italic) s.withItalic(true) else s
	}

	private fun slot(st: TStyle) = st.face.ordinal * 2 + (if (st.bold) 1 else 0)

	@Volatile private var table: Array<Map<Int, Float>> = Array(6) { emptyMap() }

	fun prime(text: String) {
		val cur = table
		val missing = text.codePoints().distinct().filter { it !in cur[0] }.toArray()
		if (missing.isEmpty()) return
		val font = Client.font
		val next = Array(6) { HashMap(cur[it]) }
		for (f in Face.entries) for (b in listOf(false, true)) {
			val st = TStyle(f, b)
			val style = style(st)
			val map = next[slot(st)]
			for (cp in missing) map[cp] = runCatching { font.splitter.stringWidth(FormattedText.of(String(Character.toChars(cp)), style)) }.getOrDefault(6f)
		}
		table = Array(6) { next[it] }
	}

	fun reset() { table = Array(6) { emptyMap() } }

	fun measure(): WikiLayout.Measure {
		val t = table
		return WikiLayout.Measure { text, st ->
			val m = t[slot(st)]
			var w = 0f
			var i = 0
			while (i < text.length) {
				val cp = text.codePointAt(i)
				w += m[cp] ?: 6f
				i += Character.charCount(cp)
			}
			w
		}
	}
}
