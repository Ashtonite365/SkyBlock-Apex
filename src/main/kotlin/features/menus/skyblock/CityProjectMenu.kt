// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class CityComponent(val slot: Int, val number: Int, val name: String, val global: String?, val description: List<String>,
                         val contributeSlot: Int?, val cost: List<String>, val status: String?)
data class CityProjectModel(val header: Int, val name: String, val progress: String?, val stage: String?, val total: String?,
                            val components: List<CityComponent>, val bonus: Pair<Int, String?>?, val nav: Map<String, Int>)

object CityProjectParser {

	val CANDIDATE_TITLE = MenuTitles.anyOf(
		Regex("^(?!.*\\bPrevious\\b)Project - \\S.*$", RegexOption.IGNORE_CASE),
		MenuTitles.cutPrefixOf("Project - Hub Revamp"),
	)
	val NAV = listOf("Go Back", "Close")
	private val COMPONENT = Regex("^Project Component #(\\d+)$", RegexOption.IGNORE_CASE)

	private fun after(lore: List<String>, prefix: String) =
		lore.firstOrNull { it.startsWith(prefix, true) }?.substring(prefix.length)?.trim(':', ' ')?.ifEmpty { null }

	fun cost(lore: List<String>): List<String> {
		val i = lore.indexOfFirst { it.equals("Cost", true) }
		return if (i < 0) emptyList() else lore.drop(i + 1).takeWhile { it.isNotBlank() }
	}

	fun parse(title: String, slots: List<SlotText>): CityProjectModel? {
		val t = MenuText.clean(title)
		if (!CANDIDATE_TITLE.containsMatchIn(t) || t.contains("Previous", true)) return null
		val header = slots.firstOrNull { it.cleanLore.firstOrNull().equals("City Project", true) } ?: return null
		val comps = slots.mapNotNull { s -> COMPONENT.matchEntire(s.cleanLore.firstOrNull() ?: "")?.let { s to it.groupValues[1].toInt() } }.sortedBy { it.first.slot }
		if (comps.isEmpty()) return null
		val buttons = slots.filter { it.cleanName.startsWith("Contribute", true) }.sortedBy { it.slot }
		val components = comps.mapIndexed { i, (s, n) ->
			val lore = s.cleanLore
			val btn = buttons.getOrNull(i)
			val global = lore.firstOrNull { it.startsWith("Global:", true) }?.substringAfter(":")?.trim()
			CityComponent(s.slot, n, s.cleanName, global, lore.drop(1).filter { it.isNotBlank() && !it.startsWith("Global:", true) },
				btn?.slot, btn?.let { cost(it.cleanLore) } ?: emptyList(), btn?.cleanLore?.lastOrNull { it.isNotBlank() })
		}
		val hl = header.cleanLore
		val progress = hl.firstNotNullOfOrNull { Regex("(\\d+(?:\\.\\d+)?%)").find(it)?.value }
		val bonus = slots.firstOrNull { it.cleanName.equals("Personal Bonus", true) }?.let { it.slot to after(it.cleanLore, "Contributions") }
		val nav = NAV.mapNotNull { n -> slots.firstOrNull { it.cleanName.equals(n, true) }?.let { n to it.slot } }.toMap()
		return CityProjectModel(header.slot, header.cleanName, progress, after(hl, "Stage"), after(hl, "Total contributions"), components, bonus, nav)
	}
}

@Config
object CityProjectSettings : MenuSettings("city-project", panels = listOf("descriptions")) {
	val reset by button("reset") { resetToDefaults() }
}

object CityProjectsSection {
	const val ID = "city-projects"
	const val DISPLAY_NAME = "City Projects"
}

object CityText {
	fun wrap(text: String, width: Int, measure: (String) -> Int): List<String> =
		dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.wrapText(text, width, measure)

	data class Line(val text: String, val optional: Boolean)
	fun fitLines(lines: List<Line>, maxLines: Int): List<Line> {
		if (lines.size <= maxLines) return lines
		var excess = lines.size - maxLines
		val out = lines.toMutableList()
		for (i in out.indices.reversed()) { if (excess == 0) break; if (out[i].optional) { out.removeAt(i); excess-- } }
		return out
	}
}

object CityProjectMenu : CustomMenu {
	override val id = "city-project"
	override val displayName = "City Project"
	override val title = CityProjectParser.CANDIDATE_TITLE
	override val settings get() = CityProjectSettings
	override val defaultSize = 560 to 300

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = CityProjectView(screen)
}

class CityProjectView(screen: AbstractContainerScreen<*>) : ApexViewBase<CityProjectModel>(screen) {
	override fun palette() = CityProjectSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = CityProjectParser.parse(title, slots)

	private var tipSlot: Int? = null

	override fun draw(ctx: GuiGraphicsExtractor, m: CityProjectModel, mx: Int, my: Int) {
		tipSlot = null
		ctx.text(font, "§6§lCity Project: §f${m.name}", x + 6, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val sub = listOfNotNull(m.progress?.let { "Progress $it" }, m.stage?.let { "Stage $it" }, m.total?.let { "Total $it" }).joinToString(" · ")
		ctx.text(font, "§7" + MenuTheme.fit(sub, w - 12, font::width), x + 6, y + 20, MenuTheme.GREY)
		if (MenuTheme.inside(mx, my, x + 6, y + 4, w - vw - 16, 26)) tipSlot = m.header
		val desc = CityProjectSettings.shows("descriptions")
		val n = m.components.size.coerceAtLeast(1); val gap = 4
		val tw = (w - 12 - gap * (n - 1)) / n; val ty = y + 32; val th = h - 58
		for ((i, c) in m.components.withIndex()) {
			val tx = x + 6 + i * (tw + gap)
			MenuTheme.frame(ctx, tx, ty, tx + tw, ty + th, MenuTheme.FILL)
			stacks[c.slot]?.let { ctx.item(it, tx + 3, ty + 3) }
			val inner = tw - 8

			val title = CityText.wrap("§6#${c.number} §f${c.name}", tw - 25, font::width)
			ctx.text(font, title.first(), tx + 21, ty + 7, MenuTheme.WHITE)
			var ly = ty + 21
			title.drop(1).forEach { ctx.text(font, it, tx + 4, ly, MenuTheme.WHITE); ly += 10 }
			ly += 2
			val status = c.status?.let { CityText.wrap("§8$it", inner, font::width) } ?: emptyList()
			val footerTop = ty + th - 20 - status.size * 10
			val body = mutableListOf<CityText.Line>()
			c.global?.let { g -> CityText.wrap("§aGlobal: $g", inner, font::width).forEach { body += CityText.Line(it, false) } }
			if (desc) c.description.forEach { d -> CityText.wrap("§7$d", inner, font::width).forEach { body += CityText.Line(it, true) } }
			if (c.cost.isNotEmpty()) {
				body += CityText.Line("§6Cost:", false)
				c.cost.forEach { co -> CityText.wrap("§f $co", inner, font::width).forEach { body += CityText.Line(it, false) } }
			}
			val bodyTop = ly
			for (l in CityText.fitLines(body, ((footerTop - 2 - ly) / 10).coerceAtLeast(0))) {
				ctx.text(font, l.text, tx + 4, ly, MenuTheme.GREY); ly += 10
			}
			status.forEachIndexed { k, l -> ctx.text(font, l, tx + 4, footerTop + k * 10, MenuTheme.DIM) }

			val costTop = bodyTop + body.indexOfFirst { it.text == "§6Cost:" }.coerceAtLeast(0) * 10
			if (MenuTheme.inside(mx, my, tx, ty, tw, th - 20))
				tipSlot = if (c.contributeSlot != null && c.cost.isNotEmpty() && my >= costTop) c.contributeSlot else c.slot
			c.contributeSlot?.let { s -> button(ctx, mx, my, tx + 4, ty + th - 18, tw - 8, 14, "Contribute") { b -> clickSlot(s, b) }
				if (MenuTheme.inside(mx, my, tx + 4, ty + th - 18, tw - 8, 14)) tipSlot = s }
		}
		var bx = x + 6
		for ((nm, s) in m.nav) { val bw = font.width(nm) + 14; button(ctx, mx, my, bx, y + h - 20, bw, 14, nm) { b -> clickSlot(s, b) }; bx += bw + 4 }
		m.bonus?.let { (s, cnt) -> val l = "Personal Bonus" + (cnt?.let { " ($it)" } ?: ""); val bw = font.width(l) + 14
			button(ctx, mx, my, x + w - bw - 6, y + h - 20, bw, 14, l) { b -> clickSlot(s, b) }
			if (MenuTheme.inside(mx, my, x + w - bw - 6, y + h - 20, bw, 14)) tipSlot = s }
		tipSlot?.let { s -> stacks[s]?.let { ctx.setComponentTooltipForNextFrame(font,
			net.minecraft.client.gui.screens.Screen.getTooltipFromItem(dev.ashtonite365.apex.util.Client.game, it), mx, my) } }
	}
}
