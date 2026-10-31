/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): max-buy helper. On a bazaar product page it shows how many you can afford at the
 * instant-buy price (purse from the scoreboard) and how many fit in your inventory. Optionally pre-fills
 * that number into the "How many do you want?" amount sign as text only; it is never submitted.
 */
package dev.ashtonite365.apex.features.apexmarket

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object MaxBuy {
	@Config
	object TConfig : ManagedConfig("max-buy", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }
		val prefillAmount by toggle("prefill-amount") { false }
	}

	private val PURSE = Regex("^(?:Purse|Piggy): ([0-9,.]+)")
	fun parsePurse(lines: List<String>): Double? =
		lines.firstNotNullOfOrNull { PURSE.find(it.trim())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }

	fun maxAffordable(purse: Double, pricePerUnit: Double): Long =
		if (pricePerUnit <= 0) 0 else kotlin.math.floor(purse / pricePerUnit).toLong()

	fun maxCarry(emptySlots: Int, partialStackCounts: List<Int>, stackSize: Int = 64): Long =
		emptySlots.toLong() * stackSize + partialStackCounts.sumOf { (stackSize - it).coerceAtLeast(0) }

	fun showBox(apexBazaarOn: Boolean) = !apexBazaarOn

	private val TITLE = Regex("^.+ ➜ (.+)$")
	private var last: Triple<ItemId, Long, Long>? = null

	private fun compute(id: ItemId): Triple<ItemId, Long, Long>? {
		val q = HypixelStaticData.bazaarData[bazaarStockOf(id)]?.quickStatus ?: return null
		val purse = parsePurse(ScoreboardUtil.simplifiedScoreboardLines) ?: return null
		val inv = Client.player?.inventory ?: return null
		var empty = 0; val partial = mutableListOf<Int>()
		for (i in 0 until 36) {
			val s = inv.getItem(i)
			if (s.isEmpty) empty++ else if (s.view().repoItemId == id) partial.add(s.count)
		}
		return Triple(id, maxAffordable(purse, q.buyPrice), maxCarry(empty, partial))
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (!showBox(dev.ashtonite365.apex.features.apexbzmenu.BzMenu.switchOn())) return
		val name = TITLE.matchEntire(screen.title.bareText.trim())?.groupValues?.get(1) ?: return
		val id = ItemNameLookup.guessItemByName(name, false) ?: return
		val r = compute(id) ?: return
		last = r
		val s = ApexFormatters::compactNumber
		val lines = listOf("§lMax buy", "§7Afford: §6${s(r.second.toDouble())}", "§7Carry: §f${s(r.third.toDouble())}",
			"§7Max: §a${s(minOf(r.second, r.third).toDouble())}")
		val acc = screen as AccessorHandledScreen
		val x = acc.x_Apex + acc.backgroundWidth_Apex + 3
		val y = acc.y_Apex + acc.backgroundHeight_Apex - lines.size * 10 - 6
		val w = lines.maxOf { Client.font.width(it) } + 8
		event.drawContext.fill(x, y, x + w, y + lines.size * 10 + 6, 0xE0202020.toInt())
		lines.forEachIndexed { i, l -> event.drawContext.text(Client.font, l, x + 4, y + 4 + i * 10, 0xFFFFFFFF.toInt()) }
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		if (!TConfig.prefillAmount) return
		val sign = event.new as? AbstractSignEditScreen ?: return
		val old = event.old as? AbstractContainerScreen<*> ?: return
		if (!old.title.bareText.trim().startsWith("How many do you want")) return
		val r = last ?: return
		val n = minOf(r.second, r.third)
		if (n <= 0) return
		val lines = (sign as AccessorSignEditScreen).messages_Apex
		if (lines.isNotEmpty() && lines[0].isBlank()) lines[0] = n.toString()
	}
}
