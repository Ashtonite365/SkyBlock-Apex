// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.collections

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

@Config
object CollectionsMenuSettings : MenuSettings("collections") {
	val reset by button("reset") { resetToDefaults() }
}

@Config
object CollectionPagesMenuSettings : MenuSettings("collection-pages") {

	val grouped by toggle("grouped") { true }
	val reset by button("reset") { resetToDefaults() }
}

object CollectionsMenu : CustomMenu {
	override val id = "collections"
	override val displayName = "Collections"
	override val title = CollectionsParser.MAIN
	override val settings get() = CollectionsMenuSettings
	override val defaultSize = 400 to 230
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this); MenuRegistry.register(CollectionPagesMenu) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = CollectionsView(screen, CollectionsMenuSettings)
}

object CollectionPagesMenu : CustomMenu {
	override val id = "collection-pages"
	override val displayName = "Collection Pages"
	override val title = CollectionsParser.PAGES

	override val slotPredicate: ((String, List<SlotText>) -> Boolean) get() = CollectionsParser::matchesItemPage
	override val settings get() = CollectionPagesMenuSettings
	override val defaultSize = 470 to 280
	override fun create(screen: AbstractContainerScreen<*>): MenuView = CollectionsView(screen, CollectionPagesMenuSettings)
}

class CollectionsView(screen: AbstractContainerScreen<*>, private val s: MenuSettings) : TileMenuView<CollPage>(screen) {

	override val widenTiles = true
	override fun palette() = s.customisation.palette
	private val warn by lazy { dev.ashtonite365.apex.features.menus.support.ApexReadWarn(if (s === CollectionPagesMenuSettings) CollectionPagesMenu.id else CollectionsMenu.id) }

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.render(ctx, mouseX, mouseY, delta)
		warn.check(screen.menu.containerId, dev.ashtonite365.apex.features.menus.support.MenuText.clean(screen.title.string), System.currentTimeMillis())
			?.let { dev.ashtonite365.apex.Apex.logger.warn(it) }
	}

	override fun parse(title: String, slots: List<SlotText>): CollPage? = read(title, slots)
		.also { warn.parsed(it != null, slots.count { s -> s.cleanName.isNotEmpty() }, System.currentTimeMillis()) }

	private fun read(title: String, slots: List<SlotText>): CollPage? {
		val p = CollectionsParser.parse(title, slots, screen.menu.slots.size - 36) ?: return null
		if (p !is CollPage.Item) return p

		return CollectionsParser.withPaneStates(p) { slot -> stacks[slot]?.let { BuiltInRegistries.ITEM.getKey(it.item).path } }
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: CollPage, mx: Int, my: Int) {
		val nav = m.nav.map { it.slot to it.name }
		val pageSuffix = m.page?.let { " §8(${it.first}/${if (it.second > 0) it.second else "?"})" } ?: ""
		val bottom = y + h - 24
		when (m) {
			is CollPage.Main -> {
				val top = header(ctx, mx, my, "Collections", m.header?.unlocked)
				groups(ctx, mx, my, top, bottom, listOf("Categories" to m.categories.map { t ->
					TileSpec(t.slot, t.name.removeSuffix(" Collections"), t.progress?.let { p -> "${p.current?.let(::fmtNum) ?: "?"}/${p.max?.let(::fmtNum) ?: "?"} unlocked" },
						t.progress?.fraction, if (t.progress?.done == true) MenuTheme.GOLD else MenuTheme.GREY)
				}))
			}
			is CollPage.Category -> {
				val top = header(ctx, mx, my, m.category + " Collections" + pageSuffix, m.header?.unlocked,
					summaryLine(m.items))
				val tiles = m.items.map(::itemTile)
				val g = if (CollectionPagesMenuSettings.grouped) CollectionsParser.groups(m.items).map { (l, ts) -> l to ts.map(::itemTile) }
					else listOf("Collections" to tiles)
				groups(ctx, mx, my, top, bottom, g)
			}
			is CollPage.Item -> {
				val next = m.next
				val top = header(ctx, mx, my, m.item + " Collection" + pageSuffix, next?.progress,
					listOfNotNull("§7Tiers §a${m.unlockedTiers}§7/§f${m.maxTier}" + (if (m.maxed) " §6maxed" else ""),
						m.header?.extra?.firstOrNull { it.startsWith("Total", true) }?.let { "§7$it" },
						next?.let { "§7next: §e${it.name}" }).joinToString(" §8· "))
				val path = { slot: Int -> stacks[slot]?.let { BuiltInRegistries.ITEM.getKey(it.item).path } }
				groups(ctx, mx, my, top, bottom, listOf("Tiers" to m.tiers.map { t -> CollectionTiles.tier(t, m.item, m.headSlot, path) }))
			}
			is CollPage.Minions -> {
				val crafted = m.crafted?.let { "Crafted minions" to it }
				val top = header(ctx, mx, my, "Crafted Minions" + pageSuffix,
					crafted?.let { (l, f) -> LoreProgress(l, null, f.first.toDouble(), f.second.toDouble()) },
					m.limit?.let { "§7Minion limit §f${it.first}§7/§f${it.second}" })
				val g = listOf(
					"Missing tiers" to m.minions.filter { !it.none && !it.complete },
					"All tiers" to m.minions.filter { it.complete },
					"Never crafted" to m.minions.filter { it.none },
				).filter { it.second.isNotEmpty() }.map { (l, ms) -> l to ms.map { mt ->
					TileSpec(mt.slot, mt.name.removeSuffix(" Minion"), if (mt.none) "not crafted" else "${mt.crafted.size}/${mt.maxTier} tiers",
						if (mt.maxTier > 0) mt.crafted.size.toDouble() / mt.maxTier else 0.0,
						if (mt.complete) MenuTheme.GOLD else if (mt.none) 0xFFFF5555.toInt() else 0xFFFFFF55.toInt())
				} }
				groups(ctx, mx, my, top, bottom, g)
			}
		}
		navRow(ctx, mx, my, nav)
		tooltip(ctx, mx, my)
	}

	private fun summaryLine(items: List<CollTile>): String {
		val maxed = items.count { it.state == TileState.MAXED }; val locked = items.count { it.state == TileState.LOCKED }
		return "§6$maxed maxed §8· §e${items.size - maxed - locked} in progress §8· §c$locked locked"
	}

	private fun itemTile(t: CollTile) = CollectionTiles.item(t)
}
