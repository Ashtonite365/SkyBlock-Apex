/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Fullbright. Overrides the lightmap (see dev.ashtonite365.apex.mixins.FullbrightLightmapPatch) instead
 * of raising gamma, since 26.3 clamps the gamma option. Client-side only.
 */
package dev.ashtonite365.apex.features.render

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object Fullbright {
	@Config
	object TConfig : ManagedConfig("fullbright", Category.RENDER) {
		var enabled by toggle("enabled") { false }
		val toggleKey by keyBindingWithDefaultUnbound("toggle-key")
	}

	@JvmStatic
	fun isEnabled(): Boolean = TConfig.enabled

	@Subscribe
	fun onWorldKeyboard(event: WorldKeyboardEvent) {
		if (!event.matches(TConfig.toggleKey)) return
		TConfig.enabled = !TConfig.enabled
		Client.showChat(
			if (TConfig.enabled) tr("apex.fullbright.toggled.on", "§eFullbright: §aON")
			else tr("apex.fullbright.toggled.off", "§eFullbright: §cOFF")
		)
	}
}
