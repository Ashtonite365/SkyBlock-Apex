// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.exptable

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.skills.SkillViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class ExpTile(val slot: Int, val row: Int, val col: Int, val name: String, val lore: List<String>,
                   val rawName: String = name, val rawLore: List<String> = lore)
data class ExpTableModel(val tiles: List<ExpTile>, val rows: Int, val cols: Int)

object ExpTableParser {

	val TITLE = Regex("^Experimentation Table$")
	const val WIDTH = 9

	fun isFiller(s: SlotText) = s.cleanName.isBlank() && s.cleanLore.none { it.isNotBlank() }

	fun compact(slots: List<SlotText>, chestSize: Int = slots.maxOfOrNull { it.slot + 1 } ?: 0): ExpTableModel {
		val kept = slots.filter { it.slot in 0 until chestSize && !isFiller(it) }.sortedBy { it.slot }
		val rows = kept.map { it.slot / WIDTH }.distinct().sorted()
		val cols = kept.map { it.slot % WIDTH }.distinct().sorted()
		val tiles = kept.map { ExpTile(it.slot, rows.indexOf(it.slot / WIDTH), cols.indexOf(it.slot % WIDTH), it.cleanName, it.cleanLore, it.name, it.lore) }
		return ExpTableModel(tiles, rows.size, cols.size)
	}

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): ExpTableModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		return compact(slots, chestSize).takeIf { it.tiles.isNotEmpty() }
	}

	private val CLICK_HINT = Regex("^((Left|Right|Shift|Middle)-)?Click\\b", RegexOption.IGNORE_CASE)

	fun status(lore: List<String>): String? {
		val paragraphs = mutableListOf(mutableListOf<String>())
		for (l in lore) if (l.isBlank()) paragraphs += mutableListOf<String>() else paragraphs.last() += l.trim()
		val last = paragraphs.map { p -> p.filterNot { CLICK_HINT.containsMatchIn(it) } }.lastOrNull { it.isNotEmpty() } ?: return null
		return last.singleOrNull()
	}
}

object ExpTableLayout {
	const val PAD = 4
	const val HEADER = 22
	const val GAP = 4
	const val TEXT_X = 22

	const val MAX_PANEL_H = 60
	const val BOTTOM_H = 44

	const val SIDE_SHARE = 0.36f
	const val LINE_H = 9

	enum class Kind { GAME, SIDE, BOTTOM }

	data class Panel(val kind: Kind, val tiles: List<ExpTile>, val x: Int, val y: Int, val w: Int, val h: Int, val text: List<String>) {
		val slot get() = tiles.first().slot
		val bounds get() = dev.ashtonite365.apex.features.menus.MenuBounds(x, y, w, h)
		fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
	}

	data class Roles(val chrono: ExpTile, val superpairs: ExpTile, val ultra: ExpTile, val left: List<ExpTile>, val right: List<ExpTile>, val bottom: List<ExpTile>)

	private fun named(m: ExpTableModel, n: String) = m.tiles.firstOrNull { it.name.trim().equals(n, true) }

	fun roles(m: ExpTableModel): Roles? {
		val c = named(m, "Chronomatron") ?: return null
		val s = named(m, "Superpairs") ?: return null
		val u = named(m, "Ultrasequencer") ?: return null
		val gameRow = minOf(c.slot, u.slot) / 9
		val sc = s.slot % 9
		val rest = m.tiles.filter { it !== c && it !== s && it !== u }.sortedBy { it.slot }
		val above = rest.filter { it.slot / 9 < gameRow }
		val left = above.filter { it.slot % 9 < sc }; val right = above.filter { it.slot % 9 > sc }
		return Roles(c, s, u, left, right, rest - left.toSet() - right.toSet())
	}

	fun text(t: ExpTile): List<String> = listOfNotNull("§f" + t.name, ExpTableParser.status(t.lore)?.let { "§7$it" })

	fun sideText(tiles: List<ExpTile>): List<String> =
		tiles.map { "§f" + it.name }.distinct() + tiles.mapNotNull { ExpTableParser.status(it.lore) }.distinct().map { "§a$it" }

	fun panels(m: ExpTableModel, b: dev.ashtonite365.apex.features.menus.MenuBounds): List<Panel>? {
		val r = roles(m) ?: return null
		val innerW = b.w - 2 * PAD
		val sideW = ((innerW - 2 * GAP) * SIDE_SHARE).toInt()
		val midW = innerW - 2 * GAP - 2 * sideW
		val x0 = b.x + PAD; val xMid = x0 + sideW + GAP; val xRight = xMid + midW + GAP
		val botH = minOf(BOTTOM_H, maxOf(1, (b.h - HEADER - PAD) / 3))
		val botY = b.y + b.h - PAD - botH
		val top = b.y + HEADER
		val hasSide = r.left.isNotEmpty() || r.right.isNotEmpty()
		val bodyH = botY - GAP - top
		val panelH = maxOf(1, minOf(MAX_PANEL_H, if (hasSide) (bodyH - GAP) / 2 else bodyH))
		val out = mutableListOf(
			Panel(Kind.GAME, listOf(r.chrono), x0, top, sideW, panelH, text(r.chrono)),
			Panel(Kind.GAME, listOf(r.superpairs), xMid, top, midW, panelH, text(r.superpairs)),
			Panel(Kind.GAME, listOf(r.ultra), xRight, top, sideW, panelH, text(r.ultra)))
		val sideY = top + panelH + GAP
		if (r.left.isNotEmpty()) out += Panel(Kind.SIDE, r.left, x0, sideY, sideW, panelH, sideText(r.left))
		if (r.right.isNotEmpty()) out += Panel(Kind.SIDE, r.right, xRight, sideY, sideW, panelH, sideText(r.right))
		if (r.bottom.isNotEmpty()) {
			val n = r.bottom.size
			val bw = (innerW - (n - 1) * GAP) / n
			r.bottom.forEachIndexed { i, t -> out += Panel(Kind.BOTTOM, listOf(t), x0 + i * (bw + GAP), botY, bw, botH, text(t)) }
		}
		return out
	}

	fun textBox(p: Panel) = dev.ashtonite365.apex.features.menus.MenuBounds(p.x + TEXT_X, p.y + 3, maxOf(1, p.w - TEXT_X - 3), maxOf(1, p.h - 6))

	fun wrap(text: String, w: Int, measure: (String) -> Int): List<String> {
		val colour = Regex("^(§.)+").find(text)?.value ?: ""
		val out = mutableListOf<String>(); var cur = ""
		for (word in text.removePrefix(colour).split(' ').filter { it.isNotEmpty() }) {
			val next = if (cur.isEmpty()) word else "$cur $word"
			if (cur.isNotEmpty() && measure(colour + next) > w) { out += colour + cur; cur = word } else cur = next
		}
		if (cur.isNotEmpty()) out += colour + cur
		return out
	}

	data class Fitted(val scale: Float, val lines: List<String>) { fun height() = lines.size * LINE_H * scale }

	fun fit(p: Panel, measure: (String) -> Int): Fitted {
		val box = textBox(p)
		var last = Fitted(0.5f, emptyList())
		for (k in 20 downTo 10) {
			val sc = k / 20f
			val ww = (box.w / sc).toInt()
			val lines = p.text.flatMap { wrap(it, ww, measure) }
			last = Fitted(sc, lines)
			if (lines.size * LINE_H * sc <= box.h + 1e-3f) return last
		}
		return last
	}
}

data class ExpClick(val slot: Int, val button: Int)

object ExpTableClicks {

	fun route(model: ExpTableModel, slot: Int, packetButton: Int): ExpClick? {
		if (packetButton != 0 && packetButton != 1) return null
		val tile = model.tiles.firstOrNull { it.slot == slot } ?: return null
		return ExpClick(tile.slot, packetButton)
	}
}

object ExpTableMenuGate {

	val SWITCH_KEYS = listOf("apex-menus")

	fun resolve(option: (String) -> Any?): Boolean =
		SWITCH_KEYS.firstNotNullOfOrNull { option(it) as? Boolean } ?: true

	@JvmStatic var master: () -> Boolean = {
		runCatching { resolve { k -> dev.ashtonite365.apex.features.experiments.ExperimentationTable.allOptions[k]?.get() } }.getOrDefault(true)
	}

	fun apex(master: Boolean, ownApex: Boolean) = master && ownApex
}

@Config
object ExpTableMenuSettings : MenuSettings("exptable") {
	val reset by button("reset") { resetToDefaults() }
}

object ExpTableMenu : CustomMenu {
	override val id = "exptable"
	override val displayName = "Experimentation Table"
	override val title = ExpTableParser.TITLE
	override val settings get() = ExpTableMenuSettings
	override val enabled get() = ExpTableMenuGate.apex(ExpTableMenuGate.master(), settings.apex)

	override val defaultSize = 460 to 250

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = ExpTableView(screen)
}

class ExpTableView(screen: AbstractContainerScreen<*>) : SkillViewBase<ExpTableModel>(screen) {
	override fun palette() = ExpTableMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = ExpTableParser.parse(title, slots, screen.menu.slots.size - 36)

	override fun draw(ctx: GuiGraphicsExtractor, m: ExpTableModel, mx: Int, my: Int) {
		header(ctx, mx, my, "Experimentation Table")
		val panels = ExpTableLayout.panels(m, dev.ashtonite365.apex.features.menus.MenuBounds(x, y, w, h))
		if (panels == null) { drawGrid(ctx, m, mx, my); return }
		var hover: ExpTableLayout.Panel? = null
		for (p in panels) {

			button(ctx, mx, my, p.x, p.y, p.w, p.h, "") { b -> ExpTableClicks.route(m, p.slot, b)?.let { clickSlot(it.slot, it.button) } }
			stacks[p.slot]?.let { ctx.item(it, p.x + 3, p.y + (p.h - 16) / 2) }
			val box = ExpTableLayout.textBox(p)
			val f = ExpTableLayout.fit(p, font::width)
			var ty = box.y + ((box.h - f.height()) / 2).coerceAtLeast(0f)
			for (l in f.lines) {
				ctx.pose().pushMatrix(); ctx.pose().translate(box.x.toFloat(), ty); ctx.pose().scale(f.scale, f.scale)
				ctx.text(font, l, 0, 0, MenuTheme.WHITE, true)
				ctx.pose().popMatrix()
				ty += ExpTableLayout.LINE_H * f.scale
			}
			if (p.contains(mx, my)) hover = p
		}

		hover?.let { p -> ctx.setComponentTooltipForNextFrame(font, p.tiles.flatMapIndexed { i, t -> (if (i > 0) listOf("") else emptyList()) + listOf(t.rawName) + t.rawLore }.map { Component.literal(it) }, mx, my) }
	}

	private fun drawGrid(ctx: GuiGraphicsExtractor, m: ExpTableModel, mx: Int, my: Int) {
		val gap = 3
		val top = y + 22
		val cw = (w - 8 - gap * (m.cols - 1)) / maxOf(1, m.cols)
		val ch = minOf(34, (h - 26 - gap * (m.rows - 1)) / maxOf(1, m.rows))
		var hover: ExpTile? = null
		for (t in m.tiles) {
			val tx = x + 4 + t.col * (cw + gap)
			val ty = top + t.row * (ch + gap)
			tile(ctx, mx, my, tx, ty, cw, ch, t.slot, "§f" + t.name, ExpTableParser.status(t.lore), null)

			areas.removeAt(areas.lastIndex)
			area(tx, ty, cw, ch) { b -> ExpTableClicks.route(m, t.slot, b)?.let { clickSlot(it.slot, it.button) } }
			if (MenuTheme.inside(mx, my, tx, ty, cw, ch)) hover = t
		}

		hover?.let { t -> ctx.setComponentTooltipForNextFrame(font, (listOf(t.rawName) + t.rawLore).map { Component.literal(it) }, mx, my) }
	}
}
