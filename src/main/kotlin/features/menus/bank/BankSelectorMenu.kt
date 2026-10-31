// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.bank

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.guides.GuideLore
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

object BankSelectorParser {
	val TITLE = Regex("^Bank$", RegexOption.IGNORE_CASE)

	private val ACCOUNT = Regex("^(?:(Personal|Co-?op|Coop)\\s+)Bank\\s+Account$", RegexOption.IGNORE_CASE)

	fun isAccountChoice(s: SlotText) = ACCOUNT.matches(s.cleanName.trim())

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 45): HubModel? {
		if (!TITLE.matches(MenuText.clean(title).trim())) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val nav = HubText.navOf(chest); val navSlots = nav.map { it.second }.toSet()
		val (accounts, other) = chest.filter { it.slot !in navSlots }.partition(::isAccountChoice)
		if (accounts.isEmpty()) return null
		fun info(s: SlotText) = GuideLore.info(s.lore).takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
		val choices = accounts.map { HubTile(it.slot, it.cleanName, info(it), tooltip = HubText.clean(it.lore), delayed = true) }
		val rest = other.map { HubTile(it.slot, it.cleanName, info(it), tooltip = HubText.clean(it.lore), clickable = GuideLore.clickable(it) || it.lore.isEmpty()) }
		return HubModel("Bank", emptyList(), listOf(HubSection("Accounts", choices), HubSection("Other", rest)).filter { it.tiles.isNotEmpty() }, nav)
	}
}

object BankSelectorMenu : CustomMenu {
	override val id = "bank-selector"
	override val displayName = "Bank (account selector)"
	override val title = BankSelectorParser.TITLE

	override val settings get() = BankMenuSettings
	override val defaultSize = 340 to 200
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (screen.menu.slots.size > 36) BankSelectorView(screen) else null
}

class BankSelectorView(screen: AbstractContainerScreen<*>) : HubView(screen, { BankMenuSettings }) {
	override fun parse(title: String, slots: List<SlotText>) = BankSelectorParser.parse(title, slots, screen.menu.slots.size - 36)
	override fun wrapsTileText(m: HubModel) = true
	override fun liveOnly(m: HubModel) = true
}
