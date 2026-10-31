/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): flip finder. Behaviour modelled on the price tooltips and common flip finders
 * (feature descriptions only); original implementation, no code copied. Display only: it lists
 * candidates in chat with clickable /ahs or /bz shortcuts. Nothing is bought, sold or relisted.
 * Data: existing price feeds only (api.hypixel.net bazaar, api.eliteskyblock.com lowest BIN + averages).
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.bazaarStockOf
import dev.ashtonite365.apex.repo.itemsSafe
import io.github.moulberry.repo.data.NEUCraftingRecipe
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.features.inventory.ItemValue
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object FlipFinder {
	@Config
	object TConfig : ManagedConfig("flip-finder", Category.AUCTION_BAZAAR) {
		val results by integer("results", 1, 30) { 10 }
		val minProfitK by integer("min-profit-k", 0, 100000) { 100 }
		val minWeeklyVolume by integer("min-weekly-volume", 0, 10_000_000) { 10_000 }
		val ahTaxPercent by integer("ah-tax-percent", 0, 10) { 2 }
		val bzTaxTenths by integer("bz-tax-tenths", 0, 50) { 11 }
		val binAvgSource by choice("bin-avg-source") { AvgSource.THREE_DAY }
	}

	enum class AvgSource : net.minecraft.util.StringRepresentable {
		ONE_DAY, THREE_DAY, SEVEN_DAY;
		override fun getSerializedName() = name
	}

	enum class Kind { BIN, BZ, CRAFT }
	data class Flip(val id: String, val buy: Double, val sell: Double, val profit: Double, val note: String)

	fun binProfit(buy: Double, sell: Double, taxPct: Int) = sell * (1 - taxPct / 100.0) - buy

	fun bzMargin(buyOrder: Double, sellOffer: Double, taxTenths: Int) = sellOffer * (1 - taxTenths / 1000.0) - buyOrder

	fun binFlips(lbin: Map<ItemId, Double>, avg: Map<ItemId, Double>, taxPct: Int, minProfit: Double): List<Flip> =
		lbin.mapNotNull { (id, low) ->
			val a = avg[id] ?: return@mapNotNull null
			if (low <= 0) return@mapNotNull null
			val p = binProfit(low, a, taxPct)
			if (p < minProfit) null else Flip(id.skyblockId, low, a, p, "${((a / low - 1) * 100).toInt()}% under avg")
		}.sortedByDescending { it.profit }

	data class BzQuote(val id: String, val instantBuy: Double, val instantSell: Double, val weeklyVolume: Long)

	fun bzFlips(quotes: List<BzQuote>, taxTenths: Int, minWeekly: Long, minProfitPerUnit: Double = 0.0): List<Flip> =
		quotes.mapNotNull { q ->
			if (q.weeklyVolume < minWeekly || q.instantSell <= 0) return@mapNotNull null

			val m = bzMargin(q.instantSell, q.instantBuy, taxTenths)
			if (m <= minProfitPerUnit) null
			else Flip(q.id, q.instantSell, q.instantBuy, m, "vol/wk ${ApexFormatters.compactNumber(q.weeklyVolume.toDouble())}, ${"%.1f".format(m / q.instantSell * 100)}%")
		}.sortedByDescending { it.profit }

	private fun avgMap() = when (TConfig.binAvgSource) {
		AvgSource.ONE_DAY -> HypixelStaticData.avg1dlowestBin
		AvgSource.THREE_DAY -> HypixelStaticData.avg3dlowestBin
		AvgSource.SEVEN_DAY -> HypixelStaticData.avg7dlowestBin
	}

	fun craftSellNet(bzInstaSell: Double?, lowestBin: Double?, ahTaxPct: Int, bzTaxTenths: Int): Double? =
		bzInstaSell?.let { it * (1 - bzTaxTenths / 1000.0) } ?: lowestBin?.let { it * (1 - ahTaxPct / 100.0) }

	private fun sellPrice(id: ItemId): Double? = craftSellNet(
		HypixelStaticData.bazaarData[bazaarStockOf(id)]?.quickStatus?.sellPrice,
		HypixelStaticData.lowestBin[id], TConfig.ahTaxPercent, TConfig.bzTaxTenths)

	fun find(kind: Kind): List<Flip> {
		val minProfit = TConfig.minProfitK * 1000.0
		return when (kind) {
			Kind.BIN -> binFlips(HypixelStaticData.lowestBin, avgMap(), TConfig.ahTaxPercent, minProfit)
			Kind.BZ -> bzFlips(HypixelStaticData.bazaarData.values.map {
				BzQuote(it.productId.stockKey, it.quickStatus.buyPrice, it.quickStatus.sellPrice,
					minOf(it.quickStatus.buyMovingWeek, it.quickStatus.sellMovingWeek))
			}, TConfig.bzTaxTenths, TConfig.minWeeklyVolume.toLong())
				.sortedByDescending { it.profit / it.buy }
			Kind.CRAFT -> RepoManager.coreRepo.items.itemsSafe.keys.asSequence().mapNotNull { key ->
				val id = ItemId(key)
				if (RepoManager.getRecipesFor(id).none { it is NEUCraftingRecipe }) return@mapNotNull null
				val cost = ItemValue.craftCost(id) ?: return@mapNotNull null
				val sell = sellPrice(id) ?: return@mapNotNull null
				val p = sell - cost
				if (p < minProfit) null else Flip(key, cost, sell, p, "craft vs sell")
			}.sortedByDescending { it.profit }.toList()
		}
	}

	private fun displayName(id: String): String =
		RepoManager.coreRepo.items.itemsSafe[id]?.displayName?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "") ?: id

	private fun line(kind: Kind, f: Flip): Component {
		val name = displayName(f.id)
		val cmd = if (kind == Kind.BZ || HypixelStaticData.bazaarData.containsKey(ItemId.BazaarProduct(f.id))) "/bz $name" else "/ahs $name"
		val s = ApexFormatters::compactNumber
		return Component.literal("§e$name §7buy §6${s(f.buy)} §7sell §6${s(f.sell)} §a+${s(f.profit)} §8(${f.note})")
			.withStyle {
				it.withClickEvent(ClickEvent.RunCommand(cmd))
					.withHoverEvent(HoverEvent.ShowText(Component.literal("Click to run $cmd")))
			}
	}

	private fun liquid(stockKey: String): Boolean {
		val q = HypixelStaticData.bazaarData[ItemId.BazaarProduct(stockKey)]?.quickStatus ?: return false
		return BazaarOrderBook.liquidityIssues(minOf(q.buyMovingWeek, q.sellMovingWeek),
			BazaarOrderBook.spreadPercent(q.sellPrice, q.buyPrice),
			BazaarOrderBook.TConfig.minWeeklyVolume.toLong(), BazaarOrderBook.TConfig.maxSpreadPercent).isEmpty()
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("flips", ManagedConfig.Category.AUCTION_BAZAAR) {
			for (kind in Kind.entries) onLiteral(kind.name.lowercase()) {
				onRun {
					val list = find(kind).take(TConfig.results)
					source.sendFeedback(tr("apex.flips.header", "Top ${kind.name} flips (prices only, nothing is bought):"))
					if (list.isEmpty()) source.sendFeedback(tr("apex.flips.none", "No flips match your filters (or prices are still loading)."))
					list.forEach { source.sendFeedback(line(kind, it)) }
				}
			}
			onLiteral("history") {
				onRun {
					val flips = FlipHistory.DConfig.data?.flips.orEmpty()
					val s = ApexFormatters::compactNumber
					source.sendFeedback(tr("apex.flips.history", "Completed flips: ${flips.size}, total profit ${s(flips.sumOf { it.profit })}"))
					flips.take(TConfig.results).forEach {
						source.sendFeedback(Component.literal("§7[${it.market}] §e${it.amount}x ${it.item} §7${s(it.buyPerUnit)} → ${s(it.sellPerUnit)} " +
							(if (it.profit >= 0) "§a+" else "§c") + s(it.profit)))
					}
				}
			}
			onLiteral("suggest") {
				onRun {
					source.sendFeedback(tr("apex.flips.suggest", "Suggested flips (top 3 per kind; BZ filtered by the liquidity limits):"))
					for (kind in Kind.entries) {
						val list = find(kind).filter { kind != Kind.BZ || liquid(it.id) }.take(3)
						list.forEach { source.sendFeedback(Component.literal("§8[${kind.name}] ").append(line(kind, it))) }
					}
				}
			}
			onRun {
				source.sendFeedback(tr("apex.flips.usage", "Usage: /apex flips <bin|bz|craft|suggest|history>"))
			}
		}
	}
}
