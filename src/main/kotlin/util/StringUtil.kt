package dev.ashtonite365.apex.util

object StringUtil {

	private val camelBoundary = Regex("((?<=[a-z])(?=[A-Z]))| ")

	fun String.words(): Sequence<String> = splitToSequence(' ')

	fun String.camelWords(): Sequence<String> = camelBoundary.splitToSequence(this)

	fun parseIntWithComma(string: String): Int = string.filterNot { it == ',' }.toInt()

	fun String.title(): String = if (isEmpty()) this else this[0].titlecase() + substring(1)

	fun Iterable<String>.unwords(): String = joinToString(separator = " ")

	fun nextLexicographicStringOfSameLength(string: String): String {
		val bumpAt = string.indexOfLast { it != Char.MAX_VALUE }
		if (bumpAt < 0) return ""
		val chars = string.toCharArray(0, bumpAt + 1)
		chars[bumpAt] = chars[bumpAt] + 1
		return String(chars)
	}
}
