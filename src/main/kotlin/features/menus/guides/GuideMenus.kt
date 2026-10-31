// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.guides

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.collections.TileMenuView
import dev.ashtonite365.apex.features.menus.collections.TileSpec
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

@Config
object BlockGuideMenuSettings : MenuSettings("block-guide") {
	val reset by button("reset") { resetToDefaults() }
}

@Config
object EnchantGuideMenuSettings : MenuSettings("enchant-guide") {

	val groupUltimate by toggle("group-ultimate") { true }
	val reset by button("reset") { resetToDefaults() }
}

object BlockGuideMenu : CustomMenu {
	override val id = "block-guide"
	override val displayName = "Handy Block Guide"
	override val title = BlockGuideParser.TITLE
	override val settings get() = BlockGuideMenuSettings
	override val defaultSize = 420 to 240
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this); MenuRegistry.register(EnchantGuideMenu) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = BlockGuideView(screen)
}

object EnchantGuideMenu : CustomMenu {
	override val id = "enchant-guide"
	override val displayName = "Enchantment Guide"
	override val title = EnchantGuideParser.TITLE
	override val settings get() = EnchantGuideMenuSettings
	override val defaultSize = 480 to 290
	override fun create(screen: AbstractContainerScreen<*>): MenuView = EnchantGuideView(screen)
}

class BlockGuideView(screen: AbstractContainerScreen<*>) : TileMenuView<BlockGuidePage>(screen) {
	override fun palette() = BlockGuideMenuSettings.customisation.palette
	override val tileW get() = 118
	override fun parse(title: String, slots: List<SlotText>) = BlockGuideParser.parse(title, slots, screen.menu.slots.size - 36)

	override fun draw(ctx: GuiGraphicsExtractor, m: BlockGuidePage, mx: Int, my: Int) {
		val bottom = y + h - 24
		when (m) {
			is BlockGuidePage.Main -> {
				val top = header(ctx, mx, my, "Handy Block Guide", null, "§7Block classes and the stats that affect them")
				groups(ctx, mx, my, top, bottom, listOf("Block Classes" to m.categories.map { c ->
					TileSpec(c.slot, c.name, c.affectedBy.firstOrNull()?.let { "§b$it" } ?: "§8no fortune stat", null, MenuTheme.GREY,
						listOf("§f${c.name}") + (if (c.affectedBy.isEmpty()) listOf("§8Not affected by fortune/spread stats")
							else listOf("§7Affected by:") + c.affectedBy.map { "§b - $it" }) + listOf("", "§eClick to view"))
				}))
			}
			is BlockGuidePage.Category -> {
				val top = header(ctx, mx, my, m.name, null,
					if (m.affectedBy.isEmpty()) "§8Not affected by fortune/spread stats" else "§7Affected by: §b" + m.affectedBy.joinToString("§7, §b"))
				groups(ctx, mx, my, top, bottom, m.groups().map { (label, bs) -> label to bs.map { b ->
					TileSpec(b.slot, b.name, b.strength?.let { "§7Strength §f${fmtNum(it.toDouble())}" } ?: b.kind?.let { "§8$it" }, null, MenuTheme.GREY)
				} })
			}
		}
		navRow(ctx, mx, my, m.nav.map { it.slot to it.name })
		tooltip(ctx, mx, my)
	}
}

class EnchantGuideView(screen: AbstractContainerScreen<*>) : TileMenuView<EnchantGuidePage>(screen) {
	override fun palette() = EnchantGuideMenuSettings.customisation.palette
	override val tileW get() = 128
	override fun parse(title: String, slots: List<SlotText>) = EnchantGuideParser.parse(title, slots, screen.menu.slots.size - 36)

	override fun draw(ctx: GuiGraphicsExtractor, m: EnchantGuidePage, mx: Int, my: Int) {
		val bottom = y + h - 24
		val top = header(ctx, mx, my, "Enchantment Guide" + (m.page?.let { " §8(${it.first}/${it.second})" } ?: ""), null,
			"§7${m.enchants.size} on this page" + (m.filter?.let { " §8· §7filter §e$it" } ?: ""))
		val g = if (EnchantGuideMenuSettings.groupUltimate) m.groups() else listOf("Enchantments" to m.enchants)
		groups(ctx, mx, my, top, bottom, g.map { (label, es) -> label to es.map { e ->
			TileSpec(e.slot, e.name, listOfNotNull(e.appliedTo.firstOrNull()?.let { a -> a + if (e.appliedTo.size > 1) " +${e.appliedTo.size - 1}" else "" },
				e.requiredEnchanting?.let { "§bEnch $it" }).joinToString(" §8· "), null, if (e.ultimate) 0xFFFF55FF.toInt() else MenuTheme.GREY,
				listOf((if (e.ultimate) "§d§l" else "§9") + e.name) + e.description.map { "§7$it" } +
					section("Sources", e.sources) + section("Applied To", e.appliedTo) +
					(e.requiredEnchanting?.let { listOf("", "§7Requires §bEnchanting $it") } ?: emptyList()) + section("Conflicts", e.conflicts, "§c"))
		} })
		val nav = m.nav.map { it.slot to it.name }.toMutableList()
		m.searchSlot?.let { nav += it to "Search" }
		m.filterSlot?.let { nav += it to "Filter: ${m.filter ?: "?"}" }
		navRow(ctx, mx, my, nav)
		tooltip(ctx, mx, my)
	}

	private fun section(name: String, items: List<String>, colour: String = "§f") =
		if (items.isEmpty()) emptyList() else listOf("", "§7$name:") + items.map { "$colour - $it" }
}
