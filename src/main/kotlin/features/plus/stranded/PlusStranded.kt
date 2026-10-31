// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.stranded

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matches
import dev.ashtonite365.apex.util.bareText
import net.minecraft.world.entity.player.Inventory

object PlusStranded {
	@Config
	object TConfig : ManagedConfig("stranded", Category.MISC) {
		val highlightPlaceableNpcs by toggle("highlight-placeable-npcs") { false }
	}

	private const val HIGHLIGHT = 0x8055FF55.toInt()

	private val patterns = PlusPatterns.group("stranded.highlightplacement")
	private val location by patterns.pattern("location", "Location: \\[-?\\d+, -?\\d+, -?\\d+]")
	private val clickToSet by patterns.pattern("clicktoset", "Click to set the location of this NPC!")
	private val clickToSpawn by patterns.pattern("clicktospawn", ".*location!")

	fun isPlaceable(lore: List<String>): Boolean {
		val prompt = lore.lastOrNull()?.trim() ?: return false
		val asksForLocation = clickToSet.matches(prompt) || clickToSpawn.matches(prompt)
		return asksForLocation && lore.none { location.matches(it.trim()) }
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!TConfig.highlightPlaceableNpcs || !SkyBlockSession.inSkyBlock || Client.screenTitle != "Island NPCs") return
		val slot = event.slot
		if (slot.container is Inventory || slot.item.isEmpty) return
		if (!isPlaceable(slot.item.view().loreAccordingToNbt.map { it.bareText })) return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, HIGHLIGHT)
	}
}
