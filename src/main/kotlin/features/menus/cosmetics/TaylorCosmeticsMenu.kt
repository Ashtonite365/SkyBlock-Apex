// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.cosmetics

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class CosmeticItem(
	val slot: Int, val name: String, val rarity: String?, val rarityLine: String?,
	val gems: Int?, val priceText: String?,

	val owned: String?,
	val lore: List<String>,
)

object TaylorCosmetics {
	const val MAIN = "Taylor's Cosmetics"
	const val BUNDLES = "Seasonal Bundles"
	const val COLLECTION = "Taylor's Collection"
	val TITLE = Regex("^(?:Taylor's Cosmetics|Seasonal Bundles|Taylor's Collection)$")

	val RARITY_ORDER = listOf("ULTIMATE", "VERY SPECIAL", "SPECIAL", "DIVINE", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON")
	private val GEMS = Regex("^([\\d,]+)\\s+Gems?\\b", RegexOption.IGNORE_CASE)
	private val BALANCE = Regex("You have:\\s*([\\d,]+)\\s*Gems?", RegexOption.IGNORE_CASE)

	private val OWNED = Regex("\\b(?:already (?:own|owned|purchased|unlocked|bought)|you own|owned|unlocked|purchased)\\b", RegexOption.IGNORE_CASE)
	private val NOT_OWNED = Regex("\\b(?:not (?:owned|unlocked|purchased)|to unlock|unlock the)\\b", RegexOption.IGNORE_CASE)

	fun gems(text: String): Int? = GEMS.find(MenuText.clean(text))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
	fun balance(slots: List<SlotText>): String? = slots.firstNotNullOfOrNull { s -> s.cleanLore.firstNotNullOfOrNull { BALANCE.find(it)?.groupValues?.get(1) } }

	fun item(s: SlotText): CosmeticItem? {
		val cost = HubText.block(s.lore, "Cost")
		val price = cost.firstOrNull { gems(it) != null } ?: return null
		val lore = s.cleanLore
		val rarity = HubText.rarity(s.lore)
		val rarityLine = lore.lastOrNull { HubText.RARITY_FOOTER.matches(it) }
		val owned = lore.firstOrNull { OWNED.containsMatchIn(it) && !NOT_OWNED.containsMatchIn(it) && !it.startsWith("Click", true) }
		return CosmeticItem(s.slot, s.cleanName, rarity, rarityLine, gems(price), price, owned, lore)
	}

	fun rank(r: String?) = RARITY_ORDER.indexOf(r).let { if (it < 0) RARITY_ORDER.size else it }

	fun groups(items: List<CosmeticItem>): List<Pair<String?, List<CosmeticItem>>> =
		items.groupBy { it.rarity }.toList().sortedBy { rank(it.first) }
			.map { (r, l) -> r to l.sortedWith(compareByDescending<CosmeticItem> { it.gems ?: -1 }.thenBy { it.slot }) }

	private fun fmt(n: Int) = "%,d".format(n)

	fun heading(rarity: String?, l: List<CosmeticItem>): String {
		val name = rarity?.lowercase()?.split(' ')?.joinToString(" ") { it.replaceFirstChar(Char::titlecase) } ?: "Other"
		val p = l.mapNotNull { it.gems }
		val price = when { p.isEmpty() -> ""; p.min() == p.max() -> " · ${fmt(p.min())} Gems"; else -> " · ${fmt(p.min())}-${fmt(p.max())} Gems" }
		return "$name (${l.size})$price"
	}

	fun info(i: CosmeticItem) = listOfNotNull(i.rarityLine?.let { HubText.colour(i.rarity) + it + "§7" }, i.priceText?.let { "§b$it§7" },
		i.owned?.let { "§a$it§7" }).joinToString(" · ")

	fun nav(slots: List<SlotText>) = HubText.navOf(slots) + listOfNotNull(slots.firstOrNull { it.cleanName.equals("SkyBlock Gems", true) }?.let { it.cleanName to it.slot })

	private fun chips(slots: List<SlotText>) = listOfNotNull(balance(slots)?.let { "SkyBlock Gems" to it })

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val nav = nav(chest); val navSlots = nav.map { it.second }.toSet()
		return if (t == MAIN) main(t, chest, nav, navSlots) else shop(t, chest, nav, navSlots)
	}

	private fun main(t: String, chest: List<SlotText>, nav: List<Pair<String, Int>>, navSlots: Set<Int>): HubModel? {
		val tiles = chest.filter { it.slot !in navSlots }.map { s ->
			val kv = HubText.kv(s.lore)

			val info = listOfNotNull(kv["current bundle"]?.let { "Current bundle: $it" }, kv["ends in"]?.let { "Ends in: $it" }).joinToString(" · ")
				.ifEmpty { HubText.infoLines(s.lore).joinToString(" ") }
			HubTile(s.slot, s.cleanName, listOf(info), tooltip = HubText.clean(s.lore), clickable = s.cleanLore.any { it.startsWith("Click", true) })
		}
		if (tiles.isEmpty()) return null
		return HubModel(t, chips(chest), listOf(HubSection("Shop", tiles)), nav)
	}

	private fun shop(t: String, chest: List<SlotText>, nav: List<Pair<String, Int>>, navSlots: Set<Int>): HubModel? {
		val items = chest.filter { it.slot !in navSlots }.mapNotNull(::item)
		if (items.isEmpty()) return null
		val other = chest.filter { it.slot !in navSlots && items.none { i -> i.slot == it.slot } }
		val sections = groups(items).map { (r, l) ->
			HubSection(heading(r, l), l.map { HubTile(it.slot, it.name, listOf(info(it)), tooltip = it.lore, highlight = it.owned != null) })
		} + listOfNotNull(other.takeIf { it.isNotEmpty() }?.let { o ->
			HubSection("Other", o.map { HubTile(it.slot, it.cleanName, listOf(HubText.infoLines(it.lore).joinToString(" ")), tooltip = HubText.clean(it.lore),
				clickable = it.cleanLore.any { l -> l.startsWith("Click", true) }) }) })
		return HubModel(t, chips(chest), sections, nav)
	}
}

@Config
object TaylorCosmeticsSettings : MenuSettings("taylor-cosmetics") {
	val reset by button("reset") { resetToDefaults() }
}

object TaylorCosmeticsMenu : CustomMenu {
	override val id = "taylor-cosmetics"
	override val displayName = "Taylor's Cosmetics"
	override val title = TaylorCosmetics.TITLE
	override val settings get() = TaylorCosmeticsSettings
	override val defaultSize = 460 to 280
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) TaylorCosmeticsView(screen) else null
}

class TaylorCosmeticsView(screen: AbstractContainerScreen<*>) : HubView(screen, { TaylorCosmeticsSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = TaylorCosmetics.parse(title, slots, screen.menu.slots.size - 36)
	override fun wrapsTileText(m: HubModel) = true
	override fun liveOnly(m: HubModel) = true
}
