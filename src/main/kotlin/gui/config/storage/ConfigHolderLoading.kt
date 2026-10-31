// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config.storage

import kotlinx.serialization.json.JsonObject
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.data.IDataHolder

object ConfigHolderLoading {

	fun <T> loadHolders(
		holders: List<IDataHolder<*>>,
		configData: JsonObject,
		key: T?,
		storageClass: ConfigStorageClass,
	) {
		for (holder in holders) {
			if (holder.storageClass != storageClass) continue
			@Suppress("UNCHECKED_CAST")
			val h = holder as IDataHolder<T>
			try {
				if (key == null) h.explicitDefaultLoad() else h.loadFrom(key, configData)
			} catch (e: Exception) {
				Faults.logFault("Could not load config $holder ($storageClass); the other configs still load", e)
				if (!h.isLoaded) {
					try {
						h.explicitDefaultLoad()
					} catch (e2: Exception) {
						Faults.logFault("Could not load defaults for config $holder ($storageClass)", e2)
					}
				}
			}
		}
	}

	fun afterAllLoaded(holders: List<IDataHolder<*>>) {
		for (h in holders) {
			try {
				h.afterAllLoaded()
			} catch (e: Exception) {
				Faults.logFault("Config $h failed after loading", e)
			}
		}
	}
}
