// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object RetiredChoices {

	val BAR_STYLES: Set<String> = setOf("pill", "gradient", "glossy", "segmented", "segmented_hd", "hatched", "flat_rounded")

	val STORAGE_STYLES: Set<String> = setOf("CLASSIC_TRANSPARENT", "CLASSIC_MINECRAFT", "CLASSIC_DARK", "CLASSIC_CUSTOM")

	private val byEnum: Map<String, Set<String>> = mapOf(
		"dev.ashtonite365.apex.util.hud.BarStyle" to BAR_STYLES,
		"dev.ashtonite365.apex.util.hud.BarStyleChoice" to BAR_STYLES,
		"dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay\$StorageTextureStyle" to STORAGE_STYLES,
	)

	fun isRetired(enumClassName: String, saved: JsonElement): Boolean {
		val name = (saved as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
		return byEnum[enumClassName]?.contains(name) == true
	}
}
