/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: a small lenient JSON reader for pack files. Packs in the wild contain comments,
 * trailing commas and unquoted strings; this reads all of those in one pass straight into Gson trees (which the
 * engine and Minecraft's codecs use), instead of parsing each file twice through two libraries.
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber

class PackJsonException(message: String, at: Int) : IllegalArgumentException("$message at offset $at")

object PackJson {
	fun parse(text: String): JsonElement {
		val r = Reader(text)
		val value = r.value()
		r.skipSpace()
		if (r.pos < text.length) r.fail("Unexpected trailing content")
		return value
	}

	private class Reader(val s: String) {
		var pos = 0

		fun fail(message: String): Nothing = throw PackJsonException(message, pos)

		fun skipSpace() {
			while (pos < s.length) {
				val c = s[pos]
				when {
					c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF' -> pos++
					c == '/' && pos + 1 < s.length && s[pos + 1] == '/' -> {
						pos += 2
						while (pos < s.length && s[pos] != '\n') pos++
					}
					c == '/' && pos + 1 < s.length && s[pos + 1] == '*' -> {
						val end = s.indexOf("*/", pos + 2)
						if (end < 0) fail("Unclosed comment")
						pos = end + 2
					}
					else -> return
				}
			}
		}

		fun value(): JsonElement {
			skipSpace()
			if (pos >= s.length) fail("Unexpected end of input")
			return when (val c = s[pos]) {
				'{' -> obj()
				'[' -> arr()
				'"' -> JsonPrimitive(quoted(c))
				else -> bare()
			}
		}

		fun obj(): JsonObject {
			pos++
			val o = JsonObject()
			while (true) {
				skipSpace()
				if (pos >= s.length) fail("Unclosed object")
				if (s[pos] == '}') { pos++; return o }
				val key = if (s[pos] == '"') quoted('"') else word().ifEmpty { fail("Expected a key") }
				skipSpace()
				if (pos >= s.length || s[pos] != ':') fail("Expected ':'")
				pos++
				o.add(key, value())
				skipSpace()
				if (pos < s.length && s[pos] == ',') pos++
				else if (pos < s.length && s[pos] != '}') fail("Expected ',' or '}'")
			}
		}

		fun arr(): JsonArray {
			pos++
			val a = JsonArray()
			while (true) {
				skipSpace()
				if (pos >= s.length) fail("Unclosed array")
				if (s[pos] == ']') { pos++; return a }
				a.add(value())
				skipSpace()
				if (pos < s.length && s[pos] == ',') pos++
				else if (pos < s.length && s[pos] != ']') fail("Expected ',' or ']'")
			}
		}

		fun quoted(quote: Char): String {
			pos++

			val start = pos
			while (pos < s.length) {
				val c = s[pos]
				if (c == quote) return s.substring(start, pos++)
				if (c == '\\') break
				pos++
			}
			val out = StringBuilder().append(s, start, pos)
			while (true) {
				if (pos >= s.length) fail("Unclosed string")
				val c = s[pos++]
				when (c) {
					quote -> return out.toString()
					'\\' -> {
						if (pos >= s.length) fail("Unclosed string")
						when (val e = s[pos++]) {
							'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
							'b' -> out.append('\b'); 'f' -> out.append('\u000C')
							'u' -> {
								if (pos + 4 > s.length) fail("Bad unicode escape")
								out.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
								pos += 4
							}
							else -> out.append(e)
						}
					}
					else -> out.append(c)
				}
			}
		}

		fun word(): String {
			val start = pos
			while (pos < s.length) {
				val c = s[pos]
				if (c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']' ||
					c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '"') break
				pos++
			}
			return s.substring(start, pos)
		}

		fun bare(): JsonElement {
			val w = word()
			if (w.isEmpty()) fail("Unexpected character '${s[pos]}'")
			return when {
				w == "true" -> JsonPrimitive(true)
				w == "false" -> JsonPrimitive(false)
				w == "null" -> JsonNull.INSTANCE
				isNumber(w) -> JsonPrimitive(LazilyParsedNumber(w))
				else -> JsonPrimitive(w)
			}
		}

		fun isNumber(w: String): Boolean {
			var i = 0
			if (w[i] == '-' || w[i] == '+') { i++; if (i == w.length) return false }
			var digits = 0
			while (i < w.length && w[i].isAsciiDigit()) { i++; digits++ }
			if (i < w.length && w[i] == '.') { i++; while (i < w.length && w[i].isAsciiDigit()) { i++; digits++ } }
			if (digits == 0) return false
			if (i < w.length && (w[i] == 'e' || w[i] == 'E')) {
				i++
				if (i < w.length && (w[i] == '-' || w[i] == '+')) i++
				val expStart = i
				while (i < w.length && w[i].isAsciiDigit()) i++
				if (i == expStart) return false
			}
			return i == w.length
		}

		private fun Char.isAsciiDigit() = this in '0'..'9'
	}
}
