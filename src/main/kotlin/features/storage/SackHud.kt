package dev.ashtonite365.apex.features.storage

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.skyblock.SackUtil

object SackHud {
	enum class Sort : StringRepresentable {
		AMOUNT, VALUE, FULLEST, NAME;

		override fun getSerializedName(): String = name
	}

	@Config
	object TConfig : ManagedConfig("sack-hud", Category.INVENTORY) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 170, 90) { Vector2i(4, 200) }
		val items by string("items") { "" }
		val sort by choice("sort") { Sort.AMOUNT }
		val maxLines by integer("max-lines", 1, 30) { 8 }
		val showCapacity by toggle("show-capacity") { true }
		val showValue by toggle("show-value") { true }
		val hideEmpty by toggle("hide-empty") { true }
	}

	private val names = mutableMapOf<ItemId, String>()

	@OptIn(NeedsComponents::class, dev.ashtonite365.apex.repo.ExpensiveItemCacheApi::class)
	private fun name(id: ItemId): String = names.getOrPut(id) {
		runCatching { SBItemStack(id).asImmutableItemStack().current().hoverName.string }.getOrDefault(id.skyblockId)
	}

	fun lines(): List<Component> {
		val c = TConfig
		val chosen = c.items.split(',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.map(::ItemId)
		val ids = if (chosen.isNotEmpty()) chosen else SackUtil.items.keys.toList()
		data class Row(val id: ItemId, val amount: Long, val cap: Long?, val value: Double?)
		var rows = ids.map { id ->
			val amount = SackUtil.items[id] ?: 0L
			Row(id, amount, SackUtil.capacity[id], HypixelStaticData.getPriceOfItem(id)?.times(amount))
		}
		if (c.hideEmpty) rows = rows.filter { it.amount > 0 }
		if (chosen.isEmpty()) rows = when (c.sort) {
			Sort.AMOUNT -> rows.sortedByDescending { it.amount }
			Sort.VALUE -> rows.sortedByDescending { it.value ?: 0.0 }
			Sort.FULLEST -> rows.sortedByDescending { r -> r.cap?.takeIf { it > 0 }?.let { r.amount.toDouble() / it } ?: 0.0 }
			Sort.NAME -> rows.sortedBy { name(it.id) }
		}
		val out = mutableListOf<Component>(Component.literal("§e§lSacks"))
		if (rows.isEmpty()) {
			out += Component.literal("§7Open your sacks once to cache them.")
			return out
		}
		rows.take(c.maxLines).forEach { r ->
			val sb = StringBuilder("§f${name(r.id)}: §a${ApexFormatters.withThousands(r.amount)}")
			if (c.showCapacity && r.cap != null && r.cap > 0) {
				val pct = (r.amount * 100 / r.cap).toInt()
				sb.append(" §7/ ${ApexFormatters.compactNumber(r.cap.toDouble())} ").append(if (pct >= 90) "§c" else "§7").append("($pct%)")
			}
			if (c.showValue && r.value != null) sb.append(" §6${ApexFormatters.compactNumber(r.value)}")
			out += Component.literal(sb.toString())
		}
		if (c.showValue) rows.sumOf { it.value ?: 0.0 }.takeIf { it > 0 }?.let { out += Component.literal("§7Total: §6${ApexFormatters.compactNumber(it)}") }
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		event.drawHudLines(TConfig.hud, lines())
	}
}
