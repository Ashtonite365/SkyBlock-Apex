// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import com.google.gson.internal.LazilyParsedNumber
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import java.util.stream.Stream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.streams.asSequence

class KJsonOps
	: DynamicOps<JsonElement> {
	companion object Shared {
		val INSTANCE: KJsonOps = KJsonOps()
	}

	private fun <R> fail(message: String): DataResult<R> = DataResult.error { message }

	private val JsonElement.isBooleanLiteral: Boolean
		get() = this is JsonPrimitive && this.booleanOrNull != null

	override fun empty(): JsonElement = JsonNull

	override fun createNumeric(num: Number): JsonElement = JsonPrimitive(value = num)

	override fun createString(str: String): JsonElement = JsonPrimitive(value = str)

	override fun createBoolean(value: Boolean): JsonElement = JsonPrimitive(value = value)

	override fun createList(stream: Stream<JsonElement>): JsonElement = JsonArray(content = stream.toList())

	override fun createMap(
		map: Stream<Pair<JsonElement, JsonElement>>,
	): JsonElement {
		val entries = LinkedHashMap<String, JsonElement>()
		map.forEach { entry -> entries[(entry.first as JsonPrimitive).content] = entry.second }
		return JsonObject(content = entries)
	}

	override fun remove(input: JsonElement, key: String): JsonElement =
		if (input !is JsonObject) input else JsonObject(content = input.filterKeys { name -> name != key })

	override fun getStringValue(input: JsonElement): DataResult<String> = when {
		input is JsonPrimitive && input.isString -> DataResult.success(input.content)
		else -> fail("Not a string: $input")
	}

	override fun getNumberValue(input: JsonElement): DataResult<Number> = when {
		input is JsonPrimitive && !input.isString && !input.isBooleanLiteral -> DataResult.success(LazilyParsedNumber(input.content))
		else -> fail("not a number: $input")
	}

	override fun getBooleanValue(input: JsonElement): DataResult<Boolean> =
		(input as? JsonPrimitive)?.booleanOrNull?.let { flag -> DataResult.success(flag) } ?: fail("Not a boolean: $input")

	override fun getStream(input: JsonElement): DataResult<Stream<JsonElement>> = when (input) {
		is JsonArray -> DataResult.success(input.stream())
		else -> fail("Not a json array: $input")
	}

	override fun getMapValues(input: JsonElement): DataResult<Stream<Pair<JsonElement, JsonElement>>> = when (input) {
		is JsonObject -> DataResult.success(input.entries.stream().map { (name, value) -> Pair.of(createString(name), value) })
		else -> fail("Not a JSON object: $input")
	}

	override fun mergeToList(list: JsonElement, value: JsonElement): DataResult<JsonElement> = when {
		list == empty() -> DataResult.success(JsonArray(content = listOf(value)))
		list is JsonArray -> DataResult.success(JsonArray(content = list + value))
		else -> fail("mergeToList called with not a list: $list")
	}

	override fun mergeToMap(
		map: JsonElement,
		key: JsonElement,
		value: JsonElement,
	): DataResult<JsonElement> {
		val acceptedKey = (key as? JsonPrimitive)?.takeUnless { k -> k.isString }
			?: return fail("key is not a string: $key")
		val addition = mapOf(acceptedKey.content to value)
		return when {
			map == empty() -> DataResult.success(JsonObject(content = addition))
			map is JsonObject -> DataResult.success(JsonObject(content = map + addition))
			else -> fail("mergeToMap called with not a map: $map")
		}
	}

	override fun <U : Any?> convertTo(output: DynamicOps<U>, input: JsonElement): U = when {
		input is JsonObject -> output.createMap(input.entries.stream().map { (name, value) ->
			Pair.of(output.createString(name), convertTo(output, value))
		})
		input is JsonArray -> output.createList(input.stream().map { element -> convertTo(output, element) })
		input is JsonNull -> output.empty()
		input is JsonPrimitive && input.isString -> output.createString(input.content)
		input is JsonPrimitive && input.isBooleanLiteral -> output.createBoolean(input.booleanOrNull!!)
		else -> error("Unknown json value: $input")
	}
}
