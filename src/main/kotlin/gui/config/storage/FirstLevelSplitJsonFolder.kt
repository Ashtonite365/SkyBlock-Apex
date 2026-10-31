// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config.storage

import java.nio.file.Path
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import dev.ashtonite365.apex.Apex

class FirstLevelSplitJsonFolder(val context: ConfigLoadContext, val folder: Path) {

	var hasCreatedBackup: Boolean = false

	fun backup(cause: String): Unit {
		val first = !hasCreatedBackup.also { hasCreatedBackup = true }
		if (first) context.createBackup(this.folder, cause)
	}

	private inline fun <T> orRecover(what: String, cause: String, fallback: T, action: () -> T): T =
		try {
			action()
		} catch (problem: Exception) {
			context.logFault(what, problem)
			backup(cause)
			fallback
		}

	private fun jsonFiles(): List<Path> = folder.listDirectoryEntries("*.json")

	fun load(): JsonObject = run {
		context.logInfo("Reading split folder $folder")
		if (!folder.exists()) return JsonObject(mapOf())
		orRecover("Reading split folder $folder failed", "failed-load", JsonObject(mapOf())) {
			JsonObject(jsonFiles().mapNotNull(::loadIndividualFile).toMap(LinkedHashMap()))
				.also { context.logInfo("Read split folder $folder") }
		}
	}

	fun loadIndividualFile(path: Path): Pair<String, JsonElement>? = run {
		context.logDebug("Reading $path")
		orRecover("Skipping unreadable $path", "failed-load", null) {
			path.nameWithoutExtension to Apex.json.parseToJsonElement(path.readText())
		}
	}

	fun save(value: JsonObject): Unit {
		context.logInfo("Writing split folder $folder")
		context.logDebug("Contents:\n$value")
		if (!folder.exists()) context.logInfo("Creating $folder").also { folder.createDirectories() }
		val present = jsonFiles()
		val written = value.entries.mapNotNullTo(HashSet()) { (key, element) -> saveIndividualFile(key, element) }
		val orphans = present.filter { it !in written }
		if (orphans.isNotEmpty()) context.logInfo("Removing files without a key")
		orphans.forEach { orphan ->
			context.logInfo("Removing $orphan")
			this.backup("save-deletion")
			runCatching { orphan.deleteExisting() }.exceptionOrNull()?.let { problem ->
				if (problem !is Exception) throw problem
				context.logFault("Removing $orphan failed", problem)
			}
		}
		context.logInfo("Wrote split folder $folder")
	}

	fun saveIndividualFile(name: String, element: JsonElement): Path? =
		orRecover("Writing key $name ($element) failed", "failed-save", null) {
			context.logDebug("Writing key $name")
			folder.resolve("$name.json").also { file ->
				context.ensureWritable(file)
				file.writeText(Apex.json.encodeToString(JsonElement.serializer(), element))
			}
		}
}
