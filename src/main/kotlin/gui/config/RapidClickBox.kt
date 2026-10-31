// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config

import net.minecraft.network.chat.Component

object RapidClickBox {

	data class Entry(val key: String, val feature: String)

	val ENTRIES: List<Entry> = emptyList()

	val BOXED: List<String> = ENTRIES.map { it.key }

	val EXCLUDED: Set<String> = emptySet()

	private val boxedSet = BOXED.toHashSet()

	init {
		require(boxedSet.size == BOXED.size) { "Duplicate RapidClickBox entry" }
		require(EXCLUDED.none { it in boxedSet }) { "An EXCLUDED option is boxed" }
	}

	fun key(option: ManagedOption<*>) = "${option.element.name}.${option.propertyName}"
	fun isBoxed(key: String): Boolean = key in boxedSet
	fun isBoxed(option: ManagedOption<*>): Boolean = isBoxed(key(option))

	const val BORDER_COLOUR = 0xFFB050FF.toInt()

	const val BACKGROUND_COLOUR = 0xE02A0B40.toInt()

	const val TINT_COLOUR = 0x2CB050FF

	const val SIGN_COLOUR = 0xFFFFC21A.toInt()
	const val SIGN_MARK_COLOUR = 0xFF2A0B40.toInt()

	const val TEXT_COLOUR = 0xFFF0D0FF.toInt()

	const val BORDER = 2

	const val INSET = BORDER + 2
	const val SIGN_WIDTH = 11
	const val SIGN_GAP = 4

	const val HEADER_PAD = 2

	const val HEADER_KEY = "apex.settings.rapid-click-box.warning"
	const val HEADER_FALLBACK = "DANGER: clicks for you in a rapid chain, not one click per click you make."

	fun headerText(): Component = Component.translatableWithFallback(HEADER_KEY, HEADER_FALLBACK)

	fun textWidth(width: Int): Int = (width - 2 * INSET - SIGN_WIDTH - SIGN_GAP).coerceAtLeast(1)

	fun headerHeight(lines: Int, lineHeight: Int): Int =
		maxOf(lines.coerceAtLeast(1) * (lineHeight + 1) - 1, SIGN_WIDTH) + 2 * HEADER_PAD

	fun boxHeight(innerHeight: Int, lines: Int, lineHeight: Int): Int =
		INSET + headerHeight(lines, lineHeight) + innerHeight + INSET

	fun innerOffsetY(lines: Int, lineHeight: Int): Int = INSET + headerHeight(lines, lineHeight)

	fun wrap(text: String, maxWidth: Int, measure: (String) -> Int): List<String> {
		val out = mutableListOf<String>()
		for (paragraph in text.split('\n')) {
			var line = ""
			for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
				val joined = if (line.isEmpty()) word else "$line $word"
				if (measure(joined) <= maxWidth) { line = joined; continue }
				if (line.isNotEmpty()) { out += line; line = "" }
				var rest = word
				while (measure(rest) > maxWidth && rest.length > 1) {
					var n = 1
					while (n < rest.length && measure(rest.substring(0, n + 1)) <= maxWidth) n++
					out += rest.substring(0, n)
					rest = rest.substring(n)
				}
				line = rest
			}
			out += line
		}
		return out
	}
}
