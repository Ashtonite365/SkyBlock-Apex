/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): AH price tools. Original implementation.
 * - Sales/day tooltip line (bazaar items: weekly moving volume / 7 from api.hypixel.net).
 * - "Still lowest BIN?" line on your own auctions in Manage Auctions.
 * - LBIN-1 pre-fill: when the price sign opens from "Create BIN Auction", the lowest BIN minus 1 is typed
 *   into the first line. It is never submitted; you confirm or edit it yourself.
 * - Price paid: remembers what you paid for an item bought from the AH (by item UUID) and shows it.
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.bazaarStockOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.bareText

object AuctionPriceTools {
	@Config
	object TConfig : ManagedConfig("ah-price-tools", Category.AUCTION_BAZAAR) {
		val salesPerDay by toggle("sales-per-day") { true }
		val stillLowestBin by toggle("still-lowest-bin") { true }
		val lbinMinusOne by toggle("lbin-minus-one") { false }
		val pricePaid by toggle("price-paid") { true }
	}

	@Serializable
	data class Data(val paid: MutableMap<String, Long> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "ah-price-paid", ::Data)

	fun salesPerDay(buyMovingWeek: Long, sellMovingWeek: Long): Double = (buyMovingWeek + sellMovingWeek) / 7.0

	fun isStillLowest(price: Long, lowestBinPerItem: Double, count: Int): Boolean = price <= lowestBinPerItem * count + 0.5

	fun lbinMinusOne(lowestBinPerItem: Double, count: Int): Long? =
		(lowestBinPerItem * count).toLong().minus(1).takeIf { it > 0 }

	private val BIN_PRICE = Regex("^Buy it now: ([0-9,]+) coins")
	private val VIEW_PRICE = Regex("^Price: ([0-9,]+) coins")
	private val AH_BOUGHT = Regex("You purchased (.+) for ([0-9,]+) coins!")

	private fun loreLong(lore: List<String>, r: Regex) =
		lore.firstNotNullOfOrNull { r.find(it.trim())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val acc = event.stack.view()
		val id = acc.repoItemId ?: return
		if (TConfig.salesPerDay) {
			HypixelStaticData.bazaarData[bazaarStockOf(id)]?.quickStatus?.let {
				event.lines.add(Component.literal("§eSales/day: §6${ApexFormatters.compactNumber(salesPerDay(it.buyMovingWeek, it.sellMovingWeek))}"))
			}
		}
		if (TConfig.pricePaid) {
			acc.itemUuid?.let { DConfig.data?.paid?.get(it.toString()) }?.let {
				event.lines.add(Component.literal("§ePrice paid: §6${ApexFormatters.withThousands(it, 3)} coins"))
			}
		}
		if (TConfig.stillLowestBin && Client.screenTitle == "Manage Auctions") {
			val price = loreLong(acc.loreAccordingToNbt.map { it.bareText }, BIN_PRICE) ?: return
			val lbin = HypixelStaticData.lowestBin[id] ?: return
			event.lines.add(Component.literal(
				if (isStillLowest(price, lbin, event.stack.count)) "§a✔ Still the lowest BIN"
				else "§c✖ Undercut: lowest BIN is ${ApexFormatters.compactNumber(lbin * event.stack.count)}"))
		}
	}

	private var pendingUuid: String? = null
	private var pendingName: String? = null
	private var pendingPrice: Long? = null

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		val old = event.old as? AbstractContainerScreen<*> ?: return
		val title = old.title.bareText.trim()
		if (title.startsWith("BIN Auction View") || title.startsWith("Confirm Purchase")) {
			val stack = old.menu.slots.getOrNull(13)?.item
			if (stack != null && !stack.isEmpty) {
				stack.view().itemUuid?.let { pendingUuid = it.toString(); pendingName = stack.hoverName.bareText.trim() }
				loreLong(stack.view().loreAccordingToNbt.map { it.bareText }, VIEW_PRICE)?.let { pendingPrice = it }
			}
		}
		if (TConfig.lbinMinusOne && title.startsWith("Create BIN Auction")) {
			val sign = event.new as? AbstractSignEditScreen ?: return
			val stack = old.menu.slots.getOrNull(13)?.item ?: return
			val id = stack.view().repoItemId ?: return
			val lbin = HypixelStaticData.lowestBin[id] ?: return
			val price = lbinMinusOne(lbin, stack.count) ?: return
			val lines = (sign as AccessorSignEditScreen).messages_Apex
			if (lines.isNotEmpty() && lines[0].isBlank()) lines[0] = price.toString()
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.pricePaid) return
		val m = AH_BOUGHT.matchEntire(event.bareText.trim()) ?: return
		val uuid = pendingUuid ?: return
		if (pendingName != null && !m.groupValues[1].contains(pendingName!!) && !pendingName!!.contains(m.groupValues[1])) return
		val data = DConfig.data ?: return
		data.paid[uuid] = m.groupValues[2].replace(",", "").toLongOrNull() ?: pendingPrice ?: return
		while (data.paid.size > 2000) data.paid.remove(data.paid.keys.first())
		DConfig.markDirty()
		pendingUuid = null
	}
}
