/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: the user's texture options. The config id, the option keys, their defaults and their
 * order are the ones existing saves and the settings screen use (SPEC §6), so settings carry over unchanged.
 * `model-overrides`, `legacy-cit` and `recolor-text` are kept for saved settings only: no engine has read them on
 * 26.3 (item overrides and legacy CIT files follow `enabled`; title colours and text replacements always apply).
 */
package dev.ashtonite365.apex.texengine.apex

import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

@Config
object TextureConfig : ManagedConfig("custom-skyblock-textures", Category.GENERAL), TextureSettings {
	override val enabled by toggle("enabled") { true }
	override val skullsEnabled by toggle("skulls-enabled") { true }
	override val cacheForever by toggle("cache-forever") { true }
	override val cacheDuration by integer("cache-duration", 0, 100) { 1 }

	val modelOverrides by toggle("model-overrides") { true }
	override val armorOverrides by toggle("armor-overrides") { true }
	override val blockOverrides by toggle("block-overrides") { true }
	override val legacyPaths by toggle("legacy-minecraft-path-support") { true }
	val legacyCit by toggle("legacy-cit") { true }
	val recolorText by toggle("recolor-text") { true }
	override val screenLayouts by toggle("screen-layouts") { true }
}
