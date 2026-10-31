// SPDX-License-Identifier: GPL-3.0-or-later
@file:UseSerializers(DashlessUUIDSerializer::class)

package dev.ashtonite365.apex.features.inventory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer

@Serializable
data class GlobalSlotLocks(
	val lockedSlots: MutableSet<Int> = mutableSetOf(),
	val boundSlots: SlotBindings = SlotBindings(),
	val lockedUUIDs: MutableSet<UUID> = mutableSetOf(),

	var migratedFromPerIsland: Boolean = false,
)

object SlotLockStore {
	const val PREFIX = "global-slot-locks"
	const val LEGACY_PREFIX = "locked-slots"

	val json = Json {
		prettyPrint = true
		isLenient = true
		allowTrailingComma = true
		allowComments = true
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	data class MergeReport(
		val sources: Int,
		val lockedSlots: Int,
		val boundPairs: Int,
		val droppedConflictingBinds: Int,
		val lockedUUIDs: Int,
	)

	fun mergeLegacy(legacy: List<LegacyProfileLocks>, into: GlobalSlotLocks = GlobalSlotLocks()): Pair<GlobalSlotLocks, MergeReport> {
		var dropped = 0
		var sources = 0
		for (profile in legacy) {
			into.lockedUUIDs.addAll(profile.lockedUUIDs)
			for (dim in listOf(profile.overworld, profile.rift)) {
				if (dim.lockedSlots.isNotEmpty() || dim.boundSlots.pairs.isNotEmpty()) sources++
				into.lockedSlots.addAll(dim.lockedSlots)
				for (pair in dim.boundSlots.pairs.sortedWith(compareBy({ it.inventory }, { it.hotbar }))) {
					val existing = into.boundSlots.pairs.filter { it.inventory == pair.inventory }
					if (existing.isEmpty()) into.boundSlots.pairs.add(pair)
					else if (pair !in existing) dropped++
				}
			}
		}
		into.migratedFromPerIsland = true
		return into to MergeReport(sources, into.lockedSlots.size, into.boundSlots.pairs.size, dropped, into.lockedUUIDs.size)
	}

	fun decodeLegacyFile(text: String): LegacyProfileLocks? = runCatching {
		val obj = json.parseToJsonElement(text).jsonObject
		val inner = obj[LEGACY_PREFIX] ?: obj
		json.decodeFromJsonElement(LegacyProfileLocks.serializer(), inner)
	}.getOrNull()

	fun encode(data: GlobalSlotLocks): String = json.encodeToString(GlobalSlotLocks.serializer(), data)

	fun decode(text: String): GlobalSlotLocks = json.decodeFromString(GlobalSlotLocks.serializer(), text)

	fun save(file: Path, data: GlobalSlotLocks) {
		Files.createDirectories(file.parent)
		val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
		Files.writeString(tmp, encode(data))
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (e: Exception) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
		}
	}

	fun load(file: Path): GlobalSlotLocks? =
		if (Files.exists(file)) decode(Files.readString(file)) else null

	fun legacyFiles(profilesDir: Path): List<Path> {
		if (!Files.isDirectory(profilesDir)) return listOf()
		return Files.list(profilesDir).use { s ->
			s.filter { Files.isDirectory(it) }
				.map { it.resolve("$LEGACY_PREFIX.json") }
				.filter { Files.exists(it) }
				.sorted()
				.toList()
		}
	}

	fun backupLegacy(profilesDir: Path, backupDir: Path): List<Path> =
		legacyFiles(profilesDir).map { src ->
			val dest = backupDir.resolve(src.parent.fileName.toString()).resolve(src.fileName.toString())
			Files.createDirectories(dest.parent)
			if (!Files.exists(dest)) Files.copy(src, dest)
			dest
		}

	fun migrateOnce(
		current: GlobalSlotLocks,
		file: Path,
		profilesDir: Path,
		backupDir: Path,
		extraLegacy: List<LegacyProfileLocks> = listOf(),
	): MergeReport? {
		if (current.migratedFromPerIsland) return null
		backupLegacy(profilesDir, backupDir)
		val fromDisk = legacyFiles(profilesDir).mapNotNull { runCatching { decodeLegacyFile(Files.readString(it)) }.getOrNull() }
		val (_, report) = mergeLegacy(fromDisk + extraLegacy, current)
		save(file, current)
		return report
	}
}
