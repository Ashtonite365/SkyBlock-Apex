// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.render

import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object WorldLineScale {
	@Config
	object TConfig : ManagedConfig("world-lines", Category.RENDER) {
		val scaleWithDistance by toggle("scale-with-distance") { true }

		val fullWidthDistance by integer("full-width-distance", 1, 64) { 8 }

		val minWidthPercent by integer("min-width-percent", 5, 100) { 20 }
	}

	fun width(base: Float, distance: Double, enabled: Boolean, fullAt: Int, minPercent: Int): Float {
		if (!enabled || distance <= fullAt) return base
		val f = (fullAt / distance).coerceIn(minPercent / 100.0, 1.0)
		return (base * f).toFloat()
	}

	fun width(base: Float, distance: Double): Float =
		width(base, distance, TConfig.scaleWithDistance, TConfig.fullWidthDistance, TConfig.minWidthPercent)
}
