// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.plus.PlusRepoData
import dev.ashtonite365.apex.util.bareText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.network.chat.Component

object GardenAnitaFortune {
	data class Price(val medals: Int, val tickets: Int)
	data class Result(val tier: Int, val maxTier: Int, val medals: Int, val tickets: Int)

	private const val FF_PER_TIER = 4

	fun levelPrices(): Map<Int, Price> = try {
		val levels = (PlusRepoData.loadRepoJson("constants/AnitaUpgradeCosts.json") as JsonObject)["level_price"] as JsonObject
		levels.entries.associate { (level, raw) ->
			val cost = raw as JsonObject
			fun int(key: String) = (cost[key] as JsonPrimitive).content.toInt()
			level.toInt() to Price(int("gold_medals"), int("jacob_tickets"))
		}
	} catch (e: Throwable) {
		emptyMap()
	}

	private val TICKETS = Regex("\\s*Jacob's Ticket x([\\d,.]+)\\s*")
	private val CURRENT_FF = Regex("\\s*You have: \\+(\\d+)\\D*Farming Fortune\\s*")

	private fun <T> firstMatch(lines: List<String>, regex: Regex, value: (String) -> T): T? {
		for (line in lines) regex.matchEntire(line)?.let { return value(it.groupValues[1]) }
		return null
	}

	fun costToMax(lore: List<String>, prices: Map<Int, Price>): Result? {
		val tier = (firstMatch(lore, CURRENT_FF) { it.toInt() } ?: 0) / FF_PER_TIER
		val shown = firstMatch(lore, TICKETS) { it.replace(",", "").toDouble() } ?: 0.0
		val nextTickets = prices[tier + 1]?.tickets ?: return null
		val scale = if (nextTickets > 0) shown / nextTickets else 0.0
		val remaining = prices.filterKeys { it > tier }.values
		val medals = remaining.sumOf { it.medals }
		val tickets = remaining.sumOf { it.tickets }
		return Result(tier, prices.size, medals, (scale * tickets).toInt())
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.anitaExtraFortune || Client.screenTitle != "Anita") return
		if (event.stack.hoverName.bareText.trim() != "Extra Farming Fortune") return
		val lore = event.lines.map { it.bareText }
		val cost = costToMax(lore, levelPrices()) ?: return
		val insertAt = lore.indexOfFirst { it.trim() == "Click to trade!" } - 1
		if (insertAt < 0) return
		val ticketPrice = RepoConstants.price(ItemId("JACOBS_TICKET"))
		val coins = ticketPrice?.let { ApexFormatters.compactNumber(it * cost.tickets) } ?: "?"
		event.lines.addAll(insertAt, listOf(
			"",
			"§7Cost to max out",
			"§6Gold medals: §8x${cost.medals}",
			"§aJacob Tickets §8x${ApexFormatters.withThousands(cost.tickets, 0)}",
			"  §7Price: §6$coins coins",
		).map(Component::literal))
		val fortuneLine = event.lines.indexOfFirst { CURRENT_FF.matches(it.bareText) }
		if (fortuneLine >= 0) event.lines.add(fortuneLine + 1, Component.literal("§7Current Tier: §e${cost.tier}/${cost.maxTier}"))
	}
}
