// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.h6

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.CopperReadout
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.features.menus.MenuClicks

object NpcShopParser {
	data class Cost(val coins: Double?, val items: List<Pair<String, Int>>, val copper: Double? = null)
	data class Offer(val slot: Int, val name: String, val cost: Cost, val stock: Long?, val stackable: Boolean, val rarity: String?, val locked: String? = null)
	data class Shop(val offers: List<Offer>, val sections: List<Pair<String, Int>>, val sellSlot: Int, val buyback: SlotText?,
	                val backTo: String?, val page: Int?, val nextPage: Int?)

	private val COINS = Regex("^([\\d,]+(?:\\.\\d+)?[kKmMbB]?) Coins?$")

	private val COPPER = Regex("^([\\d,]+(?:\\.\\d+)?[kKmM]?) Copper$", RegexOption.IGNORE_CASE)
	private val ITEM = Regex("^(.+?)(?: x([\\d,]+))?$")
	private val STOCK = Regex("^([\\d,]+) remaining$")

	fun cost(lore: List<String>): Cost? {
		val lines = HubText.block(lore, "Cost").ifEmpty { return null }
		var coins: Double? = null
		var copper: Double? = null
		val items = mutableListOf<Pair<String, Int>>()
		for (l in lines) {
			val c = COINS.matchEntire(l)
			val cu = if (c == null) COPPER.matchEntire(l) else null
			if (c != null) coins = (coins ?: 0.0) + (MenuText.number(c.groupValues[1]) ?: 0.0)
			else if (cu != null) copper = (copper ?: 0.0) + (MenuText.number(cu.groupValues[1]) ?: 0.0)
			else ITEM.matchEntire(l)?.let { items += it.groupValues[1] to (it.groupValues[2].replace(",", "").toIntOrNull() ?: 1) }
		}
		return Cost(coins, items, copper)
	}

	fun stock(lore: List<String>) = HubText.block(lore, "Stock").firstNotNullOfOrNull { STOCK.matchEntire(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }

	private fun isBuyback(s: SlotText) = HubText.clean(s.lore).any { it.contains("buyback", true) || it.contains("buy back", true) }
	private fun isSellItem(s: SlotText) = s.cleanName.equals("Sell Item", true) || HubText.clean(s.lore).any { it.contains("to sell", true) && it.contains("inventory", true) }

	fun detect(slots: List<SlotText>): Shop? {
		val filled = slots.filter { !HubText.isEmpty(it) }
		if (filled.isEmpty()) return null
		val lastRow = filled.maxOf { it.slot } / 9
		val sell = filled.firstOrNull { it.slot / 9 == lastRow && it.slot % 9 == 4 && (isSellItem(it) || isBuyback(it)) } ?: return null
		val offers = filled.filter { it.slot != sell.slot }.mapNotNull { s ->
			val c = cost(s.lore) ?: return@mapNotNull null
			Offer(s.slot, s.cleanName, c, stock(s.lore), HubText.clean(s.lore).any { it.startsWith("Right-click for more trading options", true) }, HubText.rarity(s.lore),
				HubText.clean(s.lore).takeIf { l -> l.any { it.equals("Not unlocked!", true) } }?.let { l -> l.firstOrNull { it.contains("Requires ") }?.substringAfter("Requires ")?.trim()?.let { "Requires $it" } ?: "Not unlocked" })
		}
		val sections = filled.filter { s -> s.slot != sell.slot && !HubText.isNav(s) && offers.none { it.slot == s.slot } && HubText.clean(s.lore).any { it == "Click to view!" } }
			.map { it.cleanName to it.slot }
		if (offers.isEmpty() && sections.isEmpty()) return null
		val back = filled.firstOrNull { it.cleanName == "Go Back" }?.cleanLore?.firstOrNull()?.takeIf { it.startsWith("To ") }?.removePrefix("To ")
		val next = filled.firstOrNull { it.cleanName == "Next Page" }
		val prev = filled.firstOrNull { it.cleanName == "Previous Page" }
		fun pageOf(s: SlotText?) = s?.cleanLore?.firstNotNullOfOrNull { Regex("^Page (\\d+)$").matchEntire(it)?.groupValues?.get(1)?.toInt() }
		val page = pageOf(next)?.minus(1) ?: pageOf(prev)?.plus(1)
		return Shop(offers, sections, sell.slot, sell.takeIf(::isBuyback), back, page, next?.slot)
	}

	fun costText(c: Cost) = (listOfNotNull(c.coins?.let { MenuText.coins(it) + " coins" }, c.copper?.let { dev.ashtonite365.apex.features.menus.support.PurseReadout.amount(it) + " Copper" }) + c.items.map { (n, a) -> if (a > 1) "$n x$a" else n }).joinToString(" + ")

	val hubSections = mutableMapOf<String, List<String>>()

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		val t = MenuText.clean(title)
		val shop = detect(slots) ?: return null
		if (shop.sections.isNotEmpty()) hubSections[t] = shop.sections.map { it.first }
		val offerTiles = shop.offers.map { o ->
			HubTile(o.slot, HubText.colour(o.rarity) + o.name, listOfNotNull("§6" + costText(o.cost), o.locked?.let { "§c$it" } ?: o.stock?.let { "§7Stock: $it" }),
				locked = o.locked != null, delayed = true, shiftButton = if (o.stackable) 1 else null,
				tooltip = listOf("Cost: §6" + costText(o.cost)) + listOfNotNull(o.stock?.let { "Stock: $it remaining" },
					if (o.stackable) "§eShift/right click: stack options" else null))
		}
		val buyback = shop.buyback?.let { listOf(HubTile(it.slot, "§c" + it.cleanName, HubText.infoLines(it.lore).take(2), delayed = true, tooltip = HubText.infoLines(it.lore))) }.orEmpty()
		val nav = HubText.navOf(slots).map { (l, s) ->
			if (l == "Next Page" && shop.page != null) "Next (Page ${shop.page + 1})" to s
			else if (l == "Previous Page" && shop.page != null) "Prev (Page ${shop.page - 1})" to s else l to s
		}
		val chips = listOfNotNull("Items" to shop.offers.size.toString(), shop.page?.let { "Page" to it.toString() },
			shop.offers.mapNotNull { it.cost.coins }.minOrNull()?.let { "Cheapest" to MenuText.coins(it) }
				?: shop.offers.mapNotNull { it.cost.copper }.minOrNull()?.let { "Cheapest" to dev.ashtonite365.apex.features.menus.support.PurseReadout.amount(it) + " Copper" },
			shop.backTo?.let { "Shop" to it }, if (buyback.isNotEmpty()) "Buyback" to "available" else null)

		val known = shop.backTo?.let { hubSections[it] }
		return when {
			shop.sections.isNotEmpty() && shop.offers.isEmpty() ->
				HubModel(t, chips.filter { it.first != "Items" } + ("Sections" to shop.sections.size.toString()),
					listOf(HubSection("Sections", shop.sections.map { (n, s) -> HubTile(s, n, HubText.infoLines(slots.first { it.slot == s }.lore).take(2)) })) + buybackSection(buyback), nav)
			known != null && t in known ->
				HubModel(t, chips, known.map { HubSection(it, if (it == t) offerTiles + buyback else emptyList()) }, nav, tabs = true, selectedTab = known.indexOf(t))
			else -> HubModel(t, chips, listOf(HubSection("Shop", offerTiles)) + buybackSection(buyback), nav)
		}
	}

	fun isCopperShop(title: String, slots: List<SlotText>): Boolean =
		MenuText.clean(title).startsWith("SkyMart", true) || detect(slots)?.offers?.any { it.cost.copper != null } == true

	private fun buybackSection(t: List<HubTile>) = if (t.isEmpty()) emptyList() else listOf(HubSection("Buyback", t))
}

@Config
object NpcShopSettings : MenuSettings("npc-shop") {

	val reset by button("reset") { resetToDefaults() }
}

object NpcShopMenu : CustomMenu {
	override val id = "npc-shop"
	override val displayName = "NPC Shops"

	override val title = Regex("(?!)")
	override val settings get() = NpcShopSettings
	override val defaultSize = 380 to 340

	override val slotPredicate: (String, List<SlotText>) -> Boolean = { t, slots -> NpcShopParser.parse(t, slots) != null }

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = NpcShopView(screen)
}

object NpcShopChain {
	data class Step(val slot: Int, val button: Int = 0, val delayed: Boolean = true)
	fun goBack(backSlot: Int) = Step(backSlot)
	fun section(sectionSlot: Int) = Step(sectionSlot)
}

class NpcShopView(screen: AbstractContainerScreen<*>) : HubView(screen, { NpcShopSettings }) {

	override fun showsInventory(m: HubModel) = true

	override fun showsPurse(m: HubModel) = true

	private var copperShop = false
	private var copperSlots: List<SlotText> = emptyList()

	override fun parse(title: String, slots: List<SlotText>): HubModel? {
		copperShop = NpcShopParser.isCopperShop(title, slots)
		copperSlots = if (copperShop) slots else emptyList()
		return NpcShopParser.parse(title, slots)
	}

	override fun wrapsTileText(m: HubModel) = copperShop

	override fun drawPurse(ctx: GuiGraphicsExtractor, m: HubModel, x: Int, y: Int, maxWidth: Int): Int =
		if (copperShop) CopperReadout.draw(ctx, font, copperSlots, x, y, maxWidth) else super.drawPurse(ctx, m, x, y, maxWidth)

	override fun onTab(m: HubModel, index: Int) {
		if (index == m.selectedTab) { tab = index; return }
		val back = m.nav.firstOrNull { it.first.startsWith("Back") }?.second ?: return
		send(NpcShopChain.goBack(back))
		val name = m.sections.getOrNull(index)?.title?.takeIf { it.isNotBlank() }?.let { " §e$it§7" } ?: ""
		dev.ashtonite365.apex.util.Client.showChat(net.minecraft.network.chat.Component.literal("§6[Apex] §7Back in the shop: now click$name there."))

	}

	private fun send(step: NpcShopChain.Step) {
		if (!windowStillOpen()) return
		val slot = screen.menu.slots.getOrNull(step.slot) ?: return
		MenuClicks.clickLater(screen.menu, slot, step.button)
	}

}
