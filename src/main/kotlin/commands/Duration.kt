// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import dev.ashtonite365.apex.util.tr

object DurationArgumentType
	: ArgumentType<Duration> {
	val unknownTimeCode = DynamicCommandExceptionType { code -> tr("apex.command-argument.duration.error", "Unknown time code '$code'") }

	val timeSuffixes: Map<Char, DurationUnit> = linkedMapOf('m' to DurationUnit.MINUTES, 's' to DurationUnit.SECONDS, 'h' to DurationUnit.HOURS)

	val regex: Pattern = Pattern.compile("(?<count>[0-9]+)(?<what>[" + timeSuffixes.keys.joinToString(separator = "") + "])")

	private fun Char.isAsciiDigit() = this in '0'..'9'

	private fun nextPiece(word: String, from: Int): IntRange? {
		var i = from
		while (i < word.length) {
			if (!word[i].isAsciiDigit()) { i++; continue }
			var digitsEnd = i
			while (digitsEnd < word.length && word[digitsEnd].isAsciiDigit()) digitsEnd++

			if (digitsEnd < word.length && word[digitsEnd] in timeSuffixes) return i until digitsEnd + 1
			i = digitsEnd
		}
		return null
	}

	override fun parse(
		reader: StringReader,
	): Duration {
		val wordStart = reader.cursor
		val word = reader.readUnquotedString()
		var total = Duration.ZERO
		var consumed = 0
		while (consumed < word.length) {
			val piece = nextPiece(word, consumed)
			val junkEnd = piece?.first ?: word.length
			if (junkEnd != consumed) {
				reader.cursor = wordStart + consumed
				throw unknownTimeCode.createWithContext(reader, word.substring(consumed, junkEnd))
			}
			piece!!
			val unit = timeSuffixes.getValue(word[piece.last])
			total += word.substring(piece.first, piece.last).toDouble().toDuration(unit)
			consumed = piece.last + 1
		}
		return total
	}

	override fun <S : Any?> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
		val typed = builder.remainingLowerCase.substringBefore(' ')
		return when {
			typed.isEmpty() -> super.listSuggestions(context, builder)
			typed.last().isDigit() -> {
				timeSuffixes.keys.forEach { unit -> builder.suggest(typed + unit) }
				builder.buildFuture()
			}
			else -> builder.buildFuture()
		}
	}

	override fun getExamples(): Collection<String> = EXAMPLES

	private val EXAMPLES = listOf("3m", "20s", "1h45m")
}
