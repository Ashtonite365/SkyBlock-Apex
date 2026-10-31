// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class SettingsMigrationStore(
	val dir: Path,
	private val map: MigrationMap,
	private val today: () -> LocalDate = LocalDate::now,
	private val log: (String) -> Unit = {},
	private val moves: Map<String, String> = LayoutMoves.bundled,
) {
	companion object {
		const val TREE_FILE = "settings-v2.json"

		const val LAYOUT_KEY = "_layout"
		const val LAYOUT_VERSION = 1006

		const val FIRST_RUN_KEY = "_firstRun"

		fun layoutOf(tree: JsonObject): Int = (tree[LAYOUT_KEY] as? JsonPrimitive)?.intOrNull ?: 0

		fun moveOf(path: String, moves: Map<String, String>): String? {
			moves[path]?.let { return it }
			for ((from, to) in moves) {
				val mark = from.lastOrNull()
				if (mark != '*' && mark != '#') continue
				val base = from.dropLast(1)
				if (!path.startsWith(base) || path.length == base.length) continue
				val rest = path.substring(base.length)
				if (mark == '#' && !rest.all(Char::isDigit)) continue
				return to.dropLast(1) + rest
			}
			return null
		}

		fun relayout(tree: JsonObject, moves: Map<String, String>): JsonObject {
			if (layoutOf(tree) >= LAYOUT_VERSION) return tree
			val meta = LinkedHashMap<String, JsonElement>()
			val kept = LinkedHashMap<String, JsonElement>()
			val moved = LinkedHashMap<String, JsonElement>()
			val odd = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
			for ((main, subs) in tree) {
				if (main.startsWith("_") || subs !is JsonObject) { meta[main] = subs; continue }
				for ((sub, keys) in subs) {
					if (keys !is JsonObject) { odd.getOrPut(main, ::LinkedHashMap)[sub] = keys; continue }
					for ((key, value) in keys) {
						val path = "$main.$sub.$key"
						val to = moveOf(path, moves)
						if (to == null) kept[path] = value else moved[to] = value
					}
				}
			}
			kept.putAll(moved)
			val nested = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, JsonElement>>>()
			for ((path, value) in kept) {
				val (main, sub, key) = path.split('.', limit = 3)
				nested.getOrPut(main, ::LinkedHashMap).getOrPut(sub, ::LinkedHashMap)[key] = value
			}
			val out = LinkedHashMap<String, JsonElement>()
			for ((k, v) in meta) if (k.startsWith("_")) out[k] = v
			for ((main, subs) in nested) out[main] = JsonObject(subs.mapValues { JsonObject(it.value) } + (odd.remove(main) ?: emptyMap()))
			for ((main, subs) in odd) out[main] = JsonObject(subs)
			for ((k, v) in meta) if (!k.startsWith("_")) out[k] = v
			out[LAYOUT_KEY] = JsonPrimitive(LAYOUT_VERSION)
			return JsonObject(out)
		}
		fun backupName(date: LocalDate) = "config.pre-rewrite-$date.json"
		private val json = Json { prettyPrint = true }

		fun deepMerge(a: JsonObject, b: JsonObject): JsonObject {
			val out = LinkedHashMap<String, JsonElement>(a)
			for ((k, v) in b) {
				val prev = out[k]
				out[k] = if (prev is JsonObject && v is JsonObject) deepMerge(prev, v) else v
			}
			return JsonObject(out)
		}
	}

	private val migrator = ConfigMigrator(map)
	val treeFile: Path get() = dir.resolve(TREE_FILE)

	var lastWrites: List<Path> = emptyList()
		private set

	private fun readTree(): JsonObject? {
		if (!Files.exists(treeFile)) return null
		return try {
			val tree = Json.parseToJsonElement(Files.readString(treeFile)).jsonObject
			val version = (tree[ConfigMigrator.VERSION_KEY] as? JsonPrimitive)?.intOrNull ?: 0
			require(version >= ConfigMigrator.VERSION) { "tree version $version" }
			tree
		} catch (e: Exception) {

			val aside = dir.resolve("$TREE_FILE.broken-${System.currentTimeMillis()}")
			Files.move(treeFile, aside, StandardCopyOption.REPLACE_EXISTING)
			log("Settings tree unreadable (${e.message}); moved to $aside and migrating again")
			null
		}
	}

	private fun write(path: Path, value: JsonObject, writes: MutableList<Path>) {
		val text = json.encodeToString(JsonElement.serializer(), value) + "\n"
		if (Files.exists(path) && Files.readString(path) == text) return
		Files.createDirectories(dir)
		val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
		Files.writeString(tmp, text)
		Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		writes.add(path)
	}

	private fun backupOnce(old: JsonObject, writes: MutableList<Path>) {
		val path = dir.resolve(backupName(today()))
		if (old.isEmpty() || Files.exists(path)) return
		write(path, old, writes)
		log("Wrote settings backup $path")
	}

	private fun buildTree(previous: JsonObject?, current: JsonObject): JsonObject {
		val legacy = (previous?.get(ConfigMigrator.LEGACY_KEY) as? JsonObject)?.let { deepMerge(it, current) } ?: current
		val res = migrator.migrate(legacy)
		if (res.unmapped.isNotEmpty()) log("Settings migration: ${res.unmapped.size} unmapped keys kept under _legacy")
		if (res.conflicts.isNotEmpty()) log("Settings migration conflicts: ${res.conflicts}")
		val out = LinkedHashMap<String, JsonElement>(res.tree)
		previous?.get(FIRST_RUN_KEY)?.let { out[FIRST_RUN_KEY] = it }
		out[LAYOUT_KEY] = JsonPrimitive(LAYOUT_VERSION)
		return JsonObject(out)
	}

	private fun currentTree(writes: MutableList<Path>): JsonObject? {
		val tree = readTree() ?: return null
		if (layoutOf(tree) >= LAYOUT_VERSION) return tree
		val moved = relayout(tree, moves)
		write(treeFile, moved, writes)
		log("Settings tree moved to layout $LAYOUT_VERSION (${moves.size} known path moves)")
		return moved
	}

	fun firstRun(): JsonObject? = readTree()?.get(FIRST_RUN_KEY) as? JsonObject

	fun markFirstRun(profile: String, version: Int): Boolean {
		val writes = mutableListOf<Path>()
		try {
			val tree = currentTree(writes) ?: return false
			if (FIRST_RUN_KEY in tree) return false
			val marker = JsonObject(mapOf("profile" to JsonPrimitive(profile), "version" to JsonPrimitive(version)))
			write(treeFile, JsonObject(LinkedHashMap<String, JsonElement>(tree).apply { put(FIRST_RUN_KEY, marker) }), writes)
			return true
		} finally {
			lastWrites = writes
		}
	}

	fun onLoad(old: JsonObject): JsonObject {
		val writes = mutableListOf<Path>()
		try {
			val tree = currentTree(writes)
			if (tree == null) {
				backupOnce(old, writes)
				write(treeFile, buildTree(null, old), writes)
				return old
			}
			return applyTree(tree, old)
		} finally {
			lastWrites = writes
		}
	}

	fun onSave(current: JsonObject) {
		val writes = mutableListOf<Path>()
		try {
			val previous = currentTree(writes)
			if (previous == null) backupOnce(current, writes)
			write(treeFile, buildTree(previous, current), writes)
		} finally {
			lastWrites = writes
		}
	}

	fun applyTree(tree: JsonObject, old: JsonObject): JsonObject {
		val fromOld = migrator.migrate(old).tree
		var out = old
		for ((main, subs) in tree) {
			if (main.startsWith("_") || subs !is JsonObject) continue
			val section = MainSection.byId(main) ?: continue
			for ((sub, keys) in subs) {
				if (keys !is JsonObject) continue
				for ((key, value) in keys) {
					val path = SettingPath(section, sub, key)
					if (migrator.lookup(fromOld, path) == value) continue
					for (oldKey in map.oldKeysFor(path)) {
						val config = oldKey.substringBefore('.')
						out = deepMerge(out, JsonObject(mapOf(config to JsonObject(mapOf(oldKey.substringAfter('.') to value)))))
					}
				}
			}
		}
		return out
	}
}
