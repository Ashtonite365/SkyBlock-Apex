// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.plus

import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemId
import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component

class ProfitTracker(
	val title: String,
	private val totalProvider: () -> Data = { Data() },
	private val priceOf: (String) -> Double? = { HypixelStaticData.getPriceOfItem(ItemId(it)) },
	private val nowMillis: () -> Long = System::currentTimeMillis,
	private val onChange: () -> Unit = {},
) {
	@Serializable
	data class Data(
		val items: MutableMap<String, Long> = linkedMapOf(),
		var coins: Double = 0.0,
		var activeMillis: Long = 0L,
		var events: Long = 0L,
	)

	enum class Mode { SESSION, TOTAL }

	var mode = Mode.SESSION
	val session = Data()
	val total: Data get() = totalProvider()

	private var lastAdd = -1L

	var idleTimeoutMillis = 120_000L

	private fun noteActivity() {
		val now = nowMillis()
		val gap = now - lastAdd
		if (lastAdd >= 0 && gap >= 0 && gap <= idleTimeoutMillis) {
			session.activeMillis += gap
			total.activeMillis += gap
		}
		lastAdd = now
	}

	private inline fun record(change: (Data) -> Unit) {
		noteActivity()
		change(session)
		change(total)
		onChange()
	}

	fun addItem(id: String, amount: Long = 1) {
		noteActivity()
		val targets = arrayOf(session, total)
		for (d in targets) d.items[id] = d.items[id]?.let { it + amount } ?: amount
		onChange()
	}

	fun addCoins(amount: Double) = record { it.coins += amount }

	fun addEvent() = record { it.events++ }

	private fun clear(d: Data) {
		d.items.clear()
		d.coins = 0.0
		d.activeMillis = 0
		d.events = 0
	}

	fun resetSession() {
		clear(session)
		lastAdd = -1
	}

	fun resetTotal() {
		clear(total)
		onChange()
	}

	fun reset() {
		when (mode) {
			Mode.SESSION -> resetSession()
			Mode.TOTAL -> resetTotal()
		}
	}

	val current: Data get() = when (mode) { Mode.SESSION -> session; Mode.TOTAL -> total }

	fun itemValue(id: String, amount: Long): Double = (priceOf(id) ?: 0.0) * amount

	fun profit(data: Data = current): Double {
		var items = 0.0
		for ((id, n) in data.items) items += itemValue(id, n)
		return data.coins + items
	}

	fun profitPerHour(data: Data = current): Double? {
		val hours = data.activeMillis / 3_600_000.0
		if (hours < 0.005) return null
		return profit(data) / hours
	}

	fun lines(nameOf: (String) -> String = { it }, maxItems: Int = 12): List<Component> {
		val d = current
		val out = ArrayList<Component>()
		out.add(Component.literal("§e§l$title §7(${mode.name.lowercase()})"))
		val ranked = d.items.entries.sortedWith(Comparator { a, b -> itemValue(b.key, b.value).compareTo(itemValue(a.key, a.value)) })
		for (e in ranked.take(maxItems)) {
			val id = e.key
			val n = e.value
			out.add(Component.literal("§7${ApexFormatters.withThousands(n)}x §f${nameOf(id)} §6${ApexFormatters.compactNumber(itemValue(id, n))}"))
		}
		if (d.coins != 0.0) out.add(Component.literal("§7Coins: §6${ApexFormatters.compactNumber(d.coins)}"))
		out.add(Component.literal("§eProfit: §6${ApexFormatters.compactNumber(profit(d))}"))
		val perHour = profitPerHour(d)
		if (perHour != null) out.add(Component.literal("§eProfit/h: §6${ApexFormatters.compactNumber(perHour)}"))
		return out
	}
}
