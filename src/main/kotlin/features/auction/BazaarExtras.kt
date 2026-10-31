/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: bazaar extras. Ideas from other bazaar mods (see CREDITS.md), merged
 * into one feature; original implementation written from feature descriptions only, no code copied. Settings live next to the order tracker in BazaarOrders.TConfig (AH & Bazaar).
 */
package dev.ashtonite365.apex.features.auction

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ChatFilters
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object BazaarExtras {
	enum class FlipMode : net.minecraft.util.StringRepresentable {

		COMPETITIVE,

		MATCHED;
		override fun getSerializedName() = name
	}

	fun suggestedSellPrice(bestSellOffer: Double, mode: FlipMode): Double =
		if (mode == FlipMode.COMPETITIVE) (bestSellOffer - 0.1).coerceAtLeast(0.1) else bestSellOffer

	@Serializable
	data class Data(
		val bookmarks: MutableList<String> = mutableListOf(),
		var day: String = "",
		var spentToday: Double = 0.0,
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "bazaar-extras", ::Data)

	private val T get() = BazaarOrders.TConfig

	init {
		ChatFilters.register("bazaar-progress",
			Regex("^\\[Bazaar] (?:Submitting|Executing|Claiming|Putting|Cancelling|Setting up|Sending|Processing).*")) { T.hideProgress }
	}

	private val FILLED_BUY = Regex("\\[Bazaar] Your Buy Order for ([0-9,]+)x (.+) was filled!")
	private val CANCELLED = Regex("\\[Bazaar] Cancelled! Refunded (?:([0-9,]+)x (.+)|[0-9,.]+ coins) from cancelling (Buy Order|Sell Offer)!")
	private val BUY_SETUP = Regex("\\[Bazaar] Buy Order Setup! [0-9,]+x .+ for ([0-9,.]+) coins\\.")
	private val INSTANT_BUY = Regex("\\[Bazaar] Bought [0-9,]+x .+ for ([0-9,.]+) coins!")

	private fun num(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

	fun addSpent(data: Data, coins: Double, today: String = LocalDate.now().toString()) {
		if (data.day != today) { data.day = today; data.spentToday = 0.0 }
		data.spentToday += coins
	}

	private fun clickable(text: String, hover: String, click: ClickEvent): Component =
		Component.literal(text).withStyle { it.withClickEvent(click).withHoverEvent(HoverEvent.ShowText(Component.literal(hover))) }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText.trim()
		(BUY_SETUP.matchEntire(line) ?: INSTANT_BUY.matchEntire(line))?.let { m ->
			DConfig.data?.let { addSpent(it, num(m.groupValues[1])); DConfig.markDirty() }
		}
		if (T.flipHelper) FILLED_BUY.matchEntire(line)?.let { m ->
			val name = m.groupValues[2]
			val id = BazaarOrders.idFor(name) ?: return@let
			val best = HypixelStaticData.bazaarData[id.bazaarProduct]?.quickStatus?.buyPrice ?: return@let
			val price = suggestedSellPrice(best, T.flipMode)
			val text = ApexFormatters.withThousands(price, 1)
			Client.showChat(Component.literal("§e[Flip] Sell ${m.groupValues[1]}x $name at §6$text§e each ")
				.append(clickable("§b[Copy price]", "Copy $text", ClickEvent.CopyToClipboard(text.replace(",", "")))))
		}
		if (T.reopenCancelled) CANCELLED.matchEntire(line)?.let { m ->
			val name = m.groupValues[2].ifEmpty { null }
			val msg = Component.literal("§e[Bazaar] ")
			if (name != null) msg.append(clickable("§a[Reopen ${name}] ", "Open $name in the Bazaar", ClickEvent.RunCommand("/bz $name")))
			else msg.append(clickable("§a[Open Bazaar] ", "Open the Bazaar", ClickEvent.RunCommand("/bz")))
			if (m.groupValues[1].isNotEmpty())
				msg.append(clickable("§b[Copy amount]", "Copy ${m.groupValues[1]}", ClickEvent.CopyToClipboard(m.groupValues[1].replace(",", ""))))
			Client.showChat(msg)
		}
	}

	fun orderValue(orders: List<BazaarOrders.Order>): Pair<Double, Double> {
		val open = orders.filter { it.lastState != "FILLED" }
		return open.filter { it.isBuy }.sumOf { it.amount * it.pricePerUnit } to open.filter { !it.isBuy }.sumOf { it.amount * it.pricePerUnit }
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		if (!T.orderValue) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val title = screen.title.bareText.trim()
		if (!title.startsWith("Bazaar") && title != "Your Bazaar Orders") return
		val lines = mutableListOf<String>()
		if (title == "Your Bazaar Orders") {
			val (buy, sell) = orderValue(BazaarOrders.DConfig.data?.orders.orEmpty())
			lines.add("§7Buy orders: §6${ApexFormatters.compactNumber(buy)} §7| Sell offers: §6${ApexFormatters.compactNumber(sell)}")
		}
		val data = DConfig.data
		if (T.dailyLimitMillions > 0 && data != null) {
			val spent = if (data.day == LocalDate.now().toString()) data.spentToday else 0.0
			lines.add("§7Spent today: §6${ApexFormatters.compactNumber(spent)}§7 / ${ApexFormatters.compactNumber(T.dailyLimitMillions * 1e6)}")
		}
		lines.forEachIndexed { i, l -> event.drawContext.centeredText(Client.font, l, screen.width / 2, 4 + i * 10, -1) }
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("bzbookmark", dev.ashtonite365.apex.util.data.ManagedConfig.Category.AUCTION_BAZAAR) {
			onLiteral("add") {
				onArgument("item", RestArgumentType) { item ->
					onRun {
						val name = this[item].trim()
						DConfig.data?.bookmarks?.let { if (name !in it) it.add(name) }
						DConfig.markDirty()
						source.sendFeedback(tr("apex.bazaar.bookmark.added", "Bookmarked $name."))
					}
				}
			}
			onLiteral("remove") {
				onArgument("item", RestArgumentType) { item ->
					onRun {
						DConfig.data?.bookmarks?.remove(this[item].trim())
						DConfig.markDirty()
						source.sendFeedback(tr("apex.bazaar.bookmark.removed", "Removed bookmark."))
					}
				}
			}
			onRun {
				val marks = DConfig.data?.bookmarks.orEmpty()
				if (marks.isEmpty()) source.sendFeedback(tr("apex.bazaar.bookmark.none", "No bookmarks. Use /apex bzbookmark add <item>."))
				val orders = BazaarOrders.DConfig.data?.orders.orEmpty()
				for (name in marks) {
					val id = BazaarOrders.idFor(name)
					val q = id?.let { HypixelStaticData.bazaarData[it.bazaarProduct]?.quickStatus }
					val price = q?.let { " §7buy §6${ApexFormatters.compactNumber(it.buyPrice)} §7sell §6${ApexFormatters.compactNumber(it.sellPrice)}" } ?: ""
					val active = if (orders.any { it.itemName == name && it.lastState != "FILLED" }) " §a●" else ""
					source.sendFeedback(clickable("§e$name$price$active", "Open $name in the Bazaar", ClickEvent.RunCommand("/bz $name")))
				}
			}
		}
	}
}
