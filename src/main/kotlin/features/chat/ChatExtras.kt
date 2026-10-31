/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): chat timestamps, /apex remindme reminders, rare-drop banner and
 * price alerts. Ideas from other mods' feature descriptions only
 * (see CREDITS.md; nothing copied). Original implementation.
 */
package dev.ashtonite365.apex.features.chat

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig

object ChatExtras {
	@Config
	object TConfig : ManagedConfig("chat-extras", Category.CHAT) {
		val timestamps by toggle("timestamps") { false }
		val seconds by toggle("timestamp-seconds") { false }
		val rareDropBanner by toggle("rare-drop-banner") { true }
		val priceAlerts by toggle("price-alerts") { true }
	}

	@Serializable data class Reminder(val at: Long, val text: String)
	@Serializable data class PriceAlert(val item: String, val above: Boolean, val price: Double)
	@Serializable data class Data(val reminders: MutableList<Reminder> = mutableListOf(), val priceAlerts: MutableList<PriceAlert> = mutableListOf())

	@Config
	object DConfig : DataHolder<Data>(serializer(), "chat-extras", ::Data)

	@Subscribe
	fun onModify(event: ModifyChatEvent) {
		if (!TConfig.timestamps) return
		val t = LocalTime.now().format(DateTimeFormatter.ofPattern(if (TConfig.seconds) "HH:mm:ss" else "HH:mm"))
		event.replaceWith = Component.literal("§8[$t] §r").append(event.replaceWith)
	}

	private val RARE = Regex("^(RARE|VERY RARE|CRAZY RARE|PRAY TO RNGESUS|INSANE) DROP!\\s+(.+?)(?: \\(\\+\\d+% ✯ Magic Find\\))?$")
	fun rareDrop(line: String) = RARE.matchEntire(line.trim())?.let { it.groupValues[1] to it.groupValues[2] }

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.rareDropBanner) return
		val (tier, item) = rareDrop(event.bareText) ?: return
		if (tier == "RARE") return
		Alerts.show(Component.literal("§d§l$tier DROP! §r§f$item"), 3.seconds)
	}

	fun parseDurationMinutes(s: String): Long? {
		val t = s.trim().lowercase()
		t.toLongOrNull()?.let { return it }
		val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?$").matchEntire(t) ?: return null
		if (m.groupValues[1].isEmpty() && m.groupValues[2].isEmpty()) return null
		return (m.groupValues[1].toLongOrNull() ?: 0) * 60 + (m.groupValues[2].toLongOrNull() ?: 0)
	}

	fun shouldAlert(a: PriceAlert, price: Double) = if (a.above) price >= a.price else price <= a.price

	private var tick = 0

	@Subscribe
	fun onTick(event: TickEvent) {
		if (tick++ % 20 != 0) return
		val data = DConfig.data
		val now = System.currentTimeMillis()
		val due = data.reminders.filter { it.at <= now }
		if (due.isNotEmpty()) {
			data.reminders.removeAll(due); DConfig.markDirty()
			for (r in due) { Alerts.show(Component.literal("§e⏰ ${r.text}"), 4.seconds); Client.showChat(Component.literal("§e[SkyBlock Apex] Reminder: §f${r.text}")) }
		}
		if (!TConfig.priceAlerts || tick % 1200 != 1) return
		val hit = data.priceAlerts.filter { a -> HypixelStaticData.getPriceOfItem(ItemId(a.item))?.let { shouldAlert(a, it) } == true }
		if (hit.isEmpty()) return
		data.priceAlerts.removeAll(hit); DConfig.markDirty()
		for (a in hit) Client.showChat(Component.literal("§a[SkyBlock Apex] Price alert: ${a.item} is ${if (a.above) "above" else "below"} ${ApexFormatters.withThousands(a.price.toLong())}."))
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.CHAT, "remindme", "Remind yourself after a time: /apex remindme 30m <text>") {
			onArgument("time", com.mojang.brigadier.arguments.StringArgumentType.word()) { time ->
				onArgument("text", RestArgumentType) { text ->
					onRun {
						val mins = parseDurationMinutes(this[time]) ?: return@onRun source.sendFeedback(Component.literal("§cUse e.g. 30m, 2h or 1h30m."))
						DConfig.data.reminders.add(Reminder(System.currentTimeMillis() + mins.minutes.inWholeMilliseconds, this[text])); DConfig.markDirty()
						source.sendFeedback(Component.literal("§aReminder set for $mins minutes."))
					}
				}
			}
		}
		event.register(ManagedConfig.Category.AUCTION_BAZAAR, "pricealert", "Alert when an item's price crosses a value: /apex pricealert add <ITEM_ID> above|below <price> | list") {

			onLiteral("add") { for (dir in listOf("above", "below")) onArgument("item", com.mojang.brigadier.arguments.StringArgumentType.word()) { item ->
				onLiteral(dir) {
					onArgument("price", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0)) { price ->
						onRun {
							DConfig.data.priceAlerts.add(PriceAlert(this[item].uppercase(), dir == "above", this[price])); DConfig.markDirty()
							source.sendFeedback(Component.literal("§aPrice alert added (checked every minute)."))
						}
					}
				}
			} }
			onLiteral("list") {
				onRun {
					val l = DConfig.data.priceAlerts
					if (l.isEmpty()) source.sendFeedback(Component.literal("§7No price alerts."))
					l.forEach { source.sendFeedback(Component.literal("§e${it.item} ${if (it.above) "≥" else "≤"} ${ApexFormatters.withThousands(it.price.toLong())}")) }
				}
			}
		}
	}
}
