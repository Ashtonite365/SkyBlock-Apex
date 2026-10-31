/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Hide Weather. Removes rain, snow and thunder visuals client-side: the rain / snow columns, the rain
 * splash particles and rain sounds, the weather sky darkening and the lightning sky flash. Render-only: the level's
 * weather values, game state and packets are never changed. Hooks: dev.ashtonite365.apex.mixins.HideWeather*Patch.
 */
package dev.ashtonite365.apex.features.render

import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object HideWeather {
	@Config
	object TConfig : ManagedConfig("hide-weather", Category.RENDER) {
		val enabled by toggle("enabled") { false }
	}

	@JvmStatic
	fun isActive(): Boolean = TConfig.enabled

	@JvmStatic
	fun visibleLevel(enabled: Boolean, real: Float): Float = if (enabled) 0F else real
}
