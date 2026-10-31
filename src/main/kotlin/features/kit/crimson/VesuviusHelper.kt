// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.crimson

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.dungeon.DungeonChestProfit
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object VesuviusHelper {
	@Config
	object TConfig : ManagedConfig("vesuvius", Category.CRIMSON_ISLE) {
		val hideClaimed by toggle("hide-claimed") { true }
		val profitTooltip by toggle("profit-tooltip") { true }
	}

	private val COIN_LINE = Regex("^([\\d,]+) Coins$")
	private const val CLAIMED_TINT = 0xFF8B8B8B.toInt()

	private fun section(lore: List<String>, header: String): List<String>? {
		var start = -1
		for (i in lore.indices) if (lore[i].trim() == header) { start = i; break }
		if (start < 0) return null
		val out = ArrayList<String>()
		for (i in start + 1 until lore.size) {
			val t = lore[i].trim()
			if (t.isEmpty()) break
			out.add(t)
		}
		return out
	}

	fun contents(lore: List<String>): List<String> = section(lore, "Contents") ?: emptyList()

	fun coinCost(lore: List<String>): Double {
		val lines = section(lore, "Cost") ?: return 0.0
		var sum = 0.0
		for (l in lines) sum += COIN_LINE.matchEntire(l)?.groupValues?.get(1)?.replace(",", "")?.toDouble() ?: 0.0
		return sum
	}

	fun isClaimed(name: String, lore: List<String>): Boolean {
		if (name != "Kuudra's Hollow") return false
		return lore.any { it.trim() == "No more chests to open!" }
	}

	private fun inMenu(): Boolean {
		val title = Client.screenTitle ?: return false
		return "Vesuvius" in title || "Croesus" in title || title.startsWith("Kuudra - ")
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!TConfig.hideClaimed || !inMenu()) return
		val slot = event.slot
		if (slot.container === Client.player?.inventory) return
		val stack = slot.item
		if (!isClaimed(stack.hoverName.bareText, stack.view().loreAccordingToNbt.map { it.bareText })) return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, CLAIMED_TINT)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.profitTooltip || !inMenu()) return
		val lore = event.lines.map { it.bareText }
		val items = contents(lore)
		if (items.isEmpty()) return
		var value = 0.0
		var missing = false
		for (item in items) {
			val price = DungeonChestProfit.priceLoreLine(Component.literal(item))?.value
			if (price == null) missing = true else value += price
		}
		val profit = value - coinCost(lore)
		val colour = if (profit >= 0) "§a" else "§c"
		val note = if (missing) " §7(some prices unknown)" else ""
		event.lines.add(Component.literal("§bChest profit: $colour${ApexFormatters.compactNumber(profit)}$note"))
	}
}
