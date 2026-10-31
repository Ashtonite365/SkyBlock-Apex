// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.visualwords

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.network.chat.Component

object VisualWords {
	@Config
	object TConfig : ManagedConfig("plus-visual-words", Category.MISC) {
		val enabled by toggle("enabled") { false }
		val words by string("words") { "" }
		val caseSensitive by toggle("case-sensitive") { true }
		val inChat by toggle("in-chat") { true }
		val inTooltips by toggle("in-tooltips") { true }
	}

	private const val ARROW = "=>"

	fun parse(spec: String): List<Pair<String, String>> {
		val result = ArrayList<Pair<String, String>>()
		for (entry in spec.split(';')) {
			val at = entry.indexOf(ARROW)
			if (at < 1) continue
			result += entry.substring(0, at) to entry.substring(at + ARROW.length)
		}
		return result
	}

	fun apply(text: String, words: List<Pair<String, String>>, caseSensitive: Boolean): String =
		words.fold(text) { acc, (from, to) -> acc.replace(from, to, ignoreCase = !caseSensitive) }

	private var parsedFrom: String? = null
	private var parsed: List<Pair<String, String>> = emptyList()

	private fun currentWords(): List<Pair<String, String>> {
		val spec = TConfig.words
		if (spec != parsedFrom) {
			parsed = parse(spec)
			parsedFrom = spec
		}
		return parsed
	}

	private fun rewrite(line: Component): Component =
		PlusText.mapSegments(line) { text, style -> apply(text, currentWords(), TConfig.caseSensitive) to style }

	private fun active(where: Boolean) = TConfig.enabled && where && currentWords().isNotEmpty()

	@Subscribe
	fun onChat(event: ModifyChatEvent) {
		if (!active(TConfig.inChat)) return
		event.replaceWith = rewrite(event.replaceWith)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!active(TConfig.inTooltips)) return
		for (i in event.lines.indices) event.lines[i] = rewrite(event.lines[i])
	}
}
