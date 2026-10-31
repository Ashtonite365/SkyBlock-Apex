// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object GardenWarps {
	@Config
	object TConfig : ManagedConfig("plus-garden-warps", Category.GARDEN) {
		val homeKey by keyBindingWithDefaultUnbound("home-key")
		val setHomeKey by keyBindingWithDefaultUnbound("set-home-key")
		val barnKey by keyBindingWithDefaultUnbound("barn-key")
	}

	fun commandFor(home: Boolean, setHome: Boolean, barn: Boolean): String? {
		if (home) return "warp garden"
		if (setHome) return "setspawn"
		return if (barn) "tptoplot barn" else null
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (Client.screen != null || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val home = event.matches(TConfig.homeKey)
		val setHome = event.matches(TConfig.setHomeKey)
		val barn = event.matches(TConfig.barnKey)
		commandFor(home, setHome, barn)?.let(Client::runCommand)
	}
}
