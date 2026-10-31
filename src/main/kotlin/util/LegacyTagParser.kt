package dev.ashtonite365.apex.util

import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

class LegacyTagParser private constructor(private val text: String) {
	private var pos = 0

	data class MalformedTag(val source: String, val offset: Int, val reason: String) :
		Exception("$reason at $offset in `$source`.")

	private fun fail(reason: String, at: Int = pos): Nothing = throw MalformedTag(text, at, reason)

	private fun current(): Char = if (pos < text.length) text[pos] else fail("Unexpected end of input")

	private fun skipWhitespace() {
		while (pos < text.length && text[pos].isWhitespace()) pos++
	}

	private fun readCompound(): CompoundTag {
		if (current() != '{') fail("Expected '{’ at start of tag")
		pos++
		val out = CompoundTag()
		while (true) {
			skipWhitespace()
			if (current() == '}') {
				pos++
				return out
			}
			val name = if (current() == '"') readQuoted() else readBareKey()
			skipWhitespace()
			if (current() != ':') fail("Expected ':' after identifier in tag")
			pos++
			out.put(name, readValue())
			if (current() == ',') pos++
		}
	}

	private fun readBareKey(): String {
		val from = pos
		while (current() != ':' && !current().isWhitespace()) pos++
		return text.substring(from, pos)
	}

	private fun readList(): ListTag {
		pos++
		val out = ListTag()
		while (true) {
			skipWhitespace()
			if (current() == ']') {
				pos++
				return out
			}
			skipIndexPrefix()
			out.add(readValue())
			if (current() == ',') pos++
		}
	}

	private fun skipIndexPrefix() {
		var end = pos
		while (end < text.length && (text[end].isDigit() || text[end] == '-')) end++
		if (end == pos) return
		var colon = end
		while (colon < text.length && text[colon].isWhitespace()) colon++
		if (colon < text.length && text[colon] == ':') pos = colon + 1
	}

	private fun readValue(): Tag {
		skipWhitespace()
		val c = current()
		return when {
			c == '{' -> readCompound()
			c == '[' -> readList()
			c == '"' -> StringTag.valueOf(readQuoted())
			c == '-' || c.isDigit() -> readNumber()
			else -> fail("Unexpected token found. Expected start of new element")
		}
	}

	private fun readQuoted(): String {
		pos++
		val out = StringBuilder()
		while (true) {
			if (pos >= text.length) fail("Unfinished string")
			val c = text[pos++]
			when (c) {
				'"' -> return out.toString()
				'\\' -> {
					if (pos >= text.length) fail("Unfinished backslash escape")
					val escaped = text[pos]
					if (escaped != '"' && escaped != '\\') fail("Invalid backslash escape '$escaped'")
					out.append(escaped)
					pos++
				}
				else -> out.append(c)
			}
		}
	}

	private fun readNumber(): NumericTag {
		val from = pos
		var signOk = true
		var dotOk = true
		while (true) {
			val c = current()
			when {
				signOk && (c == '-' || c == '+') -> signOk = false
				dotOk && c == '.' -> { dotOk = false; signOk = false }
				c.isDigit() -> signOk = false
				else -> break
			}
			pos++
		}
		if (current() in NUMBER_SUFFIXES) pos++
		val literal = text.substring(from, pos)
		val suffix = literal.last().takeIf { it in NUMBER_SUFFIXES }?.lowercaseChar()
		val body = if (suffix != null) literal.dropLast(1) else literal
		val integral = INTEGER.matches(body)
		val decimal = DECIMAL.matches(body)
		return when {
			suffix == 'b' && integral -> ByteTag.valueOf(body.toByte())
			suffix == 's' && integral -> ShortTag.valueOf(body.toShort())
			suffix == 'l' && integral -> LongTag.valueOf(body.toLong())
			suffix == null && integral -> IntTag.valueOf(body.toInt())
			suffix == null && decimal -> DoubleTag.valueOf(body.toDouble())
			suffix == 'f' && decimal -> FloatTag.valueOf(body.toFloat())
			suffix == 'd' && decimal -> DoubleTag.valueOf(body.toDouble())
			else -> error("Could not properly parse numeric tag '$literal', despite passing rough verification. This is a bug in the LegacyTagParser")
		}
	}

	companion object {
		private const val NUMBER_SUFFIXES = "dDbBfFlLsS"
		private val INTEGER = Regex("[-+]?[0-9]+")
		private val DECIMAL = Regex("[-+]?[0-9]*\\.?[0-9]+")

		fun parse(string: String): CompoundTag {
			val parser = LegacyTagParser(string)
			parser.skipWhitespace()
			return parser.readCompound()
		}
	}
}
