/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: text matching for pack files (written from SPEC §3.3).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ashtonite365.apex.util.stripSectionCodes
import net.minecraft.network.chat.Component

class PackFormatException(message: String) : IllegalArgumentException(message)

internal fun badPack(message: String): Nothing = throw PackFormatException(message)

class TextMatch private constructor(
	private val literal: String?,
	private val pattern: Regex?,
	private val keepFormatting: Boolean,
) {
	private val comparableLiteral = literal?.let { if (keepFormatting) it else it.stripSectionCodes() }

	private val containsShortcut: String? = pattern?.pattern?.let(::plainMiddle)

	fun test(value: String): Boolean {
		val subject = if (keepFormatting) value else value.stripSectionCodes()
		if (containsShortcut != null && !hasLineBreak(subject)) return subject.contains(containsShortcut)
		return if (pattern != null) pattern.matches(subject) else subject == comparableLiteral
	}

	fun test(text: Component): Boolean = test(text.string)

	fun searchPattern(): Regex = pattern ?: Regex(Regex.escape(literal!!))

	companion object {
		private const val REGEX_SYNTAX = "\\^$.|?*+()[]{}"

		fun plainMiddle(source: String): String? {
			if (source.length < 4 || !source.startsWith(".*") || !source.endsWith(".*")) return null
			val middle = source.substring(2, source.length - 2)
			if (middle.isEmpty() || middle.any { it in REGEX_SYNTAX || hasLineBreak(it.toString()) }) return null
			return middle
		}

		fun hasLineBreak(s: String): Boolean {
			for (c in s) if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') return true
			return false
		}

		fun read(json: JsonElement): TextMatch {
			if (json.isJsonPrimitive && json.asJsonPrimitive.isString)
				return TextMatch(json.asString, null, keepFormatting = false)
			if (!json.isJsonObject) badPack("text match must be a string or an object")
			val obj: JsonObject = json.asJsonObject
			val equals = obj.get("equals")
			val regex = obj.get("regex")
			if ((equals == null) == (regex == null)) badPack("text match needs exactly one of 'equals' or 'regex'")
			val keep = when (val mode = obj.get("color")?.asString ?: "strip") {
				"strip" -> false
				"preserve" -> true
				else -> badPack("unknown text colour mode '$mode'")
			}
			return if (equals != null) TextMatch(equals.asString, null, keep)
			else TextMatch(null, Regex(regex.asString), keep)
		}
	}
}
