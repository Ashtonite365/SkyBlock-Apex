// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.streamer

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object StreamerMode {
	val identifier: String
		get() = "streamer-mode"

	@Config
	object TConfig : ManagedConfig(category = Category.MISC, name = identifier) {
		val enabled by toggle("enabled") { false }
		val chat by toggle("chat") { true }
		val scoreboard by toggle("scoreboard") { true }
	}

	private val idPrefixes = listOf("mini", "mega", "m", "M")

	private class ServerId(val range: IntRange, val prefix: String, val number: Int)

	private fun idAt(text: String, start: Int): ServerId? {
		for (prefix in idPrefixes) {
			if (!text.startsWith(prefix, start)) continue
			val digitsFrom = start + prefix.length
			var digitsTo = digitsFrom
			while (digitsTo < text.length && digitsTo - digitsFrom < 3 && text[digitsTo] in '0'..'9') digitsTo++
			if (digitsTo == digitsFrom || digitsTo >= text.length || text[digitsTo] !in 'A'..'Z') continue
			return ServerId(start..digitsTo, prefix, text.substring(digitsFrom, digitsTo).toInt())
		}
		return null
	}

	private fun serverIds(text: String): List<ServerId> {
		val found = mutableListOf<ServerId>()
		var at = 0
		while (at < text.length) {
			val id = idAt(text, at)
			if (id == null) at++ else { found += id; at = id.range.last + 1 }
		}
		return found
	}

	private fun standIn(id: ServerId): String {
		val number = (id.number * 9182739L + 11) % 500
		return id.prefix + number + ('A' + (number % 26).toInt())
	}

	fun filterLobbyNames(line: String): String {
		val ids = serverIds(line)
		if (ids.isEmpty()) return line
		val out = StringBuilder(line.length)
		var copied = 0
		for (id in ids) {
			out.append(line, copied, id.range.first).append(standIn(id))
			copied = id.range.last + 1
		}
		return out.append(line, copied, line.length).toString()
	}

	fun filterSegments(segments: List<String>): List<String> {
		val joined = segments.joinToString("")
		val ids = serverIds(joined)
		if (ids.isEmpty()) return segments
		val pieces = segments.map { StringBuilder() }
		val pieceStart = IntArray(segments.size)
		for (p in 1 until segments.size) pieceStart[p] = pieceStart[p - 1] + segments[p - 1].length
		fun pieceOf(index: Int): Int = pieceStart.indices.last { pieceStart[it] <= index && index < pieceStart[it] + segments[it].length }
		var copied = 0
		fun copyUpTo(end: Int) {
			for (i in copied until end) pieces[pieceOf(i)].append(joined[i])
		}
		for (id in ids) {
			copyUpTo(id.range.first)
			pieces[pieceOf(id.range.first)].append(standIn(id))
			copied = id.range.last + 1
		}
		copyUpTo(joined.length)
		return pieces.map { it.toString() }
	}

	fun filterComponent(component: Component): Component {
		if (serverIds(component.string).isEmpty()) return component
		val styles = mutableListOf<Style>()
		val texts = mutableListOf<String>()
		component.visit({ style, text ->
			styles += style
			texts += text
			java.util.Optional.empty<Unit>()
		}, Style.EMPTY)
		val rebuilt = Component.empty()
		for ((i, text) in filterSegments(texts).withIndex()) if (text.isNotEmpty()) rebuilt.append(Component.literal(text).withStyle(styles[i]))
		return rebuilt
	}

	private fun scoreboardOn() = TConfig.enabled && TConfig.scoreboard

	@JvmStatic
	fun filterTabName(original: Component): Component = if (scoreboardOn()) filterComponent(original) else original

	@JvmStatic
	fun filterTeamName(original: MutableComponent): MutableComponent {
		if (!scoreboardOn() || serverIds(original.string).isEmpty()) return original
		return filterComponent(original).copy()
	}

	@Subscribe
	fun onChat(event: ModifyChatEvent) {
		if (TConfig.enabled && TConfig.chat) event.replaceWith = filterComponent(event.replaceWith)
	}
}
