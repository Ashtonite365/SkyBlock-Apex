// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

data class MinionChestModel(val chestSlots: Int, val used: Int, val names: Map<String, Int>)

object MinionChestParser {
	val TITLE = Regex("^Minion Chest$", RegexOption.IGNORE_CASE)
	const val INVENTORY = 36

	fun parse(title: String, slots: List<SlotText>, windowSlots: Int): MinionChestModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val chest = windowSlots - INVENTORY
		if (chest <= 0 || chest % 9 != 0) return null
		val items = slots.filter { it.slot < chest && it.cleanName.isNotEmpty() }
		val totals = linkedMapOf<String, Int>()
		for (s in items) totals[s.cleanName] = (totals[s.cleanName] ?: 0) + (MinionChestCounts.count(s))
		return MinionChestModel(chest, items.size, totals)
	}
}

object MinionChestCounts { var count: (SlotText) -> Int = { 1 } }

@Config
object MinionChestSettings : MenuSettings("minion-chest", panels = listOf("value", "totals")) {
	val reset by button("reset") { resetToDefaults() }
}

object MinionChestMenu : CustomMenu {
	override val id = "minion-chest"
	override val displayName = "Minion Chest"
	override val title = MinionChestParser.TITLE
	override val settings get() = MinionChestSettings
	override val defaultSize = 330 to 210
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = MinionChestView(screen)
}

class MinionChestView(screen: AbstractContainerScreen<*>) : MinionViewBase<MinionChestModel>(screen), MenuView {
	private val c get() = MinionChestSettings.customisation
	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = MinionChestParser.parse(title, slots, screen.menu.slots.size)

	private fun bazaarSell(id: String): Double? =
		HypixelStaticData.bazaarData[bazaarStockOf(ItemId(id))]?.quickStatus?.sellPrice?.takeIf { it > 0 }

	override fun draw(ctx: GuiGraphicsExtractor, m: MinionChestModel, mx: Int, my: Int) {
		val pad = 6

		val cells = MinionPanes.layout((0 until m.chestSlots).toList()) { MinionPanes.hide(live(it)) }
		val shown = cells.map { it.first }
		val used = shown.count { !live(it).isEmpty }
		ctx.text(font, "§6§lMinion Chest §7${used}/${shown.size}", x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val gy = y + 22
		for ((slot, col, row) in cells) realTile(ctx, mx, my, x + pad + col * 18, gy + row * 18, slot)
		val px = x + pad + 9 * 18 + 8
		var py = gy
		if (c.shows("value")) {
			val stacks = shown.map(::live).filter { !it.isEmpty }
			val v = MinionClicks.value(stacks.map { it.view().repoItemId?.skyblockId to it.count }, ::bazaarSell)
			ctx.text(font, "§7Value §6${MenuText.coins(v.coins)}", px, py, MenuTheme.WHITE); py += 10
			if (v.unpriced > 0) { ctx.text(font, "§8${v.unpriced} unpriced", px, py, MenuTheme.DIM); py += 10 }
		}
		if (c.shows("totals")) {
			val totals = linkedMapOf<String, Int>()
			for (i in shown) live(i).takeIf { !it.isEmpty }?.let { totals[it.hoverName.string] = (totals[it.hoverName.string] ?: 0) + it.count }
			if (totals.isEmpty()) ctx.text(font, "§8Empty", px, py, MenuTheme.DIM)
			for ((n, k) in totals.entries.take(8)) { ctx.text(font, MenuTheme.fit("§f${k}x §7$n", x + w - px - 4, font::width), px, py, MenuTheme.WHITE); py += 10 }
		}
		inventory(ctx, mx, my, x + pad, gy + maxOf(MinionPanes.rows(cells), 1) * 18 + 14, m.chestSlots)
		finish(ctx, mx, my)
	}
}
