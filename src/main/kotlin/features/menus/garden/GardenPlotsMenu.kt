// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.garden

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class PlotCell(val slot: Int, val row: Int, val col: Int, val name: String, val locked: Boolean,
                    val requirements: List<String>, val cost: List<String>, val status: List<String>,
                    val preset: String?, val notes: List<String>)

data class PlotsModel(val cells: List<PlotCell>, val nav: List<GardenTile>)

object GardenPlotsParser {
	val TITLE = Regex("^Configure Plots$")
	private val PLOT = Regex("^(Plot\\s*-\\s*\\d+|The Barn)$", RegexOption.IGNORE_CASE)

	private fun block(lore: List<String>, header: String): List<String> {
		val at = lore.indexOfFirst { it.trimEnd(':').equals(header, true) }
		if (at < 0) return emptyList()
		return lore.drop(at + 1).takeWhile { it.isNotBlank() }
	}

	fun cell(s: SlotText): PlotCell {
		val lore = s.cleanLore
		val req = block(lore, "Requirement").ifEmpty { block(lore, "Requirements") }
		val cost = block(lore, "Cost")
		val locked = cost.isNotEmpty() || req.isNotEmpty()

		val tail = lore.indexOfLast { it.isBlank() }.let { if (it >= 0) lore.drop(it + 1) else emptyList() }
		val status = if (locked) tail.filterNot { it.contains("click", true) } else emptyList()
		val preset = lore.firstOrNull { it.startsWith("Preset:", true) }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
		val notes = if (locked) emptyList() else lore.filter { it.isNotBlank() && !it.startsWith("Preset:", true) && !it.contains("click", true) }
		return PlotCell(s.slot, s.slot / 9, s.slot % 9 - 2, s.cleanName, locked, req, cost, status, preset, notes)
	}

	fun parse(title: String, slots: List<SlotText>): PlotsModel? {
		if (!TITLE.matches(dev.ashtonite365.apex.features.menus.support.MenuText.clean(title))) return null
		val named = slots.filter { it.cleanName.isNotBlank() }
		val plots = named.filter { PLOT.matches(it.cleanName) }
		if (plots.isEmpty()) return null
		val nav = named.filter { s -> GardenPageParser.NAV.any { it.equals(s.cleanName, true) } }
		if (named.size != plots.size + nav.size) return null
		if (plots.any { it.slot % 9 !in 2..6 || it.slot / 9 > 4 }) return null
		return PlotsModel(plots.sortedBy { it.slot }.map(::cell), nav.sortedBy { it.slot }.map { GardenTile(it.slot, it.cleanName, it.cleanLore) })
	}

	fun lines(c: PlotCell): List<String> = buildList {
		if (c.locked) {
			if (c.requirements.isNotEmpty()) { add("§eRequirement:"); c.requirements.forEach { add("§7$it") } }
			if (c.cost.isNotEmpty()) { add("§eCost:"); c.cost.forEach { add("§6$it") } }
			c.status.forEach { add("§c$it") }
		} else {
			add(if (c.preset != null) "§aPreset: §f${c.preset}" else "§7Preset: §8unknown")
			c.notes.forEach { add("§7$it") }
		}
	}
}

@Config object GardenPlotsSettings : MenuSettings("garden-plots") { val reset by button("reset") { resetToDefaults() } }

object GardenPlotsMenu : CustomMenu {
	override val id = "garden-plots"
	override val displayName = "Configure Plots"
	override val title = GardenPlotsParser.TITLE
	override val settings get() = GardenPlotsSettings
	override val defaultSize = 600 to 420
	override fun create(screen: AbstractContainerScreen<*>): MenuView = GardenPlotsView(screen)

	val ICON_WIDTHS = listOf(18, 13, 10)

	fun plan(fit: List<FitTile?>, w: Int, h: Int, measure: (String) -> Int): FitPlan =
		ICON_WIDTHS.firstNotNullOfOrNull { GardenFit.plan(fit, w, h, measure, cols = 5, gap = 1, iconW = it, allowHideBody = false) }
			?: GardenFit.plan(fit, w, Int.MAX_VALUE, measure, cols = 5, gap = 1, iconW = ICON_WIDTHS.last(), allowHideBody = false)!!
}

class GardenPlotsView(screen: AbstractContainerScreen<*>) : ApexViewBase<PlotsModel>(screen) {
	override fun palette() = GardenPlotsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = GardenPlotsParser.parse(title, slots)
	override val logTag get() = "[ApexGarden] Configure Plots"
	private var planKey: Any? = null
	private var plan: FitPlan? = null

	override fun draw(ctx: GuiGraphicsExtractor, m: PlotsModel, mx: Int, my: Int) {
		ctx.text(font, "§a§lConfigure Plots", x + 6, y + 7, MenuTheme.WHITE)
		val hint = "§8Left: modify  Right: teleport"
		ctx.text(font, hint, x + 10 + font.width("Configure Plots") + 6, y + 7, MenuTheme.GREY)
		val vw = font.width("Vanilla") + 10

		val view = MenuBounds(x + 3, y + 19, w - 6, h - 40)
		val grid: List<PlotCell?> = (0 until 25).map { i -> m.cells.firstOrNull { it.row == i / 5 && it.col == i % 5 } }
		val fit = grid.map { c -> c?.let { FitTile((if (it.locked) "§c" else "§a") + it.name, GardenPlotsParser.lines(it)) } }
		val key = Triple(fit, view.w, view.h)
		val p = (if (key == planKey) plan else null) ?: GardenPlotsMenu.plan(fit, view.w, view.h, font::width).also { planKey = key; plan = it }
		var tip: net.minecraft.world.item.ItemStack? = null
		val pal = palette()
		var yy = view.y
		for (r in 0 until 5) {
			val rh = p.rowHeights[r]
			for (col in 0 until 5) {
				val c = grid[r * 5 + col] ?: continue
				val cx = view.x + col * (p.tileW + p.gap)
				val hov = hovered(mx, my, cx, yy, p.tileW, rh)
				MenuTheme.frame(ctx, cx, yy, cx + p.tileW, yy + rh, if (hov) pal.fillHover else pal.fill, if (hov) pal.borderHover else if (c.locked) MenuTheme.BORDER else pal.accent)

				stacks[c.slot]?.let { GardenFit.drawIcon(ctx, it, cx + 1, yy + 1, GardenFit.iconSize(p.iconW)); if (hov) tip = it }
				GardenFit.drawLines(ctx, font, GardenFit.lines(fit[r * 5 + col]!!, p.textW, p.showBody, font::width), cx + 2 + p.iconW, yy + 3, p.scale, MenuTheme.WHITE)
				area(cx, yy, p.tileW, rh) { b -> clickSlot(c.slot, b) }
			}
			yy += rh + p.gap
		}

		var fx = x + 4
		for (n in m.nav) {
			val bw = font.width(n.name) + 12
			val bx = if (n.name.equals("Close", true)) x + (w - bw) / 2 else fx.also { fx += bw + 4 }
			button(ctx, mx, my, bx, y + h - 18, bw, 14, n.name) { b -> clickSlot(n.slot, b) }
		}
		tip?.let { if (screen.menu.carried.isEmpty) ctx.setTooltipForNextFrame(font, it, mx, my) }
	}
}
