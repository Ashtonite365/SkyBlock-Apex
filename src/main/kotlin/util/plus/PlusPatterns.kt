// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.plus

import dev.ashtonite365.apex.util.stripSectionCodes
import java.util.regex.Matcher
import java.util.regex.Pattern

object PlusPatterns {
	private val compiled = LinkedHashMap<String, Pattern>()
	val keys: Set<String> get() = compiled.keys

	fun compile(key: String, regex: String): Pattern = synchronized(compiled) {
		compiled[key] ?: Pattern.compile(regex).also { compiled[key] = it }
	}

	fun pattern(key: String, regex: String): Lazy<Pattern> = lazy { compile(key, regex) }

	fun group(prefix: String) = Group(prefix)

	class Group(val prefix: String) {
		fun pattern(key: String, regex: String) = PlusPatterns.pattern("$prefix.$key", regex)
	}
}

private fun Pattern.matcherFor(text: String, stripColours: Boolean): Matcher = matcher(if (stripColours) text.stripSectionCodes() else text)

inline fun <T> Pattern.matchMatcher(text: String, stripColours: Boolean = true, block: Matcher.() -> T): T? {
	val m = matcher(if (stripColours) text.stripSectionCodes() else text)
	if (!m.matches()) return null
	return m.block()
}

inline fun <T> Pattern.findMatcher(text: String, stripColours: Boolean = true, block: Matcher.() -> T): T? {
	val m = matcher(if (stripColours) text.stripSectionCodes() else text)
	if (!m.find()) return null
	return m.block()
}

fun Pattern.matches(text: String, stripColours: Boolean = true): Boolean = matcherFor(text, stripColours).matches()

fun Pattern.anyMatches(lines: Iterable<String>, stripColours: Boolean = true) = lines.any { line -> matches(line, stripColours) }

fun Matcher.groupOrNull(name: String): String? = try { group(name) } catch (e: Throwable) { null }

object ChatPatternTest {
	fun check(pattern: Pattern, matching: List<String>, notMatching: List<String> = emptyList()): List<String> {
		val failures = ArrayList<String>()
		for (s in matching) if (!pattern.matches(s)) failures += "should match: $s"
		for (s in notMatching) if (pattern.matches(s)) failures += "should not match: $s"
		return failures
	}
}
