// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.quests

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config

data class QcEntry(val slot: Int, val name: String, val command: String?, val description: List<String>)

data class QcButton(val label: String, val slot: Int)

data class QuestsChaptersModel(
	val heading: String,
	val headerSlot: Int?,

	val headerLore: List<String>,
	val entries: List<QcEntry>,

	val nav: List<QcButton>,
) {

	val buttons get() = entries.map { QcButton(it.name, it.slot) } + nav
}

object QuestsChaptersParser {

	val TITLE = Regex("^Quests & Chapters$", RegexOption.IGNORE_CASE)
	const val CHEST_SIZE = 54
	private val CLICK = Regex("^(?:(?:Left|Right|Shift)[- ]?)?Click(?:s)? to .*", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): QuestsChaptersModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val chest = slots.filter { it.slot < CHEST_SIZE && it.cleanName.isNotEmpty() }
		val header = chest.firstOrNull { it.cleanName.equals(t, true) }
		val navSlots = chest.filter(HubText::isNav).map { it.slot }.toSet()
		val entries = chest.filter { it !== header && it.slot !in navSlots && it.cleanLore.any { l -> l.isNotBlank() } }.map(::entry)
		if (entries.isEmpty()) return null
		val nav = HubText.navOf(chest).sortedBy { it.second }.map { QcButton(it.first, it.second) }
		return QuestsChaptersModel(t, header?.slot, header?.cleanLore?.let(::trimBlank) ?: emptyList(), entries, nav)
	}

	fun entry(s: SlotText): QcEntry {
		val lore = s.cleanLore
		val cmd = lore.firstOrNull { it.isNotBlank() }?.takeIf { it.startsWith("/") }
		val rest = lore.filter { it != cmd && !CLICK.matches(it) }
		return QcEntry(s.slot, s.cleanName, cmd, trimBlank(rest))
	}

	fun trimBlank(lines: List<String>): List<String> {
		val out = mutableListOf<String>()
		for (l in lines) if (l.isNotBlank() || (out.isNotEmpty() && out.last().isNotBlank())) out += if (l.isBlank()) "" else l
		while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.size - 1)
		return out
	}

	fun forwardButton(packetButton: Int): Int? = if (packetButton == 0) 0 else null
}

object QuestsChaptersLayout {
	const val PAD = 6
	const val LINE = 10
	const val PARA = 4
	const val TOP = 22
	const val CARD_GAP = 6
	const val NAV_H = 14

	fun wrapHeader(lore: List<String>, width: Int, measure: (String) -> Int): List<String> {
		val paras = mutableListOf<MutableList<String>>(mutableListOf())
		for (l in lore) if (l.isBlank()) { if (paras.last().isNotEmpty()) paras += mutableListOf<String>() } else paras.last() += l.trim()
		val out = mutableListOf<String>()
		paras.filter { it.isNotEmpty() }.forEachIndexed { i, p ->
			if (i > 0) out += ""
			out += BzMenuLayout.wrapText(p.joinToString(" "), width, measure)
		}
		return out
	}

	fun height(lines: List<String>) = lines.sumOf { if (it.isEmpty()) PARA else LINE }

	fun cards(n: Int, x0: Int, y: Int, width: Int, h: Int): List<IntArray> {
		if (n <= 0) return emptyList()
		val cw = (width - CARD_GAP * (n - 1)) / n
		return (0 until n).map { intArrayOf(x0 + it * (cw + CARD_GAP), y, cw, h) }
	}

	fun navTop(y: Int, h: Int) = y + h - 20
}

@Config
object QuestsChaptersSettings : MenuSettings("quests-chapters") {
	val reset by button("reset") { resetToDefaults() }
}

object QuestsChaptersMenu : CustomMenu {
	override val id = "quests-chapters"
	override val displayName = "Quests & Chapters"
	override val title = QuestsChaptersParser.TITLE
	override val settings get() = QuestsChaptersSettings
	override val defaultSize = 380 to 240

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = QuestsChaptersView(screen)
}

class QuestsChaptersView(screen: AbstractContainerScreen<*>) : ApexViewBase<QuestsChaptersModel>(screen) {
	override fun palette() = QuestsChaptersSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = QuestsChaptersParser.parse(title, slots)

	private fun forward(slot: Int, b: Int) { QuestsChaptersParser.forwardButton(b)?.let { clickSlot(slot, it) } }

	override fun draw(ctx: GuiGraphicsExtractor, m: QuestsChaptersModel, mx: Int, my: Int) {
		val L = QuestsChaptersLayout; val pad = L.PAD

		if (drawingCached) {
			val t = "Reading menu…"
			ctx.text(font, "§7$t", x + (w - font.width(t)) / 2, y + h / 2 - 4, MenuTheme.GREY); return
		}
		val vw = font.width("Vanilla") + 10
		ctx.text(font, MenuTheme.fit("§6§l" + m.heading, w - vw - 3 * pad, font::width), x + pad, y + 7, MenuTheme.GOLD)

		var top = y + L.TOP
		var tipSlot: Int? = null
		if (m.headerLore.isNotEmpty()) {
			val lines = L.wrapHeader(m.headerLore, w - 2 * pad - 30, font::width)
			val hh = L.height(lines) + 8
			MenuTheme.frame(ctx, x + pad, top, x + w - pad, top + hh, MenuTheme.FILL)
			m.headerSlot?.let { s -> stacks[s]?.let { ctx.item(it, x + pad + 4, top + 4) } }
			var ly = top + 5
			for (l in lines) { if (l.isEmpty()) { ly += L.PARA; continue }; ctx.text(font, "§7$l", x + pad + 24, ly, MenuTheme.GREY); ly += L.LINE }
			if (m.headerSlot != null && MenuTheme.inside(mx, my, x + pad, top, w - 2 * pad, hh)) tipSlot = m.headerSlot
			top += hh + 6
		}

		val navY = L.navTop(y, h)
		val cardH = maxOf(30, navY - 6 - top)
		L.cards(m.entries.size, x + pad, top, w - 2 * pad, cardH).forEachIndexed { i, r ->
			val e = m.entries[i]; val (cx, cy, cw, ch) = r.toList()
			button(ctx, mx, my, cx, cy, cw, ch, "") { b -> forward(e.slot, b) }
			stacks[e.slot]?.let { ctx.item(it, cx + 4, cy + 4) }
			ctx.text(font, MenuTheme.fit("§f§l" + e.name, cw - 28, font::width), cx + 24, cy + 5, MenuTheme.WHITE)
			e.command?.let { ctx.text(font, MenuTheme.fit("§8$it", cw - 28, font::width), cx + 24, cy + 15, MenuTheme.DIM) }
			var ly = cy + 28
			for (l in L.wrapHeader(e.description, cw - 10, font::width)) {
				if (ly + 9 > cy + ch - 2) break
				if (l.isEmpty()) { ly += L.PARA; continue }; ctx.text(font, "§7$l", cx + 5, ly, MenuTheme.GREY); ly += L.LINE
			}
			if (MenuTheme.inside(mx, my, cx, cy, cw, ch)) tipSlot = e.slot
		}

		var nx = x + pad
		for (n in m.nav) {
			val bw = font.width(n.label) + 10
			button(ctx, mx, my, nx, navY, bw, L.NAV_H, n.label) { b -> forward(n.slot, b) }; nx += bw + 4
		}
		tipSlot?.let { s -> stacks[s]?.let { ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, it), mx, my) } }
	}
}
