// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import com.mojang.blaze3d.font.GlyphInfo
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.util.RandomSource

fun testFont(advance: (Int) -> Int): Font {
	val cache = HashMap<Int, BakedGlyph>()
	fun glyph(cp: Int): BakedGlyph = cache.getOrPut(cp) {
		val info = object : GlyphInfo {
			override fun getAdvance(): Float = advance(cp).toFloat()
			override fun getBoldOffset(): Float = 1f
		}
		object : BakedGlyph {
			override fun info(): GlyphInfo = info
			override fun createGlyph(x: Float, y: Float, color: Int, shadow: Int, style: Style, a: Float, b: Float): TextRenderable.Styled = error("headless font")
		}
	}
	val source = object : GlyphSource {
		override fun getGlyph(cp: Int): BakedGlyph = glyph(cp)
		override fun getRandomGlyph(random: RandomSource, width: Int): BakedGlyph = glyph('?'.code)
	}
	return Font(object : Font.Provider {
		override fun glyphs(desc: FontDescription): GlyphSource = source
		override fun effect(): EffectGlyph = EffectGlyph { _, _, _, _, _, _, _, _ -> error("headless font") }
	})
}

val vanillaAdvance: (Int) -> Int = { cp ->
	when (cp.toChar()) {
		' ' -> 4
		'!', '\'', ',', '.', ':', ';', '|', 'i' -> 2
		'`', 'l' -> 3
		'"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4
		'<', '>', 'f', 'k' -> 5
		'@', '~' -> 7
		'▬' -> 9
		else -> 6
	}
}

val wideAdvance: (Int) -> Int = { cp -> if (cp == ' '.code) 5 else if (cp == '▬'.code) 11 else 8 }
val narrowAdvance: (Int) -> Int = { cp -> if (cp == ' '.code) 3 else if (cp == '-'.code) 4 else 5 }

fun encodeStyled(seq: net.minecraft.util.FormattedCharSequence): String {
	val sb = StringBuilder()
	var lastKey: String? = null
	seq.accept { _, style, cp ->
		val key = "{" + (style.color?.serialize() ?: "-") + (if (style.isBold) ",b" else "") + (if (style.isItalic) ",i" else "") + "}"
		if (key != lastKey) { sb.append(key); lastKey = key }
		sb.appendCodePoint(cp)
		true
	}
	return sb.toString()
}

fun encodeStyled(c: net.minecraft.network.chat.Component): String = encodeStyled(c.visualOrderText)

fun testChatWrap(text: net.minecraft.network.chat.FormattedText, width: Int, font: Font): List<net.minecraft.util.FormattedCharSequence> {
	val collected = net.minecraft.client.ComponentCollector()
	text.visit({ style, s -> collected.append(net.minecraft.network.chat.FormattedText.of(s, style)); java.util.Optional.empty<Unit>() }, Style.EMPTY)
	val lines = mutableListOf<net.minecraft.util.FormattedCharSequence>()
	font.splitter.splitLines(collected.resultOrEmpty, width, Style.EMPTY) { line, continued ->
		val ordered = net.minecraft.locale.Language.getInstance().getVisualOrder(line)
		lines += if (continued) net.minecraft.util.FormattedCharSequence.composite(net.minecraft.util.FormattedCharSequence.codepoint(' '.code, Style.EMPTY), ordered) else ordered
	}
	return lines.ifEmpty { listOf(net.minecraft.util.FormattedCharSequence.EMPTY) }
}
