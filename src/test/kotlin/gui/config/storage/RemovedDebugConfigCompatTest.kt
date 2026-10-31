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
class RemovedDebugConfigCompatTest {
	@TempDir
	lateinit var tmp: Path

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

	private val removedKeys = listOf(
		"developer.auto-rebuild",
		"power-user.show-item-id", "power-user.copy-item-id", "power-user.copy-nbt-data", "power-user.entity-data",
		"power-user.export-item-stack", "power-user.highlight-non-overlay", "power-user.slot-numbers",
		"power-user.copy-animated-skins",
	)

	private val oldConfig = json(
		"""{"developer":{"auto-rebuild":true},
			|"power-user":{"show-item-id":true,"copy-item-id":{"keyCode":75,"shift":false,"ctrl":true,"alt":false},
			|"copy-nbt-data":{"keyCode":-1},"entity-data":{"keyCode":-1},"export-item-stack":{"keyCode":-1},
			|"highlight-non-overlay":true,"slot-numbers":{"keyCode":-1},"copy-animated-skins":true},
			|"chat-tabs":{"enabled":true}}""".trimMargin()
	)

	@Test
	fun oldDebugKeysAreIgnoredOnLoad() {
		val registered = MainConfigRegistry.registered
		for (name in listOf("developer", "power-user"))
			assertTrue(registered.none { (it as? ManagedConfig)?.name == name }, "$name holder still registered")
		val configHolders = registered.filter { it.storageClass == ConfigStorageClass.CONFIG }
		assertTrue(configHolders.size > 100, "only ${configHolders.size} CONFIG holders")

		val res = ConfigMigrator(MigrationMap.bundled()).migrate(oldConfig)
		assertEquals(removedKeys.toSet(), res.unmapped.toSet())

		val store = SettingsMigrationStore(tmp.resolve("settings-rewrite"), MigrationMap.bundled(), { LocalDate.of(2026, 10, 26) })
		assertEquals(oldConfig, store.onLoad(oldConfig))
		val tree = Json.parseToJsonElement(Files.readString(store.treeFile)).jsonObject
		val misc = tree["misc"]?.jsonObject ?: JsonObject(mapOf())
		assertFalse("developer" in misc, "developer migrated into the new tree")
		assertFalse("power-user" in misc, "power-user migrated into the new tree")
		assertEquals(oldConfig, tree[ConfigMigrator.LEGACY_KEY], "old json not kept verbatim under _legacy")

		Files.writeString(store.treeFile, Json.encodeToString(JsonObject.serializer(), JsonObject(tree + mapOf(
			"misc" to json("""{"developer":{"auto-rebuild":true},"power-user":{"show-item-id":true},"chat-tabs":{"enabled":true}}"""),
		))))
		val configData = store.onLoad(oldConfig)
		assertEquals(true.toString(), configData["chat-tabs"]?.jsonObject?.get("enabled").toString())

		@Suppress("UNCHECKED_CAST")
		val saved = configHolders.associateWith { runCatching { (it as IDataHolder<Unit>).saveTo(Unit) }.getOrNull() }
		try {
			configHolders.forEach { it.clear() }
			ConfigHolderLoading.loadHolders(registered, configData, Unit, ConfigStorageClass.CONFIG)
			ConfigHolderLoading.loadHolders(registered, oldConfig, Unit, ConfigStorageClass.CONFIG)
			val notLoaded = configHolders.filterIsInstance<ManagedConfig>().filter { h -> h.sortedOptions.any { it._actualValue == null } }
			assertEquals(emptyList<String>(), notLoaded.map { it.javaClass.name }, "CONFIG holders left unloaded")
			assertTrue(ChatTabs.TConfig.enabled, "a real option next to the old keys did not load")
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
