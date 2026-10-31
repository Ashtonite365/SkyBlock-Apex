// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

@OptIn(NeedsComponents::class)
object GardenShops {

	private fun bronzeValue(medal: String): Int = when (medal.lowercase()) {
		"gold" -> 8
		"silver" -> 2
		"bronze" -> 1
		else -> throw NoSuchElementException(medal)
	}

	private val MEDALS = Regex("(?i)\\s*(?:([\\d,]+)x? )?(gold|silver|bronze) medals?(?: x([\\d,]+))?\\s*")
	private val COPPER = Regex("\\s*([\\d,]+) Copper\\s*")

	private fun count(text: String): Int = text.filter { it != ',' }.ifEmpty { "1" }.toInt()

	private fun costSection(lore: List<String>): List<String> {
		val header = lore.indexOfFirst { it.trim() == "Cost" }
		if (header < 0) return emptyList()
		val out = ArrayList<String>()
		for (i in header + 1 until lore.size) { if (lore[i].isBlank()) break; out += lore[i] }
		return out
	}

	fun bronzeCost(lore: List<String>): Int? {
		var total = 0
		var any = false
		for (line in costSection(lore)) {
			val m = MEDALS.matchEntire(line) ?: continue
			val (before, medal, after) = m.destructured
			total += count(before.ifEmpty { after }) * bronzeValue(medal)
			any = true
		}
		return if (any) total else null
	}

	fun copperCost(lore: List<String>): Int? {
		for (line in costSection(lore)) COPPER.matchEntire(line)?.let { return count(it.groupValues[1]) }
		return null
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val title = Client.screenTitle ?: return
		val inAnita = TConfig.anitaMedalProfit && title == "Anita"
		val inSkyMart = TConfig.skymartCopperPrice && title.startsWith("SkyMart")
		if (!inAnita && !inSkyMart) return
		val lore = event.lines.map { it.bareText }
		val value = event.stack.view().repoItemId?.let(RepoConstants::price) ?: return
		if (inAnita) bronzeCost(lore)?.let { event.lines.add(Component.literal("§7Profit per bronze medal: §6" + ApexFormatters.compactNumber(value / it))) }
		if (inSkyMart) copperCost(lore)?.let { event.lines.add(Component.literal("§7Coins per copper: §6" + ApexFormatters.compactNumber(value / it))) }
	}
}
