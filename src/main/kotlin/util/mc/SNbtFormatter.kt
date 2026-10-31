package dev.ashtonite365.apex.util.mc

import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.EndTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

class SNbtFormatter private constructor(private val out: StringBuilder) {
	private fun tabs(depth: Int) {
		repeat(depth) { out.append('\t') }
	}

	private inline fun <E> block(open: String, close: Char, entries: Iterator<E>, depth: Int, writeEntry: (E) -> Unit) {
		out.append(open).append('\n')
		while (entries.hasNext()) {
			val entry = entries.next()
			tabs(depth + 1)
			writeEntry(entry)
			if (entries.hasNext()) out.append(',')
			out.append('\n')
		}
		tabs(depth)
		out.append(close)
	}

	private fun sequence(marker: String, tag: CollectionTag, depth: Int) =
		block("[$marker", ']', tag.iterator(), depth) { write(it, depth + 1) }

	private fun write(tag: Tag, depth: Int) {
		when (tag) {
			is StringTag -> out.append(StringTag.quoteAndEscape(tag.value))
			is ByteTag -> out.append(tag.intValue()).append('b')
			is ShortTag -> out.append(tag.intValue()).append('s')
			is IntTag -> out.append(tag.intValue())
			is LongTag -> out.append(tag.longValue()).append('L')
			is FloatTag -> out.append(tag.floatValue()).append('f')
			is DoubleTag -> out.append(tag.doubleValue()).append('d')
			is ByteArrayTag -> sequence("B;", tag, depth)
			is IntArrayTag -> sequence("I;", tag, depth)
			is LongArrayTag -> sequence("L;", tag, depth)
			is ListTag -> sequence("", tag, depth)
			is CompoundTag -> block("{", '}', tag.keySet().sorted().iterator(), depth) { key ->
				out.append(escapeName(key)).append(": ")
				write(tag.get(key)!!, depth + 1)
			}
			is EndTag -> out.append("END")
			else -> error("Unknown tag type ${tag.javaClass.name}")
		}
	}

	companion object {
		fun prettify(nbt: Tag): String {
			val out = StringBuilder()
			SNbtFormatter(out).write(nbt, 0)
			return out.toString()
		}

		fun Tag.toPrettyString() = prettify(this)

		val SIMPLE_NAME = Regex("[A-Za-z0-9._+-]+")

		fun escapeName(key: String): String = when {
			SIMPLE_NAME.matches(key) -> key
			else -> StringTag.quoteAndEscape(key)
		}
	}
}
