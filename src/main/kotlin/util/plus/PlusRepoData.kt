// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.plus

import dev.ashtonite365.apex.Apex
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement

object PlusRepoData {
	const val BASE = "/assets/apex/plus/"

	private class Memo(private val label: String) {
		private val values = HashMap<String, Any?>()
		private var generation: Int? = null

		@Suppress("UNCHECKED_CAST")
		fun <T> get(key: String, currentGeneration: Int?, read: (String) -> String?, deserializer: DeserializationStrategy<T>): T? = synchronized(values) {
			if (generation != currentGeneration) { values.clear(); generation = currentGeneration }
			if (values.containsKey(key)) return values[key] as T?
			val value = try {
				read(key)?.let { parse(it, deserializer) }
			} catch (e: Throwable) {
				Apex.logger.warn("Failed to load $label $key", e)
				null
			}
			values[key] = value
			value
		}
	}

	private val bundled = Memo("community data")
	private val repo = Memo("community data repo")

	fun readText(name: String): String? {
		val stream = PlusRepoData::class.java.getResourceAsStream("$BASE$name.json") ?: return null
		return stream.use { it.readBytes().decodeToString() }
	}

	fun <T> parse(text: String, deserializer: DeserializationStrategy<T>): T = Apex.json.decodeFromString(deserializer, text)

	fun <T> load(name: String, deserializer: DeserializationStrategy<T>): T? = bundled.get(name, null, ::readText, deserializer)

	fun loadJson(name: String): JsonElement? = load(name, JsonElement.serializer())

	fun readRepoText(path: String): String? = PlusRepoSource.readText(path)

	fun <T> loadRepo(path: String, deserializer: DeserializationStrategy<T>): T? =
		repo.get(path, PlusRepoSource.generation, ::readRepoText, deserializer)

	fun loadRepoJson(path: String): JsonElement? = loadRepo(path, JsonElement.serializer())
}
