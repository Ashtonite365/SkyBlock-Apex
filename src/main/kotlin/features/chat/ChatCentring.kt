// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.chat

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.ComponentRenderUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.util.Optional
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ChatCentring {
	@Config
	object TConfig : ManagedConfig("hypixel-chat-layout", Category.CHAT) {
		val enabled by toggle("enabled") { true }
		val fixCentred by toggle("fix-centred") { true }
		val fixSeparators by toggle("fix-separators") { true }
	}

	const val HYPIXEL_CHAT_PX = 320
	private const val CENTRE_SLACK_PX = 6
	private const val RULE_CHARS = "-—▬━"

	private val stockAdvances: Map<Char, Int> = buildMap {
		fun put(px: Int, chars: String) = chars.forEach { put(it, px) }
		put(2, "!',.:;|i")
		put(3, "`l")
		put(4, " \"()*I[]t{}")
		put(5, "<>fk")
		put(7, "@~")
		put(9, "▬")
	}

	fun stockWidth(c: Char, bold: Boolean): Int = (stockAdvances[c] ?: 6) + if (bold && c != ' ') 1 else 0

	private class StyledLine(val text: String, val styles: List<Style>) {
		fun stockPx(range: IntRange = text.indices): Int = range.sumOf { stockWidth(text[it], styles[it].isBold) }

		fun slice(range: IntRange): MutableComponent {
			val result = Component.empty()
			var runStart = range.first
			for (i in range) {
				val endsRun = i == range.last || styles[i + 1] != styles[i]
				if (endsRun) {
					result.append(Component.literal(text.substring(runStart, i + 1)).withStyle(styles[i]))
					runStart = i + 1
				}
			}
			return result
		}
	}

	private fun linesOf(message: Component): List<StyledLine> {
		val lines = mutableListOf<StyledLine>()
		val chars = StringBuilder()
		val styles = mutableListOf<Style>()
		fun cut() { lines += StyledLine(chars.toString(), styles.toList()); chars.clear(); styles.clear() }
		message.visit<Unit>(FormattedText.StyledContentConsumer { style, str ->
			for (ch in str) if (ch == '\n') cut() else { chars.append(ch); styles += style }
			Optional.empty()
		}, Style.EMPTY)
		cut()
		return lines
	}

	private fun repeatToWidth(font: Font, c: Char, style: Style, px: Int): Component {
		val each = maxOf(1, font.width(Component.literal(c.toString()).withStyle(style)))
		return Component.literal(c.toString().repeat(maxOf(1, px / each))).withStyle(style)
	}

	private fun indent(font: Font, px: Int): Component = Component.literal(" ".repeat(maxOf(0, px / maxOf(1, font.width(" ")))))

	private fun onCentreLine(line: StyledLine, range: IntRange): Boolean {
		val lead = line.stockPx(0 until range.first)
		return kotlin.math.abs(HYPIXEL_CHAT_PX / 2 - (lead + line.stockPx(range) / 2)) <= CENTRE_SLACK_PX
	}

	private sealed interface Outcome
	private data class Rebuilt(val component: Component) : Outcome
	private data object Keep : Outcome
	private data object NotMatched : Outcome

	private fun asRule(line: StyledLine, font: Font, width: Int): Outcome {
		val core = line.text.trim()
		if (core.length < 5 || core[0] !in RULE_CHARS || core.any { it != core[0] }) return NotMatched
		return Rebuilt(repeatToWidth(font, core[0], line.styles[line.text.indexOf(core[0])], width - 2))
	}

	private fun asBanner(line: StyledLine, font: Font, width: Int): Outcome {
		val core = line.text.trim()
		val rule = core.firstOrNull() ?: return NotMatched
		if (core.length <= 10 || rule !in RULE_CHARS || core.last() != rule) return NotMatched
		val isTitle = { ch: Char -> ch != rule && ch != ' ' }
		val first = line.text.indexOfFirst(isTitle)
		if (first < 0) return Keep
		val title = first..line.text.indexOfLast(isTitle)
		if (line.stockPx() <= HYPIXEL_CHAT_PX - 10 || !onCentreLine(line, title)) return NotMatched
		val titleComponent = line.slice(title)
		val ruleStyle = line.styles[line.text.indexOf(rule)]
		val sidePx = maxOf(0, (width - font.width(titleComponent)) / 2 - font.width(" "))
		return Rebuilt(Component.empty()
			.append(repeatToWidth(font, rule, ruleStyle, sidePx)).append(" ")
			.append(titleComponent).append(" ")
			.append(repeatToWidth(font, rule, ruleStyle, sidePx)))
	}

	private fun asCentred(line: StyledLine, font: Font, width: Int): Outcome {
		if (!line.text.startsWith(" ")) return NotMatched
		val body = line.text.indexOfFirst { it != ' ' }..line.text.indexOfLast { it != ' ' }
		if (!onCentreLine(line, body)) return Keep
		val bodyComponent = line.slice(body)
		return Rebuilt(Component.empty().append(indent(font, maxOf(0, (width - font.width(bodyComponent)) / 2))).append(bodyComponent))
	}

	private fun rebuild(line: StyledLine, font: Font, width: Int): Component? {
		if (line.text.trim().isEmpty()) return null
		val attempts = buildList<(StyledLine, Font, Int) -> Outcome> {
			if (TConfig.fixSeparators) { add(::asRule); add(::asBanner) }
			if (TConfig.fixCentred) add(::asCentred)
		}
		for (attempt in attempts) when (val o = attempt(line, font, width)) {
			is Rebuilt -> return o.component
			Keep -> return null
			NotMatched -> {}
		}
		return null
	}

	fun relayout(
		message: Component, font: Font, width: Int,
		wrap: (FormattedText, Int, Font) -> List<FormattedCharSequence> = ComponentRenderUtils::wrapComponents,
	): List<FormattedCharSequence>? {
		var touched = false
		val wrapped = linesOf(message).flatMap { line ->
			val rebuilt = rebuild(line, font, width)?.also { touched = true }
			val shown = rebuilt ?: if (line.text.isEmpty()) Component.empty() else line.slice(line.text.indices)
			wrap(shown, width, font).ifEmpty { listOf(FormattedCharSequence.EMPTY) }
		}
		return wrapped.takeIf { touched }
	}

	@JvmStatic
	fun splitForChat(message: Component, font: Font, width: Int): List<FormattedCharSequence>? =
		if (TConfig.enabled && SkyBlockSession.inHypixel) relayout(message, font, width) else null
}
