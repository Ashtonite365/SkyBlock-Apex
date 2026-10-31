// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenForegroundEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object StarlynCouponProfit {
	@Config
	object TConfig : ManagedConfig("starlyn-coupon-profit", Category.FORAGING) {
		val enabled by toggle("enabled") { true }
		val lines by integer("lines", 3, 20) { 10 }
	}

	val SHOPS = mapOf("Agatha's Shop" to ItemId("AGATHA_COUPON"), "Miria's Shop" to ItemId("MIRIA_COUPON"))

	fun isShopSlot(slot: Int): Boolean {
		if (slot < 9 || slot > 44) return false
		val column = slot % 9
		return column != 0 && column != 8
	}

	private val NAME_TIMES_AMOUNT = Regex("^(.+?) x([\\d,]+)$")

	fun readCosts(lore: List<String>): List<Pair<String, Long>> {
		var header = -1
		for (i in lore.indices.reversed()) if (lore[i].trim() == "Cost") { header = i; break }
		if (header < 0) return emptyList()
		val out = ArrayList<Pair<String, Long>>()
		for (i in header + 1 until lore.size) {
			val line = lore[i].trim()
			if (line.isEmpty()) break
			val m = NAME_TIMES_AMOUNT.matchEntire(line)
			out.add(if (m == null) line to 1L else m.groupValues[1].trim() to m.groupValues[2].replace(",", "").toLong())
		}
		return out
	}

	fun profitPerCoupon(sellPrice: Double, costs: List<Triple<Boolean, Long, Double?>>): Double? {
		var coupons = 0L
		for (c in costs) if (c.first) coupons += c.second
		if (coupons <= 0) return null
		var spent = 0.0
		for ((_, amount, price) in costs) spent += (price ?: 0.0) * amount
		return (sellPrice - spent) / coupons
	}

	data class Row(val name: String, val perCoupon: Double)

	private var cachedFor: AbstractContainerScreen<*>? = null
	private var frames = 0
	private var rows = emptyList<Row>()

	private fun rowFor(stack: ItemStack, coupon: ItemId): Row? {
		val name = stack.hoverName.bareText
		val id = stack.view().repoItemId ?: ItemNameLookup.guessItemByName(name, false) ?: return null
		val sell = HypixelStaticData.getPriceOfItem(id) ?: return null
		val costs = readCosts(stack.view().loreAccordingToNbt.map { it.bareText }).map { (costName, amount) ->
			val costId = ItemNameLookup.guessItemByName(costName, false)
			val price = if (costId == null || costId == coupon) null else HypixelStaticData.getPriceOfItem(costId)
			Triple(costId == coupon, amount, price)
		}
		val per = profitPerCoupon(sell, costs) ?: return null
		return Row(name, per)
	}

	private fun compute(screen: AbstractContainerScreen<*>, coupon: ItemId): List<Row> {
		val playerInv = Client.player?.inventory
		val chestSlots = screen.menu.slots.filter { it.container !== playerInv }
		val found = ArrayList<Row>()
		for ((index, slot) in chestSlots.withIndex()) {
			if (!isShopSlot(index) || slot.item.isEmpty) continue
			rowFor(slot.item, coupon)?.let(found::add)
		}
		return found.sortedByDescending { it.perCoupon }
	}

	@Subscribe
	fun onForeground(event: HandledScreenForegroundEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val coupon = SHOPS[event.screen.title.bareText.trim()] ?: return
		if (event.screen !== cachedFor || frames++ % 20 == 0) {
			cachedFor = event.screen
			rows = compute(event.screen, coupon)
		}
		val bounds = event.screen.castAccessor()
		val text = ArrayList<String>()
		text.add("§eProfit per Coupon")
		for (row in rows.take(TConfig.lines)) text.add("§f${row.name}§7: §6${ApexFormatters.compactNumber(row.perCoupon)}")
		val width = text.maxOf { Client.font.width(it) }
		val left = maxOf(bounds.x_Apex - 6 - width, 2)
		for ((i, t) in text.withIndex()) event.context.text(Client.font, t, left, bounds.y_Apex + 4 + i * 10, -1, true)
	}
}
