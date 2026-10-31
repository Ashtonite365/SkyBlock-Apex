/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): bazaar order book overlay. Original implementation (order book view idea from
 * feature descriptions only; see CREDITS.md). Display only: while you type a price into the bazaar
 * price sign it shows the current top of the order book, your estimated queue position at that price
 * and an estimated fill time from the weekly moving volume. Nothing is clicked or sent.
 * Data: the existing api.hypixel.net /skyblock/bazaar feed (HypixelStaticData).
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.HypixelStaticData.BazaarOrderLevel
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object BazaarOrderBook {
	@Config
	object TConfig : ManagedConfig("bz-order-book", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }
		val depth by integer("depth", 1, 15) { 6 }
		val assumedAmount by integer("assumed-amount", 0, 1_000_000) { 0 }

		val toggleKey by keyBindingWithDefaultUnbound("toggle-key")
		val liquidityWarning by toggle("liquidity-warning") { true }
		val minWeeklyVolume by integer("min-weekly-volume", 0, 10_000_000) { 50_000 }
		val maxSpreadPercent by integer("max-spread-percent", 1, 500) { 15 }
		val selfCompetitionWarning by toggle("self-competition-warning") { true }
		val chatWarnings by toggle("chat-warnings") { true }
	}

	fun spreadPercent(bestBuyOrder: Double, bestSellOffer: Double): Double? =
		if (bestBuyOrder <= 0 || bestSellOffer <= 0) null else (bestSellOffer - bestBuyOrder) / bestBuyOrder * 100

	fun liquidityIssues(weeklyVolume: Long, spreadPct: Double?, minVolume: Long, maxSpread: Int): List<String> = buildList {
		if (weeklyVolume < minVolume) add("Low volume: ${ApexFormatters.compactNumber(weeklyVolume.toDouble())}/week (min ${ApexFormatters.compactNumber(minVolume.toDouble())})")
		if (spreadPct != null && spreadPct > maxSpread) add("Wide spread: ${"%.1f".format(spreadPct)}% (max $maxSpread%)")
	}

	data class OwnOrder(val id: String?, val isBuy: Boolean, val pricePerUnit: Double, val filled: Boolean)

	fun selfCompetition(own: List<OwnOrder>, id: String, isBuy: Boolean, price: Double?): List<String> =
		own.filter { it.id == id && it.isBuy == isBuy && !it.filled }.map {
			val p = ApexFormatters.withThousands(it.pricePerUnit, 1)
			when {
				price == null -> "You already have a ${if (isBuy) "buy order" else "sell offer"} at $p"
				(isBuy && price > it.pricePerUnit) || (!isBuy && price < it.pricePerUnit) -> "Undercuts your own order at $p"
				else -> "Competes with your own order at $p"
			}
		}

	private fun ownOrders() = dev.ashtonite365.apex.features.auction.BazaarOrders.currentOrders.map {
		OwnOrder(it.id, it.isBuy, it.pricePerUnit, it.lastState == "FILLED")
	}

	private fun warnings(id: ItemId, buy: Boolean, price: Double?): List<String> {
		val data = HypixelStaticData.bazaarData[bazaarStockOf(id)] ?: return listOf()
		val q = data.quickStatus
		val out = mutableListOf<String>()
		if (TConfig.liquidityWarning) out += liquidityIssues(minOf(q.buyMovingWeek, q.sellMovingWeek),
			spreadPercent(q.sellPrice, q.buyPrice), TConfig.minWeeklyVolume.toLong(), TConfig.maxSpreadPercent)
		if (TConfig.selfCompetitionWarning) out += selfCompetition(ownOrders(), id.skyblockId, buy, price)
		return out
	}

	data class Estimate(val queueAhead: Long, val ratePerHour: Double, val etaHours: Double?, val wouldInstaFill: Boolean)

	fun estimate(
		isBuyOrder: Boolean, price: Double, amount: Long,
		buyOrders: List<BazaarOrderLevel>, sellOffers: List<BazaarOrderLevel>, movingWeek: Long,
	): Estimate {
		val (same, opposite) = if (isBuyOrder) buyOrders to sellOffers else sellOffers to buyOrders
		val ahead = same.filter { if (isBuyOrder) it.pricePerUnit >= price else it.pricePerUnit <= price }.sumOf { it.amount }
		val bestOpp = opposite.firstOrNull()?.pricePerUnit
		val crosses = bestOpp != null && (if (isBuyOrder) price >= bestOpp else price <= bestOpp)
		val rate = movingWeek / 168.0
		val eta = if (crosses) 0.0 else if (rate <= 0) null else (ahead + amount) / rate
		return Estimate(ahead, rate, eta, crosses)
	}

	fun formatEta(hours: Double?): String = when {
		hours == null -> "never (no volume)"
		hours < 1.0 / 60 -> "<1m"
		hours < 1 -> "${(hours * 60).toInt()}m"
		hours < 48 -> "%.1fh".format(hours)
		else -> "%.1fd".format(hours / 24)
	}

	fun parsePrice(text: String): Double? =
		text.trim().lowercase().replace(",", "").let { t ->
			if (t.isEmpty()) null
			else when (t.last()) { 'k' -> t.dropLast(1).toDoubleOrNull()?.times(1e3); 'm' -> t.dropLast(1).toDoubleOrNull()?.times(1e6); else -> t.toDoubleOrNull() }
		}

	private var product: ItemId? = null
	private var isBuy: Boolean? = null
	private var hidden = false
	private val PRODUCT_TITLE = Regex("^.+ ➜ (.+)$")

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {

		if (event.new is AbstractSignEditScreen && TConfig.chatWarnings && SkyBlockSession.inSkyBlock) {
			val b = isBuy; val id = product
			val title = (event.old as? AbstractContainerScreen<*>)?.title?.bareText?.trim().orEmpty()
			val side = when {
				title.startsWith("How much do you want to pay") || title.contains("Buy Order") -> true
				title.startsWith("At what price are you selling") || title.contains("Sell Offer") -> false
				else -> b
			}
			if (side != null && id != null) warnings(id, side, null).forEach { Client.showChat(net.minecraft.network.chat.Component.literal("§c[Bazaar ⚠] §e$it")) }
		}
		val old = event.old as? AbstractContainerScreen<*> ?: return
		val title = old.title.bareText.trim()
		when {
			title.startsWith("How much do you want to pay") || title.contains("Buy Order") -> isBuy = true
			title.startsWith("At what price are you selling") || title.contains("Sell Offer") -> isBuy = false
			else -> PRODUCT_TITLE.matchEntire(title)?.let { m ->
				ItemNameLookup.guessItemByName(m.groupValues[1], false)?.let { product = it; isBuy = null }
			}
		}
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (event.matches(TConfig.toggleKey)) hidden = !hidden
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		if (!TConfig.enabled || hidden || !SkyBlockSession.inSkyBlock) return
		val screen = event.screen as? AbstractSignEditScreen ?: return
		val buy = isBuy ?: return
		val id = product ?: return
		val acc = screen as AccessorSignEditScreen
		val lines = acc.sign_Apex?.getText(acc.slot_Apex)?.getMessages(false)?.map { it.bareText.trim() } ?: return
		if (lines.getOrNull(1)?.startsWith("^^^") != true || lines.getOrNull(2) == "Enter query") return
		val data = HypixelStaticData.bazaarData[bazaarStockOf(id)] ?: return
		val s = ApexFormatters::compactNumber
		val out = mutableListOf("§l${if (buy) "Buy order" else "Sell offer"}: ${id.skyblockId}")
		val n = TConfig.depth
		out.add("§aBuy orders §7| §6Sell offers")
		for (i in 0 until n) {
			val b = data.sellSummary.getOrNull(i); val o = data.buySummary.getOrNull(i)
			if (b == null && o == null) break
			out.add((b?.let { "§a${ApexFormatters.withThousands(it.pricePerUnit, 1)} x${s(it.amount.toDouble())}" } ?: "§8-") +
				" §7| " + (o?.let { "§6${ApexFormatters.withThousands(it.pricePerUnit, 1)} x${s(it.amount.toDouble())}" } ?: "§8-"))
		}
		val price = parsePrice(lines[0])
		if (price != null) {
			val q = data.quickStatus
			val e = estimate(buy, price, TConfig.assumedAmount.toLong(), data.sellSummary, data.buySummary,
				if (buy) q.sellMovingWeek else q.buyMovingWeek)
			out.add("§ePrice ${ApexFormatters.withThousands(price, 1)}")
			if (e.wouldInstaFill) out.add("§cCrosses the spread: fills instantly")
			else {
				out.add("§7Queue ahead: §f${s(e.queueAhead.toDouble())} §7(top ${n} levels only)")
				out.add("§7Volume: §f${s(e.ratePerHour)}/h §7ETA: §f${formatEta(e.etaHours)}")
			}
		} else out.add("§8Type a price to see queue/ETA")
		warnings(id, buy, price).forEach { out.add("§c⚠ $it") }
		val x = 4
		val y = 4

		val fit = if (out.maxOf { Client.font.width(SidePanel.adapt(it)) } + 12 > event.screen.width - 8) out.map(SidePanel::abbreviateNumbers) else out
		val w = fit.maxOf { Client.font.width(SidePanel.adapt(it)) } + 12
		SidePanel.drawBox(event.drawContext, x, y, x + w, y + fit.size * 10 + 10)
		fit.forEachIndexed { i, l -> SidePanel.text(event.drawContext, l, x + 6, y + 6 + i * 10) }
	}
}
