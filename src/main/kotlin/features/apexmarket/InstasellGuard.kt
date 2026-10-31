/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): instasell guard. Original implementation. Blocks a click on "Sell Instantly" (and
 * "Sell Inventory/Sacks Now") once when the instant-sell price is far below the best sell offer or the total
 * is large; you must click again within a few seconds to go through. It never clicks for you.
 * ⚠ ban-risk: it changes how your clicks reach the server, so it is a riskyToggle (off by default, one-time
 * disclaimer), per the project rule for anything that intercepts inputs.
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object InstasellGuard {
	@Config
	object TConfig : ManagedConfig("instasell-guard", Category.AUCTION_BAZAAR) {
		val enabled by riskyToggle("enabled", RiskLevel.RISKY)
		val maxLossPercent by integer("max-loss-percent", 1, 100) { 20 }
		val minTotalK by integer("min-total-k", 0, 1_000_000) { 1000 }
		val confirmSeconds by integer("confirm-seconds", 1, 10) { 3 }
	}

	fun reason(perUnit: Double?, bestOffer: Double?, total: Double?, maxLossPct: Int, minTotal: Double): String? {
		if (perUnit != null && bestOffer != null && bestOffer > 0) {
			val loss = (1 - perUnit / bestOffer) * 100
			if (loss >= maxLossPct) return "instasell is ${"%.0f".format(loss)}% below the best sell offer"
		}
		if (minTotal > 0 && total != null && total >= minTotal) return "total ${ApexFormatters.compactNumber(total)} coins"
		return null
	}

	private val PER_UNIT = Regex("^Price per unit: ([0-9,.]+) coins")
	private val TOTAL = Regex("^(?:Total|You earn): ([0-9,.]+) coins")
	private val TITLE = Regex("^.+ ➜ (.+)$")
	private var armedSlot: Int? = null
	private var armedAt = 0L

	@Subscribe
	fun onSlot(event: IsSlotProtectedEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val slot = event.slot ?: return
		if (slot.container is Inventory) return
		val name = slot.item.hoverName.bareText.trim()
		if (name != "Sell Instantly" && name != "Sell Inventory Now" && name != "Sell Sacks Now") return
		val now = System.currentTimeMillis()
		if (armedSlot == slot.index && now - armedAt < TConfig.confirmSeconds * 1000L) { armedSlot = null; return }
		val lore = slot.item.view().loreAccordingToNbt.map { it.bareText.trim() }
		fun find(r: Regex) = lore.firstNotNullOfOrNull { r.find(it)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }
		val id = Client.screenTitle?.let { TITLE.matchEntire(it)?.groupValues?.get(1) }?.let { ItemNameLookup.guessItemByName(it, false) }
		val bestOffer = id?.let { HypixelStaticData.bazaarData[bazaarStockOf(it)]?.quickStatus?.buyPrice }
		val why = reason(if (name == "Sell Instantly") find(PER_UNIT) else null, bestOffer, find(TOTAL),
			TConfig.maxLossPercent, TConfig.minTotalK * 1000.0) ?: return
		armedSlot = slot.index; armedAt = now
		event.protectSilent()
		Client.showChat(Component.literal("§c[Instasell guard] §e$why. Click again within ${TConfig.confirmSeconds}s to sell."))
	}
}
