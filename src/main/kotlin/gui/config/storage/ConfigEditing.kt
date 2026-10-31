// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config.storage

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ashtonite365.apex.util.json.intoGson
import dev.ashtonite365.apex.util.json.intoKotlinJson

abstract class JsonSlot {
	abstract fun read(): JsonElement?
	abstract fun write(value: JsonElement)

	class Member(val parent: JsonObject, val key: String) : JsonSlot() {
		override fun read(): JsonElement? = parent[key]
		override fun write(value: JsonElement) = parent.add(key, value)
		override fun toString() = "Member($key)"
	}

	class Entry(val parent: JsonArray, val position: Int) : JsonSlot() {
		override fun read(): JsonElement = parent[position]
		override fun write(value: JsonElement) {
			parent[position] = value
		}
		override fun toString() = "Entry($position)"
	}
}

class ConfigEditor(val targets: List<JsonSlot>) {

	fun transform(mapper: (kotlinx.serialization.json.JsonElement) -> kotlinx.serialization.json.JsonElement) {
		for (slot in targets) slot.write(mapper(slot.read()!!.intoKotlinJson()).intoGson())
	}

	fun move(source: String, destination: String) {
		if (source == destination) return
		val from = steps(source)
		val to = steps(destination)
		for (slot in targets) {
			val moving = takeOut(slot, from) ?: continue
			putIn(slot, to, moving)
		}
	}

	private fun steps(path: String): List<String> = path.split('.').filterNot(String::isEmpty)

	private fun takeOut(slot: JsonSlot, from: List<String>): JsonElement? {
		val whole = slot.read()
		if (from.isEmpty()) {
			slot.write(JsonObject())
			return whole
		}
		var cursor = whole
		for (step in from.subList(0, from.size - 1)) {
			val next = (cursor as JsonObject).get(step)
			if (next != null) cursor = next
		}
		return (cursor as JsonObject).remove(from.last())?.deepCopy()
	}

	private fun putIn(slot: JsonSlot, to: List<String>, moving: JsonElement) {
		if (to.isEmpty()) {
			slot.write(moving)
			return
		}
		var holder = slot.read() as JsonObject
		for (step in to.subList(0, to.size - 1)) {
			holder = (holder.get(step) ?: JsonObject().also { holder.add(step, it) }) as JsonObject
		}
		val leaf = to.last()
		check(!holder.has(leaf)) { "Cannot overwrite $holder.$leaf with $moving" }
		holder.add(leaf, moving)
	}

	fun at(path: String, edits: ConfigEditor.() -> Unit) = at(path).edits()

	fun at(path: String): ConfigEditor {
		var current = targets
		for (step in steps(path)) {
			current = current.flatMap { slot ->
				when (val node = slot.read()) {
					is JsonObject -> if (step == "*") node.keySet().map { JsonSlot.Member(node, it) } else listOf(JsonSlot.Member(node, step))
					is JsonArray -> if (step == "*") List(node.size()) { JsonSlot.Entry(node, it) } else listOf(JsonSlot.Entry(node, step.toInt()))
					else -> throw IllegalStateException("Cannot expand a json primitive $node at $path")
				}
			}
		}
		return ConfigEditor(current)
	}
}
