// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

fun <T : JsonElement> List<T>.asJsonArray(): JsonArray = JsonArray(content = this)

fun Iterable<String>.toJsonArray(): JsonArray = JsonArray(content = this.map { text -> JsonPrimitive(text) })
