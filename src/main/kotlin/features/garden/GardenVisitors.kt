// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object GardenVisitors {

	val rareRewards = listOf(
		"Flowering Bouquet", "Overgrown Grass", "Green Bandana", "Dedication IV", "Music Rune", "Space Helmet",
		"Cultivating I", "Replenish I", "Delicate V", "Copper Dye", "Jungle Key", "Fruit Bowl", "Harvest Harbinger V",
		"Hypercharge Chip", "Quickdraw Chip", "Farming Exp Boost", "Unfulfilled Jerryseed", "Voter's Badge", "VIP Voter's Badge",
		"Elite Voter's Badge", "Supreme Voter's Badge",
		"Wild Strawberry Dye", "Velvet Top Hat", "Cashmere Jacket", "Satin Trousers", "Oxford Shoes", "Carnival Ticket",
		"Visitors' Gratitude", "Farming Contest Display", "Astronaut Minion Skin", "Fast Food Barn Skin",
		"Jelly Garden Greenhouse Skin",
	)

	private val byLength = rareRewards.sortedByDescending { it.length }
	private val NEXT_VISITOR = Regex("\\s*Next Visitor: (.*)")

	fun parseNextVisitor(tab: List<String>): String? {
		for (line in tab) NEXT_VISITOR.matchEntire(line)?.let { return it.groupValues[1].trim() }
		return null
	}

	fun rareRewardsIn(lore: List<String>): List<String> {
		val found = LinkedHashSet<String>()
		for (line in lore) byLength.firstOrNull { it in line }?.let(found::add)
		return found.toList()
	}

	fun timerLine(): Component? {
		if (!TConfig.visitorTimer) return null
		val next = parseNextVisitor(TabListReader.currentSnapshot.body.map { it.bareText }) ?: return null
		return Component.literal("§eNext visitor: §f$next")
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.visitorRewardWarning || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (event.stack.hoverName.string !in setOf("Accept Offer", "Refuse Offer")) return
		val rare = rareRewardsIn(event.lines.map { it.bareText }).ifEmpty { return }
		event.lines.add(Component.literal("§c§l⚠ Rare visitor reward: §r§9" + rare.joinToString("§7, §9")))
	}
}
