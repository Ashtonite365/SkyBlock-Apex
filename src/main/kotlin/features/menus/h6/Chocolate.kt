// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.h6

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.events.helpers.EventHelpers
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object ChocolateParser {
	val TITLE = Regex("^(?:Chocolate Factory|Chocolate Shop|Rabbit Hitman|Chocolate Factory Milestones|(?:\\(\\d+/\\d+\\) )?Hoppity's Collection)$")
	private val EMPLOYEE = Regex("^(.+?) - \\[(\\d+)] (.+)$")
	private val REMAINING = Regex("^([\\d,]+) remaining$")
	private val PROGRESS = Regex("^Progress to .+?: (\\d+(?:\\.\\d+)?%)$")
	const val NOT_FOUND = "You have not found this rabbit yet!"

	fun cost(lore: List<String>) = HubText.block(lore, "Cost").firstOrNull()
	fun remaining(lore: List<String>) = HubText.clean(lore).firstNotNullOfOrNull { REMAINING.matchEntire(it)?.groupValues?.get(1) }
	private fun bottomRow(filled: List<SlotText>) = BagsParser.bottomRow(filled)

	fun parse(title: String, slots: List<SlotText>, best: Int? = null, extraTooltip: (SlotText) -> List<String> = { emptyList() }): HubModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val filled = slots.filter { !HubText.isEmpty(it) }
		if (filled.isEmpty()) return null
		return when {
			t.endsWith("Hoppity's Collection") -> hoppity(t, filled)
			t == "Chocolate Shop" -> shop(filled)
			t == "Rabbit Hitman" -> hitman(filled)
			t == "Chocolate Factory Milestones" -> milestones(filled)
			else -> factory(filled, best, extraTooltip)
		}
	}

	private fun factory(filled: List<SlotText>, best: Int?, extra: (SlotText) -> List<String>): HubModel? {
		val head = filled.firstOrNull { HubText.block(it.lore, "Chocolate Production").isNotEmpty() } ?: return null
		val body = filled.filter { it !== head && !HubText.isNav(it) }.sortedBy { it.slot }
		fun tile(s: SlotText, info: List<String>) = HubTile(s.slot, s.cleanName, info, tooltip = HubText.infoLines(s.lore) + extra(s), highlight = s.slot == best)
		fun employee(s: SlotText): HubTile? = EMPLOYEE.matchEntire(s.cleanName)?.let { m ->
			HubTile(s.slot, m.groupValues[1], listOfNotNull("[${m.groupValues[2]}] ${m.groupValues[3]}", cost(s.lore)), tooltip = HubText.infoLines(s.lore) + extra(s), highlight = s.slot == best) }
		fun upgrade(s: SlotText) = tile(s, listOfNotNull(cost(s.lore)) + HubText.clean(s.lore).filter { it.startsWith("Status:") || it.startsWith("Charges:") })
		fun other(s: SlotText) = tile(s, HubText.clean(s.lore).filter { Regex("^[A-Z][A-Za-z' ]+: .+").matches(it) }.take(2).ifEmpty { HubText.infoLines(s.lore).take(1) })
		fun any(s: SlotText) = employee(s) ?: if (cost(s.lore) != null) upgrade(s) else other(s)
		val kv = HubText.kv(head.lore)
		val production = HubText.block(head.lore, "Chocolate Production").firstOrNull()
		val cookie = HubTile(head.slot, head.cleanName, listOfNotNull(production), tooltip = HubText.infoLines(head.lore))
		val factoryRow = body.filter { it.slot in FACTORY_ROW }
		val upgradeRow = body.filter { it.slot in UPGRADE_ROW }
		val menuRow = body.filter { it.slot in MENU_ROW }
		val rest = body.filter { it !in factoryRow && it !in upgradeRow && it !in menuRow }
		val chips = listOfNotNull("Chocolate" to head.cleanName.removeSuffix(" Chocolate"),
			production?.let { "Production" to it },
			kv["all-time chocolate"]?.let { "All-time" to it },
			filled.firstNotNullOfOrNull { HubText.kv(it.lore)["your barn"] }?.let { "Barn" to it })
		return HubModel("Chocolate Factory", chips, listOf(
			HubSection("Chocolate", listOf(cookie)),
			HubSection("Employees", factoryRow.map(::any)),
			HubSection("Upgrades", upgradeRow.map(::any)),
			HubSection("Menus", menuRow.map(::any)),
			HubSection("More", rest.map(::any)),
		).filter { it.tiles.isNotEmpty() }, HubText.navOf(filled))
	}

	val FACTORY_ROW = 27..35
	val UPGRADE_ROW = 36..44
	val MENU_ROW = 45..53

	private fun shop(filled: List<SlotText>): HubModel? {
		val items = filled.filter { cost(it.lore) != null }
		if (items.isEmpty()) return null
		val tiles = items.map { s -> HubTile(s.slot, HubText.colour(HubText.rarity(s.lore)) + s.cleanName,
			listOfNotNull("§6" + cost(s.lore), remaining(s.lore)?.let { "§7$it left" }), tooltip = HubText.infoLines(s.lore)) }
		val other = filled.filter { it !in items && !HubText.isNav(it) && it.cleanName != "Sell Item" }
		val chips = listOf("Items" to items.size.toString()) + other.flatMap { HubText.kv(it.lore).entries.take(1).map { e -> e.key.replaceFirstChar(Char::uppercase) to e.value } }
		return HubModel("Chocolate Shop", chips, listOf(HubSection("Shop", tiles)) + listOf(HubSection("More", other.map { HubTile(it.slot, it.cleanName, HubText.infoLines(it.lore).take(1)) })).filter { it.tiles.isNotEmpty() },
			HubText.navOf(filled))
	}

	private fun hoppity(t: String, filled: List<SlotText>): HubModel {
		val header = filled.firstOrNull { it.cleanName == "Hoppity's Collection" }
		val bottom = bottomRow(filled)
		val rabbits = filled.filter { it !== header && it.slot < bottom && !HubText.isNav(it) && HubText.rarity(it.lore) != null }
		val tiles = rabbits.map { r -> val found = HubText.clean(r.lore).none { it == NOT_FOUND }
			HubTile(r.slot, (if (found) HubText.colour(HubText.rarity(r.lore)) else "§8") + r.cleanName,
				listOf(if (found) "§aFound" else "§7Not found") + HubText.clean(r.lore).filter { it.startsWith("Duplicates") }.take(1),
				tooltip = HubText.infoLines(r.lore)) }
		val kv = header?.let { HubText.kv(it.lore) }.orEmpty()
		val total = header?.let { HubText.clean(it.lore).firstOrNull { l -> Regex("^\\d+/\\d+$").matches(l) } }
		val actions = filled.filter { it.slot >= bottom && !HubText.isNav(it) }
		val chips = listOfNotNull(HubText.page(t)?.let { "Page" to "${it.first}/${it.second}" },
			kv["rabbits found"]?.let { "Found" to (it + (total?.let { x -> " ($x)" } ?: "")) },
			"On page" to "${tiles.count { it.info.first() == "§aFound" }}/${tiles.size}")
		return HubModel(MenuText.clean(t), chips, if (tiles.isEmpty()) emptyList() else listOf(HubSection("Rabbits", tiles)),
			HubText.navOf(filled) + actions.map { HubText.actionLabel(it) to it.slot }, emptyText = "No rabbits on this page")
	}

	private fun hitman(filled: List<SlotText>): HubModel? {
		val header = filled.firstOrNull { it.cleanName == "Rabbit Hitman" && cost(it.lore) == null }
		val slotsForSale = filled.filter { it !== header && cost(it.lore) != null }

		val (locked, open) = slotsForSale.partition { HubText.requirement(it.lore) != null }
		val bottom = bottomRow(filled)
		val kv = header?.let { HubText.kv(it.lore) }.orEmpty()
		val chips = listOfNotNull(kv["available eggs"]?.let { "Available eggs" to it }, kv["purchased slots"]?.let { "Purchased slots" to it },
			locked.size.takeIf { it > 0 }?.let { "Locked" to it.toString() })
		val tiles = open.map { HubTile(it.slot, it.cleanName, listOfNotNull(cost(it.lore)), tooltip = HubText.infoLines(it.lore)) }
		return HubModel("Rabbit Hitman", chips, if (tiles.isEmpty()) emptyList() else listOf(HubSection("Egg slots", tiles)),
			HubText.navOf(filled) + filled.filter { it.slot >= bottom && !HubText.isNav(it) }.map { HubText.actionLabel(it) to it.slot }, emptyText = "Nothing to buy")
	}

	private fun milestones(filled: List<SlotText>): HubModel? {
		val bottom = bottomRow(filled)
		val ms = filled.filter { it.slot < bottom && !HubText.isNav(it) }
		if (ms.isEmpty()) return null
		fun unlocked(s: SlotText) = HubText.clean(s.lore).any { it == "UNLOCKED" }
		val tiles = ms.map { s ->
			val c = HubText.clean(s.lore)
			val progress = c.firstNotNullOfOrNull { PROGRESS.matchEntire(it)?.groupValues?.get(1) }
			val reward = c.indexOfFirst { it.endsWith(" Reward") }.takeIf { it >= 0 }?.let { c.getOrNull(it + 1) }
			HubTile(s.slot, (if (unlocked(s)) "§a" else "§7") + s.cleanName,
				listOfNotNull(if (unlocked(s)) "Unlocked" else progress?.let { "Locked · $it" } ?: HubText.requirement(s.lore) ?: "Locked", reward),
				clickable = false, tooltip = HubText.infoLines(s.lore))
		}
		val u = ms.filter(::unlocked)
		val chips = listOf("Unlocked (shown)" to "${u.size}/${ms.size}")
		return HubModel("Chocolate Factory Milestones", chips,
			listOf(HubSection("Unlocked", tiles.filter { it.info.first() == "Unlocked" }), HubSection("Locked", tiles.filter { it.info.first() != "Unlocked" })).filter { it.tiles.isNotEmpty() },
			HubText.navOf(filled) + filled.filter { it.slot >= bottom && !HubText.isNav(it) }.map { HubText.actionLabel(it) to it.slot })
	}
}

@Config
object ChocolateSettings : MenuSettings("chocolate") {
	val reset by button("reset") { resetToDefaults() }
}

object ChocolateMenu : CustomMenu {
	override val id = "chocolate"
	override val displayName = "Chocolate Factory"
	override val title = ChocolateParser.TITLE
	override val settings get() = ChocolateSettings
	override val defaultSize = 400 to 260

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = ChocolateView(screen)
}

class ChocolateView(screen: AbstractContainerScreen<*>) : HubView(screen, { ChocolateSettings }) {
	override fun parse(title: String, slots: List<SlotText>): HubModel? {
		val factory = MenuText.clean(title) == "Chocolate Factory"
		val best = if (factory && EventHelpers.TConfig.chocolateBestUpgrade) runCatching { EventHelpers.bestEmployee(screen) }.getOrNull() else null
		return ChocolateParser.parse(title, slots, best) { s -> if (factory && EventHelpers.TConfig.chocolateUpgradeTooltip) upgradeTooltip(s, slots) else emptyList() }
	}

	private fun upgradeTooltip(s: SlotText, slots: List<SlotText>): List<String> {
		if (s.slot !in 28..34) return emptyList()
		val cost = ChocolateParser.cost(s.lore)?.let { Regex("^([0-9,]+) Chocolate$").matchEntire(it) }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return emptyList()
		val head = slots.firstOrNull { HubText.block(it.lore, "Chocolate Production").isNotEmpty() }
		val current = head?.cleanName?.removeSuffix(" Chocolate")?.replace(",", "")?.toDoubleOrNull()
		val perSecond = head?.let { HubText.block(it.lore, "Chocolate Production").firstOrNull()?.substringBefore(" ")?.replace(",", "")?.toDoubleOrNull() }
		return EventHelpers.employeeTooltip(s.slot - 28, cost, current, perSecond)
	}

	override fun liveOnly(m: HubModel) = true
}
