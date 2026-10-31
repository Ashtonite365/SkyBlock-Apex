// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.pets

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.inventory.PetFeatures.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object PetPlusPorts {
	private val CANDY_LINE = Regex("^\\((\\d+)/10\\) Pet Candy Used$")

	fun candyUsed(lore: List<String>): Int? {
		for (line in lore) {
			val match = CANDY_LINE.matchEntire(line.trim()) ?: continue
			return match.groupValues[1].toInt()
		}
		return null
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!TConfig.candyUsed || !SkyBlockSession.inSkyBlock) return
		val item = event.slot.item
		if (item.isEmpty) return
		val count = candyUsed(item.view().loreAccordingToNbt.map { it.bareText })
		if (count == null || count <= 0) return
		val label = "§c$count"
		val right = event.slot.x + 17
		event.context.text(Client.font, label, right - Client.font.width(label), event.slot.y + 9, -1, true)
	}
}
