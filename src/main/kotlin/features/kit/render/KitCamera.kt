// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.render

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.client.CameraType

object KitCamera {
	@Config
	object TConfig : ManagedConfig("camera-tweaks", Category.RENDER) {
		val noFrontCamera by toggle("no-front-camera") { false }
		val customDistance by toggle("custom-distance") { false }
		val distance by integer("distance", 1, 32) { 4 }
	}

	object Logic {
		const val VANILLA_DISTANCE = 4f

		fun scaledDistance(original: Float, enabled: Boolean, distance: Int): Float {
			if (!enabled) return original
			val factor = distance / VANILLA_DISTANCE
			return original * factor
		}
	}

	@JvmStatic
	fun modifyCameraDistance(original: Float): Float = Logic.scaledDistance(original, TConfig.customDistance, TConfig.distance)

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.noFrontCamera) return
		val options = Client.game.options
		if (options.cameraType != CameraType.THIRD_PERSON_FRONT) return
		options.cameraType = CameraType.FIRST_PERSON
	}
}
