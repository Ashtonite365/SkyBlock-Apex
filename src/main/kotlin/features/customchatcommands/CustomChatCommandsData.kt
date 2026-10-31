// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.customchatcommands

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder

@Serializable
data class CustomChatCommandsData(
	var wheels: List<ChatCommandWheel> = listOf(),

	var legacyImported: Boolean = false,
) {
	@Config
	object DConfig : DataHolder<CustomChatCommandsData>(kotlinx.serialization.serializer(), "custom-chat-commands", ::CustomChatCommandsData) {

		private var importedThisLoad = false

		override fun onLoad() {
			if (!data.legacyImported) {
				runCatching {
					val f = dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader.storageFolder.resolve("macros.json")
					LegacyMacros.read(f)?.let { data.wheels = data.wheels + it }
				}
				data.legacyImported = true

				importedThisLoad = true
			}
			RadialChatCommands.setWheels(data.wheels)
		}

		override fun afterAllLoaded() {
			if (importedThisLoad) {
				importedThisLoad = false
				markDirty()
			}
		}
	}
}

object LegacyMacros {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	fun read(file: Path): List<ChatCommandWheel>? = if (file.exists()) parse(file.readText()) else null

	fun parse(text: String): List<ChatCommandWheel> {
		val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
		fun commandOf(o: Any?) = ((o as? JsonObject)?.get("command"))?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		val wheels = (root["wheels"] as? JsonArray).orEmpty().mapNotNull { w ->
			val o = w as? JsonObject ?: return@mapNotNull null
			val key = o["keyBinding"]?.let { runCatching { json.decodeFromJsonElement(SavedKeyBinding.serializer(), it) }.getOrNull() }
				?: SavedKeyBinding.unbound()
			ChatCommandWheel(key, (o["options"] as? JsonArray).orEmpty().mapNotNull { commandOf(it) }.map(::ChatCommand))
		}
		val fromCombos = (root["comboActions"] as? JsonArray).orEmpty().mapNotNull { commandOf((it as? JsonObject)?.get("action")) }
		return wheels + listOfNotNull(fromCombos.takeIf { it.isNotEmpty() }?.let { ChatCommandWheel(SavedKeyBinding.unbound(), it.map(::ChatCommand)) })
	}
}
