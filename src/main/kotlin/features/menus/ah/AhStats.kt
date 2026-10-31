// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.ah

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class AhStatsModel(
	val created: Long?, val withBids: Long?, val withoutBids: Long?, val highestHeld: Long?,
	val earned: Long?, val fees: Long?, val sold: Map<String, Long>,
	val won: Long?, val bids: Long?, val highestBid: Long?, val spent: Long?, val bought: Map<String, Long>,
	val backSlot: Int?,
) {

	val netProfit get() = if (earned == null && fees == null && spent == null) null else (earned ?: 0) - (fees ?: 0) - (spent ?: 0)
	val sellThrough get() = AhStatsParser.ratio(withBids, created)
	val averageSale get() = AhStatsParser.ratio(earned, withBids)
	val winRate get() = AhStatsParser.ratio(won, bids)
}

object AhStatsParser {

	val TITLE = Regex("^(?i)(?:Co-?op\\s+)?Auction\\s+Stat(?:istic)?s$")
	val RARITIES = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic", "Divine", "Special", "Very Special", "Ultimate", "Admin")
	private val LINE = Regex("^(.+?):\\s*([\\d,]+)\\b.*$")

	fun ratio(a: Long?, b: Long?): Double? = if (a == null || b == null || b == 0L) null else a.toDouble() / b

	private fun fields(lore: List<String>) = lore.mapNotNull { LINE.matchEntire(MenuText.clean(it)) }
		.associate { it.groupValues[1].trim().lowercase() to it.groupValues[2].replace(",", "").toLongOrNull() }

	private fun rarities(f: Map<String, Long?>, suffix: String) = RARITIES.mapNotNull { r -> f["${r.lowercase()} $suffix"]?.let { r to it } }.toMap()

	private fun find(slots: List<SlotText>, vararg names: String) = slots.firstOrNull { s -> names.any { s.cleanName.equals(it, true) } }

	fun parse(title: String, slots: List<SlotText>): AhStatsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val seller = find(slots, "Seller Stats", "Seller Statistics")
		val buyer = find(slots, "Buyer Stats", "Buyer Statistics")
		if (seller == null && buyer == null) return null
		val s = seller?.let { fields(it.lore) } ?: emptyMap()
		val b = buyer?.let { fields(it.lore) } ?: emptyMap()
		val back = slots.firstOrNull { it.cleanName.lowercase().let { n -> n.startsWith("go back") || n == "back" } }
		return AhStatsModel(
			s["auctions created"], s["auctions completed with bids"], s["auctions completed without bids"], s["highest auction held"],
			s["total coins earned"], s["coins spent on fees"], rarities(s, "sold"),
			b["auctions won"], b["total bids"], b["highest bid"], b["coins spent"], rarities(b, "bought"),
			back?.slot)
	}

	fun pct(v: Double?) = v?.let { "%.1f%%".format(java.util.Locale.ROOT, it * 100) } ?: "—"
	fun coins(v: Number?) = v?.let { MenuText.coins(it.toDouble()) } ?: "—"
	fun colour(rarity: String): Int = when (rarity) {
		"Common" -> 0xFFFFFFFF; "Uncommon" -> 0xFF55FF55; "Rare" -> 0xFF5555FF; "Epic" -> 0xFFAA00AA
		"Legendary" -> 0xFFFFAA00; "Mythic" -> 0xFFFF55FF; "Divine" -> 0xFF55FFFF; "Special", "Very Special" -> 0xFFFF5555
		"Ultimate" -> 0xFFAA0000; else -> 0xFFAAAAAA
	}.toInt()
}

@Config
object AhStatsSettings : MenuSettings("ah-stats", panels = listOf("derived", "rarity")) {
	val hideZeroRarities by toggle("hide-zero-rarities") { false }
	val reset by button("reset") { resetToDefaults() }
}

object AhStatsMenu : CustomMenu {
	override val id = "ah-stats"
	override val displayName = "Auction Stats"
	override val title = AhStatsParser.TITLE
	override val settings get() = AhStatsSettings
	override val defaultSize = 320 to 230

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = AhStatsView(screen)
}

class AhStatsView(screen: AbstractContainerScreen<*>) : ApexViewBase<AhStatsModel>(screen) {
	private val c get() = AhStatsSettings.customisation
	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = AhStatsParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: AhStatsModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		ctx.text(font, "§6§lAuction Stats", x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		m.backSlot?.let { slot -> val bw = font.width("Back") + 10; button(ctx, mx, my, x + w - vw - bw - 8, y + 3, bw, 14, "Back") { clickSlot(slot, 0) } }

		var top = y + 22
		if (c.shows("derived")) {
			MenuTheme.panel(ctx, x + pad, top, w - 2 * pad, 30, p)
			val net = m.netProfit
			val items = listOf(
				"Net profit" to ((if (net != null && net < 0) "§c" else "§a") + AhStatsParser.coins(net)),
				"Sell-through" to "§f" + AhStatsParser.pct(m.sellThrough),
				"Avg sale" to "§6" + AhStatsParser.coins(m.averageSale),
				"Win rate" to "§f" + AhStatsParser.pct(m.winRate))
			val cw = (w - 2 * pad) / items.size
			items.forEachIndexed { i, (l, v) ->
				ctx.text(font, "§7$l", x + pad + i * cw + 5, top + 5, MenuTheme.GREY)
				ctx.text(font, MenuTheme.fit(v, cw - 8, font::width), x + pad + i * cw + 5, top + 16, MenuTheme.WHITE)
			}
			top += 36
		}
		val colW = (w - 2 * pad - 6) / 2
		column(ctx, x + pad, top, colW, "§aSeller", listOf(
			"Auctions created" to AhStatsParser.coins(m.created), "Sold (with bids)" to AhStatsParser.coins(m.withBids),
			"Unsold (no bids)" to AhStatsParser.coins(m.withoutBids), "Highest held" to AhStatsParser.coins(m.highestHeld),
			"Coins earned" to "§6" + AhStatsParser.coins(m.earned), "Fees paid" to "§c" + AhStatsParser.coins(m.fees)), m.sold)
		column(ctx, x + pad + colW + 6, top, colW, "§bBuyer", listOf(
			"Auctions won" to AhStatsParser.coins(m.won), "Total bids" to AhStatsParser.coins(m.bids),
			"Highest bid" to "§6" + AhStatsParser.coins(m.highestBid), "Coins spent" to "§c" + AhStatsParser.coins(m.spent)), m.bought)
	}

	private fun column(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, cw: Int, head: String, rows: List<Pair<String, String>>, rar: Map<String, Long>) {
		val bottom = y + h - 6
		MenuTheme.panel(ctx, cx, cy, cw, bottom - cy, palette())
		ctx.text(font, "§l$head", cx + 5, cy + 5, MenuTheme.WHITE)
		var ry = cy + 17
		for ((l, v) in rows) {
			ctx.text(font, "§7$l", cx + 5, ry, MenuTheme.GREY)
			ctx.text(font, "§f$v", cx + cw - 5 - font.width("§f$v"), ry, MenuTheme.WHITE); ry += 10
		}
		if (!c.shows("rarity")) return
		ry += 4
		val shown = rar.filter { !AhStatsSettings.hideZeroRarities || it.value > 0 }
		val max = (shown.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
		val labelW = 52; val barW = cw - 10 - labelW - 24
		for ((r, n) in shown) {
			if (ry + 8 > bottom - 2) break
			val col = AhStatsParser.colour(r)
			ctx.text(font, MenuTheme.fit(r, labelW - 2, font::width), cx + 5, ry, col)
			val bx = cx + 5 + labelW
			ctx.fill(bx, ry + 2, bx + barW, ry + 6, 0xFF2A2A2A.toInt())
			ctx.fill(bx, ry + 2, bx + (barW * n / max).toInt(), ry + 6, col)
			ctx.text(font, "$n", bx + barW + 3, ry, MenuTheme.WHITE)
			ry += 9
		}
	}
}
