// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.render.farterrain.surface

class OtherLodMods(private val isModLoaded: (String) -> Boolean) {
	companion object {

		val MOD_IDS: List<String> = listOf("distanthorizons", "voxy")
	}

	val loaded: Boolean by lazy { MOD_IDS.any { id -> runCatching { isModLoaded(id) }.getOrDefault(false) } }
}
