// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
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
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config

data class PastProject(val slot: Int, val name: String, val released: String?, val description: List<String>,
                       val contributed: String?, val bonuses: List<String>)
data class PastProjectsModel(val projects: List<PastProject>, val nav: Map<String, Int>)

object PreviousCityProjectsParser {
	val TITLE = Regex("^Previous City Projects(?:\\s*\\(\\d+/\\d+\\))?$", RegexOption.IGNORE_CASE)
	val NAV = listOf("Previous Page", "Go Back", "Close", "Next Page")
	private val PREFIX = Regex("^City project:\\s*", RegexOption.IGNORE_CASE)

	fun project(s: SlotText): PastProject {
		val l = s.cleanLore
		val released = l.firstOrNull { it.startsWith("Released", true) }
		val bi = l.indexOfFirst { it.trim().equals("Bonuses:", true) }
		val head = (if (bi < 0) l else l.take(bi)).filter { it.isNotBlank() && it != released }

		val ci = head.indexOfFirst { it.startsWith("You made", true) || it.startsWith("You contributed", true) }
		val contributed = if (ci < 0) null else head.drop(ci).joinToString(" ")
		val desc = if (ci < 0) head else head.take(ci)
		val bonuses = if (bi < 0) emptyList() else l.drop(bi + 1).filter { it.isNotBlank() }.map { it.trimStart('▶', ' ') }
		return PastProject(s.slot, s.cleanName.replace(PREFIX, ""), released, desc, contributed, bonuses)
	}

	fun parse(title: String, slots: List<SlotText>): PastProjectsModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val projects = slots.filter { PREFIX.containsMatchIn(it.cleanName) }.sortedBy { it.slot }.map(::project)
		if (projects.isEmpty()) return null
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		return PastProjectsModel(projects, nav)
	}
}

@Config
object PreviousCityProjectsSettings : MenuSettings("previous-city-projects", panels = listOf("bonuses")) {
	val reset by button("reset") { resetToDefaults() }
}

object PreviousCityProjectsMenu : CustomMenu {
	override val id = "previous-city-projects"
	override val displayName = "Previous City Projects"
	override val title = PreviousCityProjectsParser.TITLE
	override val settings get() = PreviousCityProjectsSettings
	override val defaultSize = 560 to 300

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = PreviousCityProjectsView(screen)
}

class PreviousCityProjectsView(screen: AbstractContainerScreen<*>) : ApexViewBase<PastProjectsModel>(screen) {
	override fun palette() = PreviousCityProjectsSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = PreviousCityProjectsParser.parse(title, slots)
	private var scroll by dev.ashtonite365.apex.util.ScrollMemory.int(settle = true) { scrollKey() }

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean { scroll = maxOf(0, scroll - (amount * 16).toInt()); return true }

	override fun draw(ctx: GuiGraphicsExtractor, m: PastProjectsModel, mx: Int, my: Int) {
		ctx.text(font, "§6§lPrevious City Projects §7(${m.projects.size})", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val bonuses = PreviousCityProjectsSettings.shows("bonuses")
		val cols = maxOf(1, (w - 12) / 170); val gap = 4
		val cw = (w - 12 - gap * (cols - 1)) / cols
		val top = y + 22; val bottom = y + h - 24

		val cards = m.projects.map { p ->
			val inner = cw - 8
			val t = CityText.wrap("§f${p.name}", cw - 25, font::width)
			val body = buildList {
				p.released?.let { addAll(CityText.wrap("§e$it", inner, font::width)) }
				p.description.joinToString(" ").takeIf { it.isNotBlank() }?.let { addAll(CityText.wrap("§7$it", inner, font::width)) }
				p.contributed?.let { addAll(CityText.wrap("§a$it", inner, font::width)) }
				if (bonuses && p.bonuses.isNotEmpty()) { add("§6Bonuses:"); p.bonuses.forEach { addAll(CityText.wrap("§f▶ $it", inner, font::width)) } }
			}
			Triple(p, t, body)
		}
		val rows = cards.chunked(cols)
		val heights = rows.map { r -> r.maxOf { (_, t, b) -> 8 + maxOf(16, t.size * 10) + b.size * 10 + 4 } }
		val total = heights.sum() + gap * (rows.size - 1)
		scroll = scroll.coerceAtMost(maxOf(0, total - (bottom - top)))
		var tip: Int? = null
		ctx.enableScissor(x + 2, top, x + w - 2, bottom)
		var ry = top - scroll
		for ((ri, r) in rows.withIndex()) {
			val rh = heights[ri]
			for ((ci, card) in r.withIndex()) {
				val (p, t, body) = card
				val cx = x + 6 + ci * (cw + gap)
				if (ry + rh >= top && ry <= bottom) {
					MenuTheme.frame(ctx, cx, ry, cx + cw, ry + rh, MenuTheme.FILL)
					stacks[p.slot]?.let { ctx.item(it, cx + 3, ry + 3) }
					t.forEachIndexed { k, l -> ctx.text(font, l, cx + 21, ry + 7 + k * 10, MenuTheme.WHITE) }
					var ly = ry + 8 + maxOf(16, t.size * 10)
					body.forEach { ctx.text(font, it, cx + 4, ly, MenuTheme.GREY); ly += 10 }
					if (my in top until bottom && MenuTheme.inside(mx, my, cx, ry, cw, rh)) tip = p.slot
				}
			}
			ry += rh + gap
		}
		ctx.disableScissor()
		var bx = x + 6
		for ((nm, s) in m.nav) { val bw = font.width(nm) + 14; button(ctx, mx, my, bx, y + h - 20, bw, 14, nm) { b -> clickSlot(s, b) }; bx += bw + 4 }
		tip?.let { s -> stacks[s]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) } }
	}
}
