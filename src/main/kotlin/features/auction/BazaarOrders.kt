/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * APEX-26.3 PORT (AH/BZ extras). Original implementation written for this port from feature
 * descriptions only (similar features exist in, and BazaarNotifier); no code copied.
 */
package dev.ashtonite365.apex.features.auction

import dev.ashtonite365.apex.util.bazaarStockOf
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.events.HudRenderEvent
import org.joml.Vector2i
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object BazaarOrders {
	@Config
	object TConfig : ManagedConfig("bazaar-orders", Category.AUCTION_BAZAAR) {
		val trackOrders by toggle("track-orders") { true }
		val outbidAlerts by toggle("outbid-alerts") { true }
		val outbidSound by toggle("outbid-sound") { true }
		val outbidClickable by toggle("outbid-clickable") { true }
		val highlightOrders by toggle("highlight-orders") { true }
		val highlightAuctions by toggle("highlight-auctions") { true }
		val trackerHud by toggle("tracker-hud") { false }
		val flipHelper by toggle("flip-helper") { true }
		val flipMode by choice("flip-mode") { BazaarExtras.FlipMode.COMPETITIVE }
		val reopenCancelled by toggle("reopen-cancelled") { true }
		val orderValue by toggle("order-value") { true }
		val dailyLimitMillions by integer("daily-limit-millions", 0, 100000) { 0 }
		val hideProgress by toggle("hide-progress") { false }
		val ordersHud by toggle("orders-hud") { false }
		val ordersHudOutsideOnly by toggle("orders-hud-outside-only") { true }
		val ordersHudLines by integer("orders-hud-lines", 1, 20) { 8 }
		val ordersHudPosition by position("orders-hud-position", 180, 90) { Vector2i(5, 160) }
		val marginSummary by toggle("margin-summary") { true }
		val trackerHudPosition by position("tracker-hud-position", 140, 50) { Vector2i(5, 260) }
	}

	@Serializable
	data class Order(
		val itemName: String,
		val id: String?,
		val isBuy: Boolean,
		var amount: Int,
		var pricePerUnit: Double,
		var lastState: String = "UNKNOWN",
	)

	@Serializable
	data class Data(val orders: MutableList<Order> = mutableListOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "bazaar-orders", ::Data)

	private val orders get() = DConfig.data?.orders

	val currentOrders: List<Order> get() = orders.orEmpty().toList()

	private val SETUP = Regex("\\[Bazaar] (Buy Order|Sell Offer) Setup! ([0-9,]+)x (.+) for ([0-9,.]+) coins\\.")
	private val FILLED = Regex("\\[Bazaar] Your (Buy Order|Sell Offer) for ([0-9,]+)x (.+) was filled!")
	private val CANCELLED_BUY = Regex("\\[Bazaar] Cancelled! Refunded ([0-9,.]+) coins from cancelling Buy Order!")
	private val CANCELLED_SELL = Regex("\\[Bazaar] Cancelled! Refunded ([0-9,]+)x (.+) from cancelling Sell Offer!")

	private fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0
	fun idFor(name: String): ItemId? = ItemNameLookup.guessItemByName(name, false)

	fun applyChat(list: MutableList<Order>, line: String): Boolean {
		SETUP.matchEntire(line)?.let { m ->
			val amount = num(m.groupValues[2]).toInt()
			val name = m.groupValues[3]
			list.add(Order(name, runCatching { idFor(name)?.skyblockId }.getOrNull(), m.groupValues[1] == "Buy Order",
				amount, num(m.groupValues[4]) / amount.coerceAtLeast(1)))
			return true
		}
		FILLED.matchEntire(line)?.let { m ->
			val isBuy = m.groupValues[1] == "Buy Order"
			val idx = list.indexOfFirst { it.isBuy == isBuy && it.itemName == m.groupValues[3] && it.amount == num(m.groupValues[2]).toInt() }
				.takeIf { it >= 0 } ?: list.indexOfFirst { it.isBuy == isBuy && it.itemName == m.groupValues[3] }
			if (idx >= 0) list[idx].lastState = "FILLED"
			return idx >= 0
		}
		CANCELLED_SELL.matchEntire(line)?.let { m ->
			return list.removeIf { !it.isBuy && it.itemName == m.groupValues[2] && it.lastState != "FILLED" }
		}
		CANCELLED_BUY.matchEntire(line)?.let { m ->
			val refund = num(m.groupValues[1])
			val idx = list.indexOfFirst { it.isBuy && kotlin.math.abs(it.amount * it.pricePerUnit - refund) < 1.0 }
			if (idx >= 0) list.removeAt(idx)
			return idx >= 0
		}
		return false
	}

	val tracker = SessionTracker(idleTimeout = 30.minutes, resetAfter = null)
	private val FILLED_FOR_TRACKER = FILLED

	@Subscribe
	fun onOrdersHud(event: HudRenderEvent) {

		if (!TConfig.ordersHud) return
		val list = orders ?: return
		if (list.isEmpty()) return
		if (TConfig.ordersHudOutsideOnly && Client.screenTitle?.contains("Bazaar") == true) return
		val lines = mutableListOf<Component>(Component.literal("§6§lBazaar Orders §7(${list.size})"))
		for (o in list.take(TConfig.ordersHudLines)) {
			val kind = if (o.isBuy) "§aBuy" else "§bSell"
			val state = when {
				o.lastState == "FILLED" -> " §a§lFILLED"
				isOutbid(o) -> " §c(outbid)"
				else -> ""
			}
			lines += Component.literal("$kind §f${o.amount}x ${o.itemName} §7@ §6${dev.ashtonite365.apex.util.ApexFormatters.withThousands(o.pricePerUnit, 1)}$state")
		}
		if (list.size > TConfig.ordersHudLines) lines += Component.literal("§7… ${list.size - TConfig.ordersHudLines} more")
		if (TConfig.marginSummary) { val om = dev.ashtonite365.apex.features.apexmarket.OrderMargin; om.lines(om.current()).forEach { lines += Component.literal(it) } }
		event.drawHudLines(TConfig.ordersHudPosition, lines)
	}

	private fun isOutbid(o: Order): Boolean {
		val id = o.id?.let { ItemId(it) } ?: return false
		val q = dev.ashtonite365.apex.repo.HypixelStaticData.bazaarData[bazaarStockOf(id)]?.quickStatus ?: return false
		return if (o.isBuy) q.sellPrice > o.pricePerUnit + 0.05 else q.buyPrice < o.pricePerUnit - 0.05
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.trackerHud || tracker.isEmpty) return
		event.drawHudLines(TConfig.trackerHudPosition, listOf(Component.literal("§6Bazaar Orders")) + tracker.lines())
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.trackOrders) return
		val list = orders ?: return
		val line = event.bareText.trim()
		if (line.startsWith("[Bazaar] Claimed")) {

			list.removeIf { it.lastState == "FILLED" && line.contains(it.itemName) }
			DConfig.markDirty(); return
		}
		FILLED_FOR_TRACKER.matchEntire(line)?.let { m ->
			val isBuy = m.groupValues[1] == "Buy Order"
			val amount = num(m.groupValues[2])
			val ppu = list.firstOrNull { it.isBuy == isBuy && it.itemName == m.groupValues[3] }?.pricePerUnit
			tracker.add(if (isBuy) "Buy orders filled" else "Sell offers filled")
			if (ppu != null) tracker.add(if (isBuy) "Coins spent" else "Coins earned", amount * ppu)
		}
		if (applyChat(list, line)) DConfig.markDirty()
	}

	fun stateOf(order: Order): String? {
		if (order.lastState == "FILLED") return "FILLED"
		val id = order.id?.let(::ItemId) ?: return null
		val status = HypixelStaticData.bazaarData[id.bazaarProduct]?.quickStatus ?: return null

		val outbid = if (order.isBuy) status.sellPrice > order.pricePerUnit + 0.05 else status.buyPrice < order.pricePerUnit - 0.05
		return if (outbid) "OUTBID" else "BEST"
	}

	private var lastCheck = TimeMark.farPast()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.outbidAlerts || !SkyBlockSession.inSkyBlock) return
		if (lastCheck.passedTime() < 10.seconds) return
		lastCheck = TimeMark.now()
		val list = orders ?: return
		var dirty = false
		for (order in list) {
			val state = stateOf(order) ?: continue
			if (state == order.lastState) continue
			if (state == "OUTBID") {
				val kind = if (order.isBuy) "buy order" else "sell offer"
				val msg = tr("apex.bazaar.outbid",
					"§e[Bazaar] Your $kind for ${order.amount}x ${order.itemName} at ${ApexFormatters.withThousands(order.pricePerUnit, 1)} is no longer the best price.").copy()

				if (TConfig.outbidClickable) msg.withStyle {
					it.withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("/bz"))
						.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("§eClick to open the Bazaar")))
				}
				Client.showChat(msg)
				if (TConfig.outbidSound) dev.ashtonite365.apex.util.Alerts.sound()

			}
			order.lastState = state
			dirty = true
		}
		if (dirty) DConfig.markDirty()
	}

	private val PRICE_PER_UNIT = Regex("Price per unit: ([0-9,.]+) coins")
	private val ORDER_AMOUNT = Regex("(?:Order|Offer) amount: ([0-9,]+)x")

	@Subscribe
	fun onOrdersMenu(event: ChestInventoryUpdateEvent) {
		if (!TConfig.trackOrders || Client.screenTitle != "Your Bazaar Orders") return
		val list = orders ?: return
		val menu = Client.containerScreen?.menu ?: return
		val seen = mutableListOf<Order>()
		for (slot in menu.slots) {
			if (slot.container is Inventory) continue
			val stack = slot.item
			val title = stack.hoverName.bareText.trim()
			val isBuy = title.startsWith("BUY ")
			if (!isBuy && !title.startsWith("SELL ")) continue
			val name = title.substringAfter(' ')
			val lore = stack.view().loreAccordingToNbt.map { it.bareText }
			val price = lore.firstNotNullOfOrNull { PRICE_PER_UNIT.find(it) }?.groupValues?.get(1)?.let(::num) ?: continue
			val amount = lore.firstNotNullOfOrNull { ORDER_AMOUNT.find(it) }?.groupValues?.get(1)?.let(::num)?.toInt() ?: 0
			val filled = lore.any { it.contains("100%") || it.contains("Filled: ") && it.contains("$amount/$amount") }
			val old = list.find { it.isBuy == isBuy && it.itemName == name && kotlin.math.abs(it.pricePerUnit - price) < 0.05 }
			seen.add(Order(name, old?.id ?: runCatching { idFor(name)?.skyblockId }.getOrNull(), isBuy, amount, price,
				if (filled) "FILLED" else old?.lastState ?: "UNKNOWN"))
		}
		list.clear(); list.addAll(seen)
		DConfig.markDirty()
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (event.slot.container is Inventory) return
		val title = Client.screenTitle ?: return
		val stack = event.slot.item
		if (stack.isEmpty) return
		val colour: Int? = when {
			title == "Your Bazaar Orders" && TConfig.highlightOrders -> {
				val name = stack.hoverName.bareText.trim()
				val isBuy = name.startsWith("BUY ")
				val order = orders?.find { it.isBuy == isBuy && it.itemName == name.substringAfter(' ') }
				when (order?.let(::stateOf)) { "BEST" -> 0x6000FF00; "OUTBID" -> 0x60FF0000; "FILLED" -> 0x6000AAFF; else -> null }
			}
			(title == "Manage Auctions" || title == "Your Bids" || title.startsWith("Auction View")) && TConfig.highlightAuctions -> {
				val lore = stack.view().loreAccordingToNbt.map { it.bareText.trim() }
				when {
					lore.any { it == "Status: Sold!" || it == "Status: Ended!" && lore.any { l -> l.startsWith("Buyer:") } } -> 0x6000FF00
					lore.any { it == "Status: Expired!" } -> 0x60FF0000
					title == "Your Bids" && lore.any { it.startsWith("Bidder:") } && lore.none { it == "Bidder: ${Client.playerName}" || it.endsWith(" ${Client.playerName}") } -> 0x60FFAA00
					else -> null
				}
			}
			else -> null
		}
		if (colour != null) event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, colour)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("bzorders", dev.ashtonite365.apex.util.data.ManagedConfig.Category.AUCTION_BAZAAR) {
			onRun {
				val list = orders.orEmpty()
				if (list.isEmpty()) source.sendFeedback(tr("apex.bazaar.orders.none", "No tracked bazaar orders. Open \"Your Bazaar Orders\" to sync."))
				for (o in list) {
					val state = stateOf(o) ?: o.lastState
					source.sendFeedback(Component.literal(
						"${if (o.isBuy) "§aBUY" else "§6SELL"} §f${o.amount}x ${o.itemName} §7@ §e${ApexFormatters.withThousands(o.pricePerUnit, 1)} §7- " +
							when (state) { "BEST" -> "§abest price"; "OUTBID" -> "§coutbid"; "FILLED" -> "§bfilled"; else -> "§8unknown" }))
				}

				if (list.isNotEmpty() && TConfig.marginSummary) {
					val om = dev.ashtonite365.apex.features.apexmarket.OrderMargin
					om.lines(om.current()).forEach { source.sendFeedback(Component.literal(it)) }
				}
			}
		}
	}
}
