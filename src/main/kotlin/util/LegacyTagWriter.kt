package dev.ashtonite365.apex.util

import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.EndTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import dev.ashtonite365.apex.util.mc.SNbtFormatter.Companion.SIMPLE_NAME

class LegacyTagWriter( val compact: Boolean) {

	private fun write(out: StringBuilder, tag: Tag, depth: Int) {
		when (tag) {
			is EndTag -> Unit
			is ByteTag -> out.append(tag.value()).append('B')
			is ShortTag -> out.append(tag.value()).append('S')
			is IntTag -> out.append(tag.value())
			is LongTag -> out.append(tag.value()).append('L')
			is FloatTag -> out.append(tag.value()).append('F')
			is DoubleTag -> out.append(tag.value()).append('D')
			is StringTag -> out.append(quote(tag.value()))
			is CompoundTag -> {
				val keys = tag.keySet().sorted()
				writeEntries(out, '{', '}', keys.size, depth) { i ->
					val key = keys[i]
					writeKey(out, if (SIMPLE_NAME.matches(key)) key else quote(key))
					write(out, tag.get(key)!!, depth + 1)
				}
			}
			is CollectionTag -> {
				val items = tag
				writeEntries(out, '[', ']', items.size(), depth) { i ->
					writeKey(out, i.toString())
					write(out, items.get(i), depth + 1)
				}
			}
			else -> out.append(tag.toString())
		}
	}

	private fun lineBreak(out: StringBuilder, depth: Int) {
		if (compact) return
		out.append('\n')
		repeat(depth) { out.append(INDENT) }
	}

	private fun writeEntries(out: StringBuilder, open: Char, close: Char, count: Int, depth: Int, entry: (Int) -> Unit) {
		out.append(open)
		lineBreak(out, depth + 1)
		for (i in 0 until count) {
			if (i > 0) {
				out.append(',')
				lineBreak(out, depth + 1)
			}
			entry(i)
		}
		if (count > 0) lineBreak(out, depth)
		out.append(close)
	}

	private fun writeKey(out: StringBuilder, key: String) {
		out.append(key).append(':')
		if (!compact) out.append(' ')
	}

	companion object Notation {
		private const val INDENT = "  "

		fun stringify(tag: Tag, compact: Boolean): String =
			StringBuilder().also { LegacyTagWriter(compact).write(it, tag, 0) }.toString()

		fun Tag.toLegacyString(pretty: Boolean = false): String = stringify(this, !pretty)

		private fun quote(text: String): String = JsonPrimitive(text).toString()
	}
}
