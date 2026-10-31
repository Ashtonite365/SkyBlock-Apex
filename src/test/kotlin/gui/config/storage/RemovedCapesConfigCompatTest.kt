// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.gui.config.storage

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.features.chat.ChatTabs
import dev.ashtonite365.apex.gui.config.storage.ConfigHolderLoading
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingsMigrationStore
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig

@ExtendWith(AutoBootstrapExtension::class)
class RemovedCapesConfigCompatTest {
	@TempDir
	lateinit var tmp: Path

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

	private val oldConfig = json("""{"developer-capes":{"show-cape":false},"chat-tabs":{"enabled":true}}""")

	@Test
	fun oldDeveloperCapesKeyIsIgnoredOnLoad() {
		val registered = MainConfigRegistry.registered
		assertTrue(registered.none { (it as? ManagedConfig)?.name == "developer-capes" }, "developer-capes holder still registered")
		val configHolders = registered.filter { it.storageClass == ConfigStorageClass.CONFIG }
		assertTrue(configHolders.size > 100, "only ${configHolders.size} CONFIG holders")

		val store = SettingsMigrationStore(tmp.resolve("settings-rewrite"), MigrationMap.bundled(), { LocalDate.of(2026, 10, 26) })
		assertEquals(oldConfig, store.onLoad(oldConfig))
		val tree = Json.parseToJsonElement(Files.readString(store.treeFile)).jsonObject
		assertFalse("developer-capes" in (tree["misc"]?.jsonObject ?: JsonObject(mapOf())), "capes migrated into the new tree")
		assertEquals(oldConfig, tree[ConfigMigrator.LEGACY_KEY], "old json not kept verbatim under _legacy")
		val res = ConfigMigrator(MigrationMap.bundled()).migrate(oldConfig)
		assertEquals(listOf("developer-capes.show-cape"), res.unmapped)
		Files.writeString(store.treeFile, Json.encodeToString(JsonObject.serializer(), JsonObject(tree + mapOf(
			"misc" to json("""{"developer-capes":{"show-cape":true},"chat-tabs":{"enabled":true}}"""),
		))))
		val configData = store.onLoad(oldConfig)
		assertEquals(true.toString(), configData["chat-tabs"]?.jsonObject?.get("enabled").toString())

		@Suppress("UNCHECKED_CAST")
		val saved = configHolders.associateWith { runCatching { (it as IDataHolder<Unit>).saveTo(Unit) }.getOrNull() }
		try {
			configHolders.forEach { it.clear() }
			ConfigHolderLoading.loadHolders(registered, configData, Unit, ConfigStorageClass.CONFIG)
			val notLoaded = configHolders.filterIsInstance<ManagedConfig>().filter { h -> h.sortedOptions.any { it._actualValue == null } }
			assertEquals(emptyList<String>(), notLoaded.map { it.javaClass.name }, "CONFIG holders left unloaded")
			assertTrue(ChatTabs.TConfig.enabled, "a real option next to the old key did not load")
		} finally {
			for ((h, json) in saved) {
				@Suppress("UNCHECKED_CAST")
				val hh = h as IDataHolder<Unit>
				hh.clear()
				if (json != null) runCatching { hh.loadFrom(Unit, json) }
			}
		}
	}
}
