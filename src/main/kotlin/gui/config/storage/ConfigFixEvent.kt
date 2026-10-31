// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config.storage

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus

data class ConfigFixEvent(val storageClass: ConfigStorageClass, val toVersion: Int, var data: JsonObject) : ApexEvent() {
	companion object : ApexEventBus<ConfigFixEvent>()

	fun on(toVersion: Int, storageClass: ConfigStorageClass, block: ConfigEditor.() -> Unit) {
		require(toVersion <= ApexConfigLoader.currentConfigVersion) { "fixer for $toVersion is newer than ${ApexConfigLoader.currentConfigVersion}" }
		if (toVersion != this.toVersion || storageClass != this.storageClass) return
		ConfigEditor(listOf(RootSlot())).block()
	}

	private inner class RootSlot : JsonSlot() {
		override fun read(): JsonObject = data
		override fun write(value: JsonElement) {
			data = value as JsonObject
		}
		override fun toString() = "ConfigRoot($storageClass)"
	}
}
