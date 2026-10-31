// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.data

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object LegacyDataMigration {
	const val MARKER = ".migrated-from-legacy"
	const val LEGACY_ID = "firmament"
	const val NEW_ID = "apex"

	val KEY_RENAMES: Map<String, String> = mapOf(
		"neu-inventory-overlays" to "core-inventory-overlays",
		"neu-misc" to "core-misc",
		"skyhanni-chat-compact" to "plus-chat-compact",
		"skyhanni-chat-extras" to "plus-chat-extras",
		"skyhanni-data" to "plus-data",
		"skyhanni-events" to "plus-events",
		"skyhanni-garden-warps" to "plus-garden-warps",
		"skyhanni-lore" to "plus-lore",
		"skyhanni-misc" to "plus-misc",
		"skyhanni-plots" to "plus-plots",
		"skyhanni-seasonal" to "plus-seasonal",
		"skyhanni-spray" to "plus-spray",
		"skyhanni-visual-words" to "plus-visual-words",
	)

	val PATH_RENAMES: Map<String, String> = KEY_RENAMES + mapOf(
		"skyhanni-repo" to "plus-repo",
		"SkyHanni-REPO-main.zip" to "community-repo-main.zip",
	)

	private val BAR_STYLE_VALUES = mapOf(
		"firmament" to "standard",
		"sba_classic" to "classic",
		"sba_pill" to "pill",
		"skyhanni_gradient" to "gradient",
		"skyhanni_glossy" to "glossy",
		"skyhanni_segmented" to "segmented",
		"skyhanni_segmented_hd" to "segmented_hd",
		"skyhanni_hatched" to "hatched",
		"skyblocker_rounded" to "flat_rounded",
		"odin_accent" to "accent",
	)

	val VALUE_RENAMES: Map<String, Map<String, String>> = mapOf(
		"panel-style" to BAR_STYLE_VALUES,
		"timer-style" to BAR_STYLE_VALUES,
		"bar-style" to BAR_STYLE_VALUES,
		"health-style" to BAR_STYLE_VALUES,
		"mana-style" to BAR_STYLE_VALUES,
		"xp-style" to BAR_STYLE_VALUES,
		"vitality-style" to BAR_STYLE_VALUES,
		"texture-style" to mapOf(
			"FIRMAMENT" to "STANDARD",
			"NEU_TRANSPARENT" to "CLASSIC_TRANSPARENT",
			"NEU_MINECRAFT" to "CLASSIC_MINECRAFT",
			"NEU_DARK" to "CLASSIC_DARK",
			"NEU_CUSTOM" to "CLASSIC_CUSTOM",
		),
	)

	fun renameKey(key: String): String =
		if ('.' in key) key.split('.').joinToString(".") { KEY_RENAMES[it] ?: it } else KEY_RENAMES[key] ?: key

	fun migrateJson(element: JsonElement, parentKey: String? = null): JsonElement = when (element) {
		is JsonObject -> {
			val out = LinkedHashMap<String, JsonElement>()
			for ((k, v) in element) if (renameKey(k) == k) out[k] = migrateJson(v, k)
			for ((k, v) in element) {
				val nk = renameKey(k)
				if (nk != k && nk !in out) out[nk] = migrateJson(v, nk)
			}
			JsonObject(out)
		}
		is JsonArray -> JsonArray(element.map { migrateJson(it, parentKey) })
		is JsonPrimitive -> {
			val last = parentKey?.substringAfterLast('.')
			val map = last?.let { VALUE_RENAMES[it] }
			if (map != null && element.isString) map[element.content]?.let { JsonPrimitive(it) } ?: element else element
		}
	}

	fun migrateJsonObject(obj: JsonObject): JsonObject = migrateJson(obj) as JsonObject

	data class Report(val copied: List<Path>, val skippedExisting: List<Path>, val failed: List<Path>, val ran: Boolean)

	@Volatile private var done = false

	@Synchronized
	fun runOnce(gameDir: Path = Path.of(""), log: (String) -> Unit = {}) {
		if (done) return
		done = true
		runFor(gameDir, log)
	}

	fun runFor(gameDir: Path, log: (String) -> Unit = {}) {
		try {
			val r1 = migrate(gameDir.resolve("config").resolve(LEGACY_ID), gameDir.resolve("config").resolve(NEW_ID))
			val r2 = migrate(gameDir.resolve(".$LEGACY_ID"), gameDir.resolve(".$NEW_ID"))
			if (r1.ran || r2.ran)
				log("Legacy data migration: copied ${r1.copied.size + r2.copied.size} files, kept ${r1.skippedExisting.size + r2.skippedExisting.size} existing, ${r1.failed.size + r2.failed.size} failed")
		} catch (e: Exception) {
			log("Legacy data migration failed: $e")
		}
	}

	private val json = Json { prettyPrint = true; isLenient = true; allowTrailingComma = true; allowComments = true }

	private fun renamePath(rel: Path): Path {
		var out: Path? = null
		for (part in rel) {
			val name = part.toString()
			val renamed = if (name.endsWith(".json")) (PATH_RENAMES[name.removeSuffix(".json")]?.let { "$it.json" } ?: name)
			else PATH_RENAMES[name] ?: name
			out = out?.resolve(renamed) ?: Path.of(renamed)
		}
		return out ?: rel
	}

	fun migrate(old: Path, new: Path): Report {
		if (!Files.isDirectory(old) || Files.exists(new.resolve(MARKER))) return Report(emptyList(), emptyList(), emptyList(), false)
		val copied = mutableListOf<Path>(); val skipped = mutableListOf<Path>(); val failed = mutableListOf<Path>()
		Files.createDirectories(new)
		Files.walkFileTree(old, object : SimpleFileVisitor<Path>() {
			override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
				val target = new.resolve(renamePath(old.relativize(file)).toString())
				if (Files.exists(target)) { skipped.add(target); return FileVisitResult.CONTINUE }
				try {
					Files.createDirectories(target.parent)
					if (file.fileName.toString().endsWith(".json")) {
						val text = Files.readString(file)
						val migrated = runCatching { json.encodeToString(JsonElement.serializer(), migrateJson(json.parseToJsonElement(text))) + "\n" }.getOrNull()
						if (migrated != null) Files.writeString(target, migrated) else Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
					} else {
						Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
					}
					copied.add(target)
				} catch (e: Exception) {
					failed.add(file)
				}
				return FileVisitResult.CONTINUE
			}

			override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
				failed.add(file); return FileVisitResult.CONTINUE
			}
		})
		Files.writeString(new.resolve(MARKER), "Copied from $old on first launch. The old folder was left untouched.\n")
		return Report(copied, skipped, failed, true)
	}

	val KEY_MAPPING_RENAMES: Map<String, String> = mapOf(
		"key.jarvis.open-gui-editor" to "key.apex.open-gui-editor",
	)

	fun migrateOptionsLines(lines: List<String>, renames: Map<String, String> = KEY_MAPPING_RENAMES): List<String>? {
		fun nameOf(line: String): String? = if (line.startsWith("key_") && ':' in line) line.substring(4, line.indexOf(':')) else null
		val saved = lines.mapNotNullTo(HashSet()) { nameOf(it) }
		var changed = false
		val result = lines.map { line ->
			val target = nameOf(line)?.let { renames[it] }
			if (target == null || target in saved) line
			else { changed = true; "key_" + target + line.substring(line.indexOf(':')) }
		}
		return result.takeIf { changed }
	}

	fun migrateOptionsFile(gameDir: Path): Boolean {
		val options = gameDir.resolve("options.txt")
		if (!Files.isRegularFile(options)) return false
		val migrated = migrateOptionsLines(Files.readAllLines(options)) ?: return false
		val pending = options.resolveSibling("options.txt.apex-migrating")
		Files.write(pending, migrated)
		Files.move(pending, options, StandardCopyOption.REPLACE_EXISTING)
		return true
	}
}
