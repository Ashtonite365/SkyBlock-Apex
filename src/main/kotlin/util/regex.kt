package dev.ashtonite365.apex.util

import java.util.regex.Matcher
import java.util.regex.Pattern
import org.intellij.lang.annotations.Language
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalContracts::class)
inline fun <T> String.ifMatches(pattern: Regex, onMatch: (MatchResult) -> T): T? {
	contract { callsInPlace(onMatch, AT_MOST_ONCE) }
	val found = pattern.matchEntire(this) ?: return null
	return onMatch(found)
}

@OptIn(ExperimentalContracts::class)
inline fun <T> Pattern.useMatch(input: String?, onMatch: Matcher.() -> T): T? {
	contract { callsInPlace(onMatch, AT_MOST_ONCE) }
	if (input == null) return null
	val matcher = matcher(input)
	return if (matcher.matches()) matcher.onMatch() else null
}

fun <T> String.ifDropLast(suffix: String, onSuffix: (String) -> T): T? =
	if (endsWith(suffix)) onSuffix(substring(0, length - suffix.length)) else null

@Language("RegExp")
const val TIME_PATTERN: String = "[0-9]+[ms]"

@Language("RegExp")
const val SHORT_NUMBER_FORMAT: String = "[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?[kKmMbB]?"

val siScalars: Map<Char, Double> = linkedMapOf(
	'k' to 1_000.0, 'K' to 1_000.0,
	'm' to 1_000_000.0, 'M' to 1_000_000.0,
	'b' to 1_000_000_000.0, 'B' to 1_000_000_000.0,
)

fun parseTimePattern(token: String): Duration {
	val count = token.dropLast(1).toInt()
	return when (token.lastOrNull()) {
		'm' -> count.minutes
		's' -> count.seconds
		else -> throw IllegalStateException("Invalid pattern for time $token")
	}
}

private val SHORT_NUMBER = Regex("^([+-]?[0-9][0-9,]*(?:\\.[0-9]+)?|[+-]?\\.[0-9]+)([kKmMbB]?)")

private val COLOUR_CODE = Regex("\u00a7.")

fun parseShortNumberOrNull(string: String): Double? {
	val clean = string.replace(COLOUR_CODE, "").trim()
	val m = SHORT_NUMBER.find(clean) ?: return null
	val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
	val unit = m.groupValues[2]
	val mult = if (unit.isEmpty()) 1.0 else siScalars.getValue(unit[0])
	return base * mult
}

fun parseShortNumber(string: String): Double = parseShortNumberOrNull(string) ?: 0.0
