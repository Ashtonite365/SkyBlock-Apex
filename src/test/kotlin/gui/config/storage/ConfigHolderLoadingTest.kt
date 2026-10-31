// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.config.storage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.config.storage.ConfigHolderLoading
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.GenericConfig
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig

@ExtendWith(AutoBootstrapExtension::class)
class ConfigHolderLoadingTest {
	private val registered get() = MainConfigRegistry.registered

	private val badValue: JsonElement = JsonArray(listOf(JsonPrimitive(1), JsonArray(listOf())))

	private fun rejectsBadValue(h: IDataHolder<*>): Boolean = runCatching {
		when (h) {
			is GenericConfig<*> -> Apex.json.decodeFromJsonElement(h.serializer, badValue)
			is ProfileKeyedConfig<*> -> Apex.json.decodeFromJsonElement(h.serializer, badValue)
			else -> return false
		}
	}.isFailure

	private fun prefixOf(h: IDataHolder<*>) = when (h) {
		is GenericConfig<*> -> h.prefix
		is ProfileKeyedConfig<*> -> h.prefix
		else -> error("no prefix")
	}

	@Test
	fun oneFailingConfigDoesNotStopTheOthersLoading() {
		val state = MainConfigRegistry.snapshot()
		try {
			for ((storageClass, key) in listOf(ConfigStorageClass.STORAGE to Unit, ConfigStorageClass.PROFILE to SkyBlockSession.NULL_UUID)) {
				val holders = registered.filter { it.storageClass == storageClass }
				assertTrue(holders.size > 20, "only ${holders.size} $storageClass holders")

				val victim = holders.first(::rejectsBadValue)
				assertTrue(holders.indexOf(victim) < holders.size - 5, "need holders after the failing one")
				holders.forEach { it.clear() }
				assertTrue(holders.none(MainConfigRegistry::isLoaded), "clear() did not unload the $storageClass holders")
				ConfigHolderLoading.loadHolders(registered, buildJsonObject { put(prefixOf(victim), badValue) }, key, storageClass)
				val unloaded = holders.filterNot(MainConfigRegistry::isLoaded).map { it.javaClass.name }
				assertEquals(emptyList<String>(), unloaded, "$storageClass holders left unloaded after ${victim.javaClass.name} failed")
			}
		} finally {
			MainConfigRegistry.restore(state)
		}
	}
}
