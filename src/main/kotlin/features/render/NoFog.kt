/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: No Fog. One toggle that clears ALL fog everywhere (every SkyBlock island, no island condition):
 * terrain / render-distance fog, the atmospheric (air, rain, nether-style) fog, water, lava and powder-snow fog, and
 * the fog that blindness and darkness pull in. Render-only: the effects themselves (and their darkening pulse) stay,
 * nothing about game state or packets changes. Hooks: dev.ashtonite365.apex.mixins.NoFogPatch (each fog environment)
 * and NoFogDistancePatch (the render-distance fog FogRenderer.setupFog adds afterwards).
 * The config key stays no-fog.crimson-isle (the original island-only toggle) so existing values carry over unchanged.
 */
package dev.ashtonite365.apex.features.render

import net.minecraft.client.renderer.fog.FogData
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object NoFog {
	@Config
	object TConfig : ManagedConfig("no-fog", Category.RENDER) {

		val enabled by toggle("crimson-isle") { true }
	}

	fun shouldClear(enabled: Boolean): Boolean = enabled

	@JvmStatic
	fun isActive(): Boolean = shouldClear(TConfig.enabled)

	@JvmStatic
	fun clear(data: FogData, renderDistance: Float, cloudDistance: Float) {
		val far = renderDistance * 2F
		data.environmentalStart = far
		data.environmentalEnd = far
		data.skyEnd = maxOf(data.skyEnd, renderDistance)
		data.cloudEnd = maxOf(data.cloudEnd, cloudDistance)
	}

	@JvmStatic
	fun clearDistance(data: FogData, renderDistance: Float) {
		val far = renderDistance * 2F
		data.renderDistanceStart = far
		data.renderDistanceEnd = far
	}
}
