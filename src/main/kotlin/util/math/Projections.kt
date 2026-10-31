// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import net.minecraft.world.phys.Vec2
import dev.ashtonite365.apex.util.render.normaliseAngle

object Projections {

	object Two {

		val ε: Double = 0.000001
		val π: Double = dev.ashtonite365.apex.util.render.HALF_TURN
		val τ: Double = π * 2

		fun isNullish(float: Float): Boolean = abs(float) < ε

		fun xInterceptOfLine(origin: Vec2, direction: Vec2): Vec2? = when {
			isNullish(direction.x) -> Vec2(origin.x, 0F)
			isNullish(direction.y) -> null
			else -> {
				val rise = direction.y / direction.x
				Vec2(origin.x - origin.y / rise, 0F)
			}
		}

		fun interceptAlongCardinal(distanceFromAxis: Float, slope: Float): Float? =
			if (isNullish(slope)) null else -distanceFromAxis / slope

		@JvmStatic
		fun projectAngleOntoUnitBox(
			angleRadians: Double,
		): Vec2 {
			val wrapped = normaliseAngle(angleRadians)
			val x = cos(wrapped)
			val y = sin(wrapped)
			val reach = max(abs(x), abs(y))
			return Vec2((x / reach).toFloat(), (y / reach).toFloat())
		}
	}
}
