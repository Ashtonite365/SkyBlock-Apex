// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config.storage

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.debug.DebugLogger
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingsMigrationStore
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.SkyBlockSession.NULL_UUID
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.IConfigProvider
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.LegacyDataMigration
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig
import dev.ashtonite365.apex.util.json.intoGson
import dev.ashtonite365.apex.util.json.intoKotlinJson

object ApexConfigLoader {
	const val currentConfigVersion = 1006
	private const val PRE_SPLIT_VERSION = 995

	val configFolder: Path = Path("config", "apex").toAbsolutePath()
	val storageFolder: Path = configFolder.resolve("storage")
	val profilePath: Path = configFolder.resolve("profiles")
	val configVersionFile: Path = configFolder.resolve("config.version")
	val settingsMigration by lazy {
		SettingsMigrationStore(configFolder.resolve("settings-rewrite"), MigrationMap.bundled(), log = { line -> debugLogger.log(line) })
	}
	val debugLogger: DebugLogger = DebugLogger("config")

	val allConfigs: List<IDataHolder<*>> = IConfigProvider.providers.allValidInstances.flatMap { provider ->
		provider.configs
	}

	var configPromise: CompletableFuture<Void?>? = null
	private var dirtySince: TimeMark = TimeMark.farPast()

	private fun folderOf(context: ConfigLoadContext, path: Path) = readFolder(context, path)

	private fun readFolder(context: ConfigLoadContext, path: Path): JsonObject {
		if (!path.isDirectory()) return JsonObject(emptyMap())
		val out = LinkedHashMap<String, JsonElement>()
		for (file in path.listDirectoryEntries("*.json")) {
			try {
				out[file.nameWithoutExtension] = Apex.json.parseToJsonElement(file.readText())
			} catch (e: Exception) {
				context.logFault("Could not read ${file.name}", e)
			}
		}
		return JsonObject(out)
	}

	private fun writeFolder(context: ConfigLoadContext, path: Path, data: JsonObject) {
		for ((name, value) in data) {
			val file = path.resolve("$name.json")
			context.ensureWritable(file)
			file.writeText(Apex.json.encodeToString(JsonElement.serializer(), value))
		}
	}

	private fun profileFolders(): List<Path> =
		if (profilePath.exists()) profilePath.listDirectoryEntries().filter { it.isDirectory() } else emptyList()

	fun loadConfig(): Unit {
		val folderExisted = configFolder.exists()
		if (folderExisted && !configVersionFile.exists()) convertPreSplitInstall()
		updateConfigs()
		ConfigLoadContext("load-${System.currentTimeMillis()}").use { context ->
			var settings = folderOf(context, configFolder)
			try {
				settings = settingsMigration.onLoad(settings)
			} catch (e: Exception) {
				context.logFault("Settings migration failed on load; using the old config as is", e)
			}
			loadConfigFromData(settings, Unit, ConfigStorageClass.CONFIG)
			loadConfigFromData(folderOf(context, storageFolder), Unit, ConfigStorageClass.STORAGE)
			loadConfigFromData<UUID>(JsonObject(emptyMap()), null, ConfigStorageClass.PROFILE)
			for (folder in profileFolders()) {
				val id = runCatching { UUID.fromString(folder.name) }.getOrNull() ?: continue
				loadConfigFromData(folderOf(context, folder), id, ConfigStorageClass.PROFILE)
			}
			ConfigHolderLoading.afterAllLoaded(allConfigs)
		}
		if (FreshInstallDefaults.shouldApply(folderExisted, runCatching { settingsMigration.firstRun() }.getOrNull()))
			applyFreshInstallDefaults()
	}

	private fun applyFreshInstallDefaults() {
		try {
			val missed = FreshInstallDefaults.apply(allConfigs.filterIsInstance<ManagedConfig>())
			if (missed.isNotEmpty()) debugLogger.log("Fresh-install defaults skipped: $missed")
			saveAll()
			settingsMigration.markFirstRun(FreshInstallDefaults.PROFILE, currentConfigVersion)
		} catch (e: Exception) {
			Faults.logFault("Fresh-install defaults failed; keeping the code defaults", e)
		}
	}

	fun <T> loadConfigFromData(json: JsonObject, owner: T?, kind: ConfigStorageClass) =
		ConfigHolderLoading.loadHolders(holders = allConfigs, json, owner, kind)

	fun <T> collectConfigFromData(key: T, storageClass: ConfigStorageClass): JsonObject =
		allConfigs.filter { it.storageClass == storageClass }.fold(JsonObject(emptyMap())) { acc, holder ->
			@Suppress("UNCHECKED_CAST")
			mergeJson(acc, (holder as IDataHolder<T>).saveTo(key))
		}

	fun collectAllProfileIds(): Set<UUID> = allConfigs.filter { it.storageClass == ConfigStorageClass.PROFILE && it.isLoaded }
		.flatMapTo(LinkedHashSet()) { holder -> (holder as ProfileKeyedConfig<*>).keys() }

	fun saveAll(): Unit = ConfigLoadContext("save-${System.currentTimeMillis()}").use { context ->
		val settings = collectConfigFromData(Unit, ConfigStorageClass.CONFIG)
		writeFolder(context, configFolder, settings)
		try {
			settingsMigration.onSave(settings)
		} catch (e: Exception) {
			context.logFault("Settings migration failed on save", e)
		}
		writeFolder(context, storageFolder, collectConfigFromData(Unit, ConfigStorageClass.STORAGE))
		for (id in collectAllProfileIds())
			writeFolder(context, profilePath.resolve(id.toString()), collectConfigFromData(id, ConfigStorageClass.PROFILE))
		writeConfigVersion()
	}

	fun mergeJson(a: JsonObject, b: JsonObject): JsonObject = JsonObject((a.keys + b.keys).associateWith { union(a[it], b[it]) })

	private fun union(left: JsonElement?, right: JsonElement?): JsonElement {
		if (left == null) return right!!
		if (right == null) return left
		return mergeJson(left as JsonObject, right as JsonObject)
	}

	fun updateConfigs(): Unit {
		if (!configVersionFile.exists()) return
		val text = configVersionFile.readText()
		val from = text.trim().substringBefore(' ').toIntOrNull() ?: run {
			Faults.logFault("Could not parse old config version from '$text'")
			return
		}
		if (from >= currentConfigVersion) return
		ConfigLoadContext("update-from-$from-to-$currentConfigVersion-${System.currentTimeMillis()}").use { context ->
			upgradeFolder(context, from, ConfigStorageClass.CONFIG, configFolder)
			upgradeFolder(context, from, ConfigStorageClass.STORAGE, storageFolder)
			for (folder in profileFolders()) upgradeFolder(context, from, ConfigStorageClass.PROFILE, folder)
			writeConfigVersion()
		}
	}

	fun writeConfigVersion() = configVersionFile.createParentDirectories().writeText("$currentConfigVersion apex")

	private fun upgradeFolder(context: ConfigLoadContext, from: Int, storageClass: ConfigStorageClass, path: Path) {
		if (from >= currentConfigVersion) {
			context.logInfo("$storageClass at $path is already at version $from")
			return
		}
		context.logInfo("Upgrading $storageClass at $path from version $from")
		context.createBackup(path, "update-from-$from")
		var data = folderOf(context, path)
		for (step in from + 1..currentConfigVersion) {
			val event = ConfigFixEvent.publish(ConfigFixEvent(storageClass, step, data.intoGson().asJsonObject))
			data = event.data.intoKotlinJson().jsonObject
		}
		writeFolder(context, path, data)
	}

	private fun convertPreSplitInstall() {
		debugLogger.log("Importing legacy config")
		val storageTopics = setOf("inventory-buttons", "macros")
		val legacy = configFolder.resolveSibling("apex-legacy-config-${System.currentTimeMillis()}")
		configFolder.moveTo(legacy)
		configFolder.createDirectories()
		for (file in legacy.listDirectoryEntries("*.json")) {
			val target = if (file.nameWithoutExtension in storageTopics) storageFolder.resolve(file.name) else configFolder.resolve(file.name)
			target.createParentDirectories()
			file.copyTo(target)
		}
		val oldProfiles = legacy.resolve("profiles")
		if (oldProfiles.isDirectory()) {
			for (topic in oldProfiles.listDirectoryEntries().filter { it.isDirectory() }) {
				for (file in topic.listDirectoryEntries("*.json")) {
					val target = profilePath.resolve(file.nameWithoutExtension).resolve("${topic.name}.json")
					target.createParentDirectories()
					file.copyTo(target)
				}
			}
		}
		configVersionFile.writeText("$PRE_SPLIT_VERSION LEGACY")
	}

	@Subscribe
	fun onTick(event: TickEvent): Unit {
		val pending = configPromise ?: return
		val waited = dirtySince.passedTime()
		if (waited < 1.seconds) return
		if (!pending.isDone && waited < 3.seconds) return
		debugLogger.log("Debounce over, saving every config")
		configPromise = null
		saveAll()
	}

	fun markDirty(holder: IDataHolder<*>, waitFor: CompletableFuture<Void?>? = null) {
		debugLogger.log("Save requested")
		dirtySince = TimeMark.now()
		configPromise = waitFor ?: CompletableFuture.completedFuture(null)
	}
}
