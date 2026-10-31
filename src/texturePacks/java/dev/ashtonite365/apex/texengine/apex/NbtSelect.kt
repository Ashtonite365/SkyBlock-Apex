/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: selecting and testing NBT values from pack files (written from SPEC §3.5).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.util.Base64
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

class NbtPath private constructor(private val steps: List<String>) {
	fun select(root: Tag): List<Tag> {
		var current = listOf(root)
		for (step in steps) {
			if (current.isEmpty()) break
			current = current.flatMap { step(it, step) }
		}
		return current
	}

	private fun step(node: Tag, step: String): List<Tag> = when (step) {
		"*" -> when (node) {
			is CompoundTag -> node.keySet().mapNotNull { node.get(it) }
			is CollectionTag -> (0 until node.size()).map { node.get(it) }
			else -> listOf()
		}

		"*json" -> {
			val text = (node as? StringTag)?.value()
			if (text == null) listOf() else runCatching {
				listOf(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, JsonParser.parseString(text)))
			}.getOrElse { listOf() }
		}

		"*base64" -> {
			val text = (node as? StringTag)?.value()
			if (text == null) listOf() else runCatching {
				listOf(StringTag.valueOf(String(Base64.getDecoder().decode(text.trim()))))
			}.getOrElse { listOf() }
		}

		else -> when (node) {
			is CompoundTag -> listOfNotNull(node.get(step))
			is CollectionTag -> step.toIntOrNull()?.takeIf { it in 0 until node.size() }?.let { listOf(node.get(it)) } ?: listOf()
			else -> listOf()
		}
	}

	companion object {
		fun read(json: JsonElement): NbtPath {
			val parts = when {
				json.isJsonArray -> json.asJsonArray.map { it.asString }
				json.isJsonPrimitive -> json.asString.split('.')
				else -> badPack("NBT path must be a string or an array")
			}
			return NbtPath(parts.filter { it.isNotEmpty() })
		}
	}
}

fun interface NbtTest {
	fun test(tag: Tag): Boolean

	companion object {
		private val typedKeys = listOf("string", "int", "float", "double", "long", "short", "byte")

		fun read(json: JsonElement, ignoredKeys: Set<String> = setOf()): NbtTest {
			if (json.isJsonPrimitive) {
				val prim = json.asJsonPrimitive
				if (prim.isString) {
					val expected = prim.asString
					return NbtTest { (it as? StringTag)?.value() == expected }
				}
				if (prim.isNumber) {
					val expected = prim.asLong
					return NbtTest {
						(it is ByteTag || it is ShortTag || it is IntTag || it is LongTag) && (it as NumericTag).longValue() == expected
					}
				}
				badPack("NBT test must be a string, number or object")
			}
			if (!json.isJsonObject) badPack("NBT test must be a string, number or object")
			val obj = json.asJsonObject
			val present = typedKeys.filter { obj.has(it) }
			val unknown = obj.keySet().filter { it !in typedKeys && it !in ignoredKeys }
			if (present.size != 1 || unknown.isNotEmpty()) badPack("NBT test needs exactly one typed key")
			val key = present.single()
			val value = obj.get(key)
			if (key == "string") {
				val text = TextMatch.read(value)
				return NbtTest { it is StringTag && text.test(it.value()) }
			}
			val kind: (Tag) -> Boolean = when (key) {
				"int" -> { t -> t is IntTag }
				"float" -> { t -> t is FloatTag }
				"double" -> { t -> t is DoubleTag }
				"long" -> { t -> t is LongTag }
				"short" -> { t -> t is ShortTag }
				else -> { t -> t is ByteTag }
			}
			val inRange = readNumber(value)
			return NbtTest { kind(it) && inRange((it as NumericTag).doubleValue()) }
		}

		private fun readNumber(json: JsonElement): (Double) -> Boolean {
			if (json.isJsonPrimitive && json.asJsonPrimitive.isNumber) {
				val exact = json.asDouble
				return { it == exact }
			}
			if (!json.isJsonObject) badPack("typed NBT value must be a number or a range object")
			val obj: JsonObject = json.asJsonObject
			val min = obj.get("min")?.asDouble
			val max = obj.get("max")?.asDouble
			if (min == null && max == null) badPack("NBT range needs 'min' or 'max'")
			val minOpen = obj.get("minExclusive")?.asBoolean ?: false
			val maxOpen = obj.get("maxExclusive")?.asBoolean ?: true
			return { v ->
				(min == null || (if (minOpen) v > min else v >= min)) && (max == null || (if (maxOpen) v < max else v <= max))
			}
		}
	}
}

class NbtQuery(val path: NbtPath, val test: NbtTest) {
	fun matches(root: Tag) = path.select(root).any(test::test)

	companion object {
		fun read(obj: JsonObject, extraKeys: Set<String> = setOf()): NbtQuery {
			val path = NbtPath.read(obj.get("path") ?: badPack("NBT query needs a 'path'"))
			val match = obj.get("match")
			val test = if (match != null) NbtTest.read(match) else NbtTest.read(obj, setOf("path") + extraKeys)
			return NbtQuery(path, test)
		}
	}
}
