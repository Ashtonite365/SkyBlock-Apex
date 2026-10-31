// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import kotlinx.serialization.json.JsonElement as KElement
import kotlinx.serialization.json.JsonArray as KArray
import kotlinx.serialization.json.JsonNull as KNull
import kotlinx.serialization.json.JsonObject as KObject
import kotlinx.serialization.json.JsonPrimitive as KPrimitive

fun JsonElement.intoKotlinJson(): KElement = when {
	this is JsonNull -> KNull
	this is JsonObject -> KObject(content = LinkedHashMap<String, KElement>().also { into ->
		for ((name, value) in this.entrySet()) into[name] = value.intoKotlinJson()
	})
	this is JsonArray -> KArray(content = this.map { element -> element.intoKotlinJson() })
	this is JsonPrimitive && this.isString -> KPrimitive(value = this.asString)
	this is JsonPrimitive && this.isBoolean -> KPrimitive(value = this.asBoolean)
	this is JsonPrimitive -> KPrimitive(value = this.asNumber)
	else -> throw IllegalStateException("Unknown json variant $this")
}

fun KElement.intoGson(): JsonElement = when (this) {
	is KNull -> JsonNull.INSTANCE
	is KPrimitive -> when {
		this.isString -> JsonPrimitive(this.content)
		this.content == "true" || this.content == "false" -> JsonPrimitive(this.content == "true")
		else -> JsonPrimitive(LazilyParsedNumber(this.content))
	}
	is KObject -> JsonObject().apply { this@intoGson.forEach { (name, value) -> add(name, value.intoGson()) } }
	is KArray -> JsonArray().apply { this@intoGson.forEach { element -> add(element.intoGson()) } }
}
