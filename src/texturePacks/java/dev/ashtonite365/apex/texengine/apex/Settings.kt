/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: the user's texture settings (config id `custom-skyblock-textures`, SPEC §6).
 */
package dev.ashtonite365.apex.texengine.apex

interface TextureSettings {
	val enabled: Boolean
	val skullsEnabled: Boolean
	val cacheForever: Boolean
	val cacheDuration: Int
	val armorOverrides: Boolean
	val blockOverrides: Boolean
	val legacyPaths: Boolean
	val screenLayouts: Boolean
}

object Settings {

	@Volatile
	var source: TextureSettings = Defaults

	object Defaults : TextureSettings {
		override val enabled = true
		override val skullsEnabled = true
		override val cacheForever = true
		override val cacheDuration = 1
		override val armorOverrides = true
		override val blockOverrides = true
		override val legacyPaths = true
		override val screenLayouts = true
	}
}

internal val settings: TextureSettings get() = Settings.source
