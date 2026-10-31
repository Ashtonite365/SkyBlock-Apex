// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import net.minecraft.network.chat.Component

object GardenSensitivity {

	private var saved: Double? = null

	fun reduced(original: Double, divisor: Int): Double = original / maxOf(divisor, 1)

	private fun allowed() = TConfig.sensitivityReducer && SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN

	private fun lower() {
		val option = Client.game.options.sensitivity()
		val before = option.get()
		saved = before
		option.set(reduced(before, TConfig.sensitivityDivisor))
		Client.showChat(Component.literal("§eMouse sensitivity lowered"))
	}

	private fun restore() {
		val before = saved ?: return
		Client.game.options.sensitivity().set(before)
		Client.showChat(Component.literal("§eMouse sensitivity restored"))
		saved = null
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!allowed() || !event.matches(TConfig.sensitivityKey)) return
		if (saved == null) lower() else restore()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (saved != null && !allowed()) restore()
	}
}
